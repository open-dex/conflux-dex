package conflux.dex.dao;

import org.junit.Test;

public class CurrencyDaoImplTest {
    private final CurrencyDaoImpl currencyDao = new CurrencyDaoImpl();

    @Test(expected = NullPointerException.class)
    public void testAddCurrency() {
        TestDexDao dao = new TestDexDao();
        currencyDao.addCurrency(dao.cfx);
    }

    @Test(expected = NullPointerException.class)
    public void testListCurrencies() {
        currencyDao.listCurrencies();
    }

    @Test(expected = NullPointerException.class)
    public void testGetCurrency() {
        currencyDao.getCurrency(1);
    }

    @Test(expected = NullPointerException.class)
    public void testGetCurrencyByName() {
        currencyDao.getCurrencyByName("cfx");
    }

    @Test(expected = NullPointerException.class)
    public void testGetCurrencyByContractAddress() {
        currencyDao.getCurrencyByContractAddress("address");
    }
}
