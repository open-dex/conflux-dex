package conflux.dex.service.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import conflux.dex.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import conflux.dex.blockchain.log.ScheduleWithdrawRequest;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.WithdrawEventArg;
import conflux.dex.model.Account;
import conflux.dex.model.AccountStatus;
import conflux.dex.model.BalanceChangeType;
import conflux.dex.model.Currency;
import conflux.dex.model.User;
import conflux.dex.model.WithdrawRecord;
import conflux.dex.model.WithdrawType;
import conflux.dex.service.OrderService;
import conflux.dex.ws.topic.AccountTopic;

/**
 * Batch handle withdraw event logs within a transaction.
 */
public class WithdrawEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(DepositEventHandler.class);
	
	private static class Item {
		public WithdrawRecord record;
		public Currency currency;
		public Account account;
		
		public Item(WithdrawRecord record, Currency currency, Account account) {
			this.record = record;
			this.currency = currency;
			this.account = account;
		}
	}
	
	private List<Item> items = new LinkedList<Item>();
	
	public WithdrawEventHandler(List<ScheduleWithdrawRequest> schedule, DexDao dao, OrderService service) {
		Map<String, Long> address2IdMap = new HashMap<String, Long>();
		
		for (ScheduleWithdrawRequest s : schedule) {
			// Request force withdraw
			if (s.getTime().compareTo(BigInteger.ZERO) != 0) {
				logger.trace("received request withdraw event log: {}", s);
				
				Item item = create(dao, s, address2IdMap);
				if (item != null) {
					service.cancelOrders(item.account.getUserId(), item.currency.getId());
					
					item.record = WithdrawRecord.request(
						s.getSenderAddress(),
						item.currency.getName(),
						s.getTxHash(),
						s.getTime());
					
					this.items.add(item);
				}
			// Force withdraw
			} else {
				logger.trace("received force withdraw event log: {}", s);
				
				Item item = create(dao, s, address2IdMap);
				if (item != null) {
					item.record = WithdrawRecord.force(
						s.getSenderAddress(),
						item.currency.getName(),
						s.getTxHash(),
						item.account.getAvailable());
					
					this.items.add(item);
				}
			}
		}
	}
	
	private static Item create(DexDao dao, ScheduleWithdrawRequest data, Map<String, Long> address2IdMap) {
		Optional<Currency> currency = dao.getCurrencyByContractAddress(data.getContractAddress().getHexAddress()).get();
		if (!currency.isPresent()) {
			logger.error("cannot find currency for event log: {}", data);
			return null;
		}
		
		Long userId = address2IdMap.get(data.getSenderAddress());
		if (userId == null) {
			Optional<User> user = dao.getUserByName(data.getSenderAddress()).get();
			if (!user.isPresent()) {
				logger.info("cannot find user for event log: {}", data);
				return null;
			}
			
			userId = user.get().getId();
			address2IdMap.put(data.getSenderAddress(), userId);
		}
		
		Optional<Account> account = dao.getAccount(userId, currency.get().getName());
		if (!account.isPresent()) {
			logger.info("cannot find account, currency = {}, event log = {}", currency.get().getName(), data);
			return null;
		}
		
		// set withdraw record outside
		return new Item(null, currency.get(), account.get());
	}
	
	// must executed within a transaction.
	public void handle(DexDao dao) {
		for (Item item : this.items) {
			dao.mustAddWithdrawRecord(item.record);
			
			switch (item.record.getType()) {
			case OnChainRequest:
				dao.updateAccountStatus(item.account.getId(), AccountStatus.Normal, AccountStatus.ForceWithdrawing);
				break;
			case OnChainForce:
				AccountService.mustUpdateAccountBalance(logger, dao, item.account.getId(), BigDecimal.ZERO, item.account.getAvailable().negate());
				dao.updateAccountStatus(item.account.getId(), AccountStatus.ForceWithdrawing, AccountStatus.Normal);
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}
	}
	
	public void publish(AccountTopic topic, DexDao dao) {
		for (Item item : this.items) {
			if (item.record.getType() == WithdrawType.OnChainForce) {
				topic.publish(BalanceChangeType.Withdraw, item.account.getUserId(), item.account.getId(), true, true);
				Events.WITHDRAW_COMPLETED.fire(new WithdrawEventArg(item.record, item.account));
			}
		}
	}
}
