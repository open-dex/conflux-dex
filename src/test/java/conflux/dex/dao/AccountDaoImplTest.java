package conflux.dex.dao;

import conflux.dex.model.AccountStatus;
import conflux.dex.service.AccountService;
import org.easymock.EasyMock;
import org.junit.Test;
import java.math.BigDecimal;

public class AccountDaoImplTest {
    private final AccountDaoImpl accountDao = new AccountDaoImpl();

    @Test(expected = NullPointerException.class)
    public void testAddAccount() {
        TestDexDao dao = new TestDexDao();
        accountDao.addAccount(dao.aliceCat);
    }

    @Test(expected = NullPointerException.class)
    public void testUpdateAccountBalance() {
        accountDao.updateAccountBalance(1L, BigDecimal.valueOf(1L), BigDecimal.valueOf(1L));
    }

    @Test(expected = NullPointerException.class)
    public void testGetAccount() {
        accountDao.getAccount(1L, "CFX");
    }

    @Test(expected = NullPointerException.class)
    public void testGetAccountById() {
        accountDao.getAccountById(1L);
    }

    @Test(expected = NullPointerException.class)
    public void testListAccounts() {
        AccountService accountService = new AccountService(accountDao);
        accountService.listAccounts(1L, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateAccountStatus() {
        AccountStatus status = EasyMock.createMock(AccountStatus.class);
        accountDao.updateAccountStatus(1L, status, status);
    }

}
