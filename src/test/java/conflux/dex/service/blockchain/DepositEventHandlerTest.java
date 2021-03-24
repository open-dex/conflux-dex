package conflux.dex.service.blockchain;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import conflux.dex.service.AccountService;
import org.junit.Test;

import conflux.dex.blockchain.log.DepositData;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.Account;
import conflux.dex.model.AccountStatus;
import conflux.dex.model.Currency;
import conflux.dex.model.DepositRecord;
import conflux.dex.model.User;
import conflux.dex.ws.topic.AccountTopic;

public class DepositEventHandlerTest {
	private static final String DEFAULT_TX_SENDER = "0x18236b0d7a24991ef251037a8d6871b9293b2e0d";

	private DepositData newDepositData(Currency currency, String txHash, String recipient, int amount) {
		return new DepositData(currency.getContractAddress(), txHash, DEFAULT_TX_SENDER, recipient, BigInteger.valueOf(amount));
	}
	
	@Test
	public void testNewUser() {
		TestDexDao dao = new TestDexDao();
		
		// new user "u1" deposit 123 tokens
		List<DepositData> deposits = Arrays.asList(newDepositData(dao.cat, "tx1", "u1", 123));
		DepositEventHandler handler = new DepositEventHandler(deposits, dao.get());
		handler.handle(dao.get());
		
		// new user/account created
		User newUser = dao.get().getUserByName("u1").mustGet();
		Account newAccount = AccountService.mustGetAccount(dao.get(), newUser.getId(), dao.cat.getName());
		assertNewAccount(newAccount, dao.cat, 123);
	}
	
	private void assertNewAccount(Account newAccount, Currency currency, int balance) {
		assertEquals(0, newAccount.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(AccountStatus.Normal, newAccount.getStatus());
		assertEquals(balance, currency.toIntegerFormat(newAccount.getAvailable()).intValue());
	}
	
	@Test
	public void testNewAccount() {
		// prepare new currency
		TestDexDao dao = new TestDexDao();
	    AccountService accountService = new AccountService(dao.get());
		Currency pig = new Currency("pig", "0x8af3d86ea2b7dd87496ecdb1b4bcc6e39284d355", "0x8b0af247b862f3965f3c0a5f94d70931517961b5", 2);
		dao.get().addCurrency(pig);
		int oldNumAccounts = accountService.listAccounts(dao.alice.getId(), 0, Integer.MAX_VALUE).getTotal();
		
		// Alice deposit 123 tokens for new currency
		List<DepositData> deposits = Arrays.asList(
				newDepositData(pig, "tx1", dao.alice.getName(), 100),
				newDepositData(pig, "tx2", dao.alice.getName(), 200));
		DepositEventHandler handler = new DepositEventHandler(deposits, dao.get());
		handler.handle(dao.get());
		
		// new account created only once
		int newNumAccounts = accountService.listAccounts(dao.alice.getId(), 0, Integer.MAX_VALUE).getTotal();
		assertEquals(oldNumAccounts + 1, newNumAccounts);
		
		// new account created for alice
		Account newAccount = AccountService.mustGetAccount(dao.get(), dao.alice.getId(), pig.getName());
		assertNewAccount(newAccount, pig, 300);
	}
	
	@Test
	public void testExistingAccount() {
		TestDexDao dao = new TestDexDao();
		BigDecimal oldBalance = dao.aliceCfx.getAvailable();
		
		// Alice deposit 123 tokens for existing currency
		List<DepositData> deposits = Arrays.asList(
				newDepositData(dao.cfx, "tx1", dao.alice.getName(), 100),
				newDepositData(dao.cfx, "tx1", dao.alice.getName(), 200));
		DepositEventHandler handler = new DepositEventHandler(deposits, dao.get());
		handler.handle(dao.get());
		
		// existing account updated
		BigDecimal delta = dao.aliceCfx.getAvailable().subtract(oldBalance);
		assertEquals(300, dao.cfx.toIntegerFormat(delta).intValue());
	}
	
	@Test
	public void testListRecords() {
		TestDexDao dao = new TestDexDao();
		
		List<DepositData> deposits = Arrays.asList(
				newDepositData(dao.cat, "tx1", "u1", 100),
				newDepositData(dao.cat, "tx2", "u1", 200),
				newDepositData(dao.cat, "tx3", "u1", 300),
				newDepositData(dao.cat, "tx4", "u2", 200),
				newDepositData(dao.cat, "tx5", "u3", 300));
		DepositEventHandler handler = new DepositEventHandler(deposits, dao.get());
		handler.handle(dao.get());
		AccountTopic accountTopic = new AccountTopic(dao.get());
		handler.publish(accountTopic, dao.get());
		
		// check for u1
		List<DepositRecord> records = dao.get().listDepositRecords("u1", dao.cat.getName(), 0, 10, true).getItems();
		assertEquals(3, records.size());
		assertDepositRecord(records.get(0), "u1", dao.cat, 100, "tx1");
		assertDepositRecord(records.get(1), "u1", dao.cat, 200, "tx2");
		assertDepositRecord(records.get(2), "u1", dao.cat, 300, "tx3");
		
		// check for u1 DESC
		records = dao.get().listDepositRecords("u1", dao.cat.getName(), 1, 2, false).getItems();
		assertEquals(2, records.size());
		assertDepositRecord(records.get(0), "u1", dao.cat, 200, "tx2");
		assertDepositRecord(records.get(1), "u1", dao.cat, 100, "tx1");
		
		// check for u2
		records = dao.get().listDepositRecords("u2", dao.cat.getName(), 0, 10, true).getItems();
		assertEquals(1, records.size());
		assertDepositRecord(records.get(0), "u2", dao.cat, 200, "tx4");
		
		// check for u3
		records = dao.get().listDepositRecords("u3", dao.cat.getName(), 0, 10, true).getItems();
		assertEquals(1, records.size());
		assertDepositRecord(records.get(0), "u3", dao.cat, 300, "tx5");
	}
	
	private void assertDepositRecord(DepositRecord record, String user, Currency currency, int amount, String txHash) {
		assertEquals(user, record.getUserAddress());
		assertEquals(currency.getName(), record.getCurrency());
		assertEquals(amount, currency.toIntegerFormat(record.getAmount()).intValue());
		assertEquals(DEFAULT_TX_SENDER, record.getTxSender());
		assertEquals(txHash, record.getTxHash());
	}
}
