package conflux.dex.blockchain;

import conflux.dex.blockchain.log.DepositData;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.Currency;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class DepositDataTest {
    private DepositData newDepositData(Currency currency) {
        return new DepositData(currency.getContractAddress(), "tx1", "faucet", "u1", BigInteger.valueOf(123));
    }

    @Test
    public void TestToString() {
        TestDexDao dao = new TestDexDao();
        DepositData depositData = newDepositData(dao.cat);
        Assert.assertNotNull(depositData.toString());
    }
}
