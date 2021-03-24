package conflux.dex.dao;

import conflux.dex.model.SettlementStatus;
import conflux.dex.model.WithdrawRecord;
import org.junit.Test;

public class WithdrawDaoImplTest {

    @Test(expected = NullPointerException.class)
    public void testAddWithdrawRecord() {
        WithdrawDaoImpl withdrawDao = new WithdrawDaoImpl();
        withdrawDao.addWithdrawRecord(new WithdrawRecord());
    }

    @Test(expected = NullPointerException.class)
    public void testListWithdrawRecords() {
        WithdrawDaoImpl withdrawDao = new WithdrawDaoImpl();
        withdrawDao.listWithdrawRecords("u1", "cx", 0, 1, false);
    }

    @Test(expected = NullPointerException.class)
    public void testListWithdrawRecords2() {
        WithdrawDaoImpl withdrawDao = new WithdrawDaoImpl();
        withdrawDao.listWithdrawRecords("u1", 0, 1, false);
    }

    @Test(expected = NullPointerException.class)
    public void testListWithdrawRecordsByStatus() {
        WithdrawDaoImpl withdrawDao = new WithdrawDaoImpl();
        withdrawDao.listWithdrawRecordsByStatus(SettlementStatus.OnChainConfirmed);
    }
}
