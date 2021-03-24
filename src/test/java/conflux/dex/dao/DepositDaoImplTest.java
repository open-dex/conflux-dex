package conflux.dex.dao;

import conflux.dex.model.DepositRecord;
import org.easymock.EasyMock;
import org.junit.Test;

public class DepositDaoImplTest {
    private final DepositDaoImpl depositDao = new DepositDaoImpl();

    @Test(expected = NullPointerException.class)
    public void testAddDepositRecord() {
        DepositRecord record = EasyMock.createMock(DepositRecord.class);
        record.setId(EasyMock.anyInt());
        EasyMock.expectLastCall();
        EasyMock.replay(record);
        depositDao.addDepositRecord(record);
    }

    @Test(expected = NullPointerException.class)
    public void testListDepositRecords() {
        depositDao.listDepositRecords("u1", "cx", 1, 1, true);
    }

    @Test(expected = NullPointerException.class)
    public void testListDepositRecords2() {
        depositDao.listDepositRecords("u1",1, 1, true);
    }
}
