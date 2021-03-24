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

import conflux.dex.blockchain.log.DepositData;
import conflux.dex.dao.DexDao;
import conflux.dex.event.DepositEventArg;
import conflux.dex.event.Events;
import conflux.dex.model.Account;
import conflux.dex.model.BalanceChangeType;
import conflux.dex.model.Currency;
import conflux.dex.model.DepositRecord;
import conflux.dex.model.User;
import conflux.dex.ws.topic.AccountTopic;

/**
 * Batch handle deposit event logs within a transaction.
 */
public class DepositEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(DepositEventHandler.class);
	
	private static final int ID_NOT_EXISTS = -1;
	
	private static class AccountItem {
		public long accountId;
		public BigDecimal amount;
		
		public AccountItem(Optional<Account> account, BigDecimal amount) {
			this.accountId = account.isPresent() ? account.get().getId() : ID_NOT_EXISTS;
			this.amount = amount;
		}
	}
	
	private static class UserItem {
		public long userId;
		public Map<String, AccountItem> accounts = new HashMap<String, AccountItem>();
		
		public UserItem(DexDao dao, String userAddress) {
			Optional<User> user = dao.getUserByName(userAddress).get();
			this.userId = user.isPresent() ? user.get().getId() : ID_NOT_EXISTS;
		}
		
		public void add(DexDao dao, Currency currency, BigInteger amount) {
			BigDecimal amountDecimal = currency.toDecimalFormat(amount);
			
			AccountItem item = this.accounts.get(currency.getName());
			if (item != null) {
				item.amount = item.amount.add(amountDecimal);
				return;
			}
			
			Optional<Account> account = this.userId == ID_NOT_EXISTS
					? Optional.empty()
					: dao.getAccount(this.userId, currency.getName());
			item = new AccountItem(account, amountDecimal);
			this.accounts.put(currency.getName(), item);
		}
	}
	
	// key: user address
	private Map<String, UserItem> items = new HashMap<String, UserItem>();
	private List<DepositRecord> records = new LinkedList<DepositRecord>();
	
	public DepositEventHandler(List<DepositData> deposits, DexDao dao) {
		for (DepositData data : deposits) {
			logger.trace("received deposit event log: {}", data);
			
			Optional<Currency> currency = dao.getCurrencyByContractAddress(data.getContractAddress().getHexAddress()).get();
			if (!currency.isPresent()) {
				logger.error("failed to find currency for event log: {}", data);
				continue;
			}
			
			this.records.add(convert(data, currency.get()));
		
			// aggregate by user
			String userAddress = data.getRecipientAddress();
			
			UserItem item = this.items.get(userAddress);
			if (item == null) {
				item = new UserItem(dao, userAddress);
				this.items.put(userAddress, item);
			}
			
			item.add(dao, currency.get(), data.getAmount());
		}
	}
	
	private static DepositRecord convert(DepositData data, Currency currency) {
		return DepositRecord.create(
				data.getRecipientAddress(),
				currency.getName(),
				currency.toDecimalFormat(data.getAmount()),
				data.getSenderAddress(),
				data.getTxHash());
	}
	
	// must executed within a transaction.
	public void handle(DexDao dao) {
		// update account balance
		for (Map.Entry<String, UserItem> userEntry : this.items.entrySet()) {
			UserItem userItem = userEntry.getValue();
			
			// add user if not exists
			if (userItem.userId == ID_NOT_EXISTS) {
				User user = new User(userEntry.getKey());
				dao.addUser(user);
				userItem.userId = user.getId();
				logger.trace("new user created for deposit event, id = {}, address = {}", user.getId(), userEntry.getKey());
			}
			
			// deposit for each account
			for (Map.Entry<String, AccountItem> accountEntry : userItem.accounts.entrySet()) {
				String currency = accountEntry.getKey();
				AccountItem accountItem = accountEntry.getValue();
				
				if (accountItem.accountId == ID_NOT_EXISTS) {
					Account account = new Account(userItem.userId, currency, accountItem.amount);
					dao.addAccount(account);
					accountItem.accountId = account.getId();
					logger.trace("new account created for deposit event: {}", account);
				} else {
					AccountService.mustUpdateAccountBalance(logger, dao, accountItem.accountId, BigDecimal.ZERO, accountItem.amount);
				}
			}
		}
		
		// add deposit records after user added due to user address dependency
		for (DepositRecord record : records) {
			dao.addDepositRecord(record);
		}
	}
	
	public void publish(AccountTopic topic, DexDao dao) {
		Map<String, Long> users = new HashMap<String, Long>();
		
		for (Map.Entry<String, UserItem> entry : this.items.entrySet()) {
			long userId = entry.getValue().userId;
			users.put(entry.getKey(), userId);
			
			for (AccountItem accountItem : entry.getValue().accounts.values()) {
				topic.publish(BalanceChangeType.Deposit, userId, accountItem.accountId, true, true);
			}
		}
		
		Events.DEPOSIT.fire(new DepositEventArg(this.records, users));
	}
}
