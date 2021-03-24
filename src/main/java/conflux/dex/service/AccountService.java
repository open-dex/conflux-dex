package conflux.dex.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import conflux.dex.common.BusinessFault;
import conflux.dex.dao.AccountDao;
import conflux.dex.dao.UserDao;
import conflux.dex.model.Account;
import conflux.dex.model.PagingResult;
import conflux.dex.model.User;

@Service
public class AccountService {
	private AccountDao dao;
	
	@Autowired
	public AccountService(AccountDao dao) {
		this.dao = dao;
	}
	
	public static User getOrAddUser(UserDao userDao, String name) {
		Optional<User> existing = userDao.getUserByName(name).get();
		if (existing.isPresent()) {
			return existing.get();
		}
		
		User user = new User(name);
		if (userDao.addUser(user)) {
			return user;
		}
		
		return userDao.getUserByName(name).mustGet();
	}

	public Account getOrAddAccount(long userId, String currency, BigDecimal balance) {
		Optional<Account> existing = this.dao.getAccount(userId, currency);
		if (existing.isPresent()) {
			return existing.get();
		}
		
		Account account = new Account(userId, currency, balance);
		if (this.dao.addAccount(account)) {
			return account;
		}
		
		return mustGetAccount(dao, userId, currency);
	}
    public PagingResult<Account> listAccounts(long userId, int offset, int limit) {
		List<Account> list = dao.listAccounts(userId);
        return PagingResult.fromList(offset, limit, list);
    }
	//
	public static void mustUpdateAccountBalance(Logger logger, AccountDao dao, long accountId, BigDecimal holdDelta, BigDecimal availableDelta) {
		if (!dao.updateAccountBalance(accountId, holdDelta, availableDelta)) {
			if (logger != null) {
				logger.error("failed to update account balance, accountId = {}, holdDelta = {}, availableDelta = {}, account = {}",
						accountId, holdDelta, availableDelta, dao.getAccountById(accountId));
			}
			throw BusinessFault.AccountBalanceNotEnough.rise();
		}
	}
	public static void mustUpdateAccountBalance(AccountDao dao, long accountId, BigDecimal holdDelta, BigDecimal availableDelta){
		mustUpdateAccountBalance(null,dao, accountId, holdDelta, availableDelta);
	}

	public static Account mustGetAccount(AccountDao dao, long userId, String currency) {
		Optional<Account> account = dao.getAccount(userId, currency);
		if (!account.isPresent()) {
			throw BusinessFault.AccountNotFound.rise();
		}

		return account.get();
	}

	public static Account mustGetAccountById(AccountDao dao, long id){
		Optional<Account> account = dao.getAccountById(id);
		if (!account.isPresent()) {
			throw BusinessFault.AccountNotFound.rise();
		}

		return account.get();
	}
}
