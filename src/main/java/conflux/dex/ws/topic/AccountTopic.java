package conflux.dex.ws.topic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.BusinessException;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.Account;
import conflux.dex.model.BalanceChange;
import conflux.dex.model.BalanceChangeType;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Product;
import conflux.dex.service.AccountService;
import conflux.dex.ws.TopicRequest;

@Component
public class AccountTopic extends UserTopic {
	
	private static final String TOPIC_NAME = "accounts";
	private static final String KEY_MODEL = "model";
	
	private Index accountBalanceOnlyIndex = new Index("accounts.0");
	private Index accountOrAvailableBalanceIndex = new Index("accounts.1");
	
	@Autowired
	public AccountTopic(DexDao dao) {
		super(dao);
		
		this.register(TOPIC_NAME);
		
		// available -> hold, balance not changed
		Events.WITHDRAW_SUBMITTED.addHandler(data -> publish(BalanceChangeType.Withdraw, data.account, true, false));
		// hold reduced and balance changed
		Events.WITHDRAW_COMPLETED.addHandler(data -> publish(BalanceChangeType.Withdraw, data.account, false, true));
		// hold -> available, balance not changed
		Events.WITHDRAW_FAILED.addHandler(data -> publish(BalanceChangeType.Withdraw, data.account, true, false));
		Events.TRANSFER.addHandler(data -> {
			publish(BalanceChangeType.Transfer, data.sender, true, true);
			
			for (Account recipient : data.recipients) {
				publish(BalanceChangeType.Transfer, recipient, true, true);
			}
		});
	}
	
	@Override
	protected void register(Product product) {
		if (product instanceof InstantExchangeProduct)
			this.engineService.instantExchangeLogWorkers.get(product.getId()).addHandler(new OrderMatchPublisher(this, this.dao));
		else
			this.engineService.logWorkers.get(product.getId()).addHandler(new OrderMatchPublisher(this, this.dao));
	}
	
	@Override
	protected Index getIndex(TopicRequest request) {
		Object model = request.getArguments() == null ? null : request.getArguments().get(KEY_MODEL);
		if (model == null) {
			throw BusinessException.validateFailed("model not specified");
		}
		
		switch (model.toString()) {
			case "0": return this.accountBalanceOnlyIndex;
			case "1": return this.accountOrAvailableBalanceIndex;
			default: throw BusinessException.validateFailed("model value is invalid");
		}
	}
	
	public void publish(BalanceChangeType type, Account account, boolean availableBalanceChanged, boolean accountBalanceChanged) {
		this.publish(type, account.getUserId(), account.getId(), availableBalanceChanged, accountBalanceChanged);
	}
	
	public void publish(BalanceChangeType type, long userId, long accountId, boolean availableBalanceChanged, boolean accountBalanceChanged) {
		if (accountBalanceChanged 
				&& (this.accountBalanceOnlyIndex.isSubscribed(userId) 
						|| this.accountOrAvailableBalanceIndex.isSubscribed(userId))) {
			Account account = AccountService.mustGetAccountById(dao, accountId);
			BalanceChange data = BalanceChange.accountBalanceChanged(type, account);
			this.accountBalanceOnlyIndex.publish(userId, data);
			this.accountOrAvailableBalanceIndex.publish(userId, data);
		}
		
		if (availableBalanceChanged && this.accountOrAvailableBalanceIndex.isSubscribed(userId)) {
			Account account = AccountService.mustGetAccountById(dao, accountId);
			BalanceChange data = BalanceChange.availableBalanceChanged(type, account);
			this.accountOrAvailableBalanceIndex.publish(userId, data);
		}
	}

}
