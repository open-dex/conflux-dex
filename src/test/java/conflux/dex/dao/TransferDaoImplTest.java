package conflux.dex.dao;

import conflux.dex.model.SettlementStatus;
import conflux.dex.model.TransferRecord;
import org.junit.Test;

public class TransferDaoImplTest {

    @Test(expected = NullPointerException.class)
    public void testAddTransferRecord() {
        TransferDaoImpl transferDao = new TransferDaoImpl();
        TransferRecord record = new TransferRecord();
        transferDao.addTransferRecord(record);
    }

    @Test(expected = NullPointerException.class)
    public void testUpdateTransferSettlement() {
        TransferDaoImpl transferDao = new TransferDaoImpl();
        SettlementStatus status = SettlementStatus.OffChainSettled;
        transferDao.updateTransferSettlement(1L, status, "tx", 0L);
    }

    @Test(expected = NullPointerException.class)
    public void testListTransferRecords() {
        TransferDaoImpl transferDao = new TransferDaoImpl();
        transferDao.listTransferRecords("u1", "cfx", 0, 1 , true);
    }

    @Test(expected = NullPointerException.class)
    public void testListTransferRecords2() {
        TransferDaoImpl transferDao = new TransferDaoImpl();
        transferDao.listTransferRecords("u1", 0, 1 , true);
    }

    @Test(expected = NullPointerException.class)
    public void testListTransferRecordsByStatus() {
        TransferDaoImpl transferDao = new TransferDaoImpl();
        SettlementStatus status = SettlementStatus.OffChainSettled;
        transferDao.listTransferRecordsByStatus(status);
    }
}
