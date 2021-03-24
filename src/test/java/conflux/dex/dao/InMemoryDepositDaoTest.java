package conflux.dex.dao;

import conflux.dex.model.DepositRecord;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class InMemoryDepositDaoTest {

    private final InMemoryDepositDao inMemoryDepositDao = new InMemoryDepositDao();

    @Test
    public void testGetDailyLimitRateByProductId() {
        DepositRecord record = EasyMock.createMock(DepositRecord.class);
        record.setId(EasyMock.anyInt());
        EasyMock.expectLastCall();
        EasyMock.expect(record.getId()).andReturn(1L);
        EasyMock.replay(record);
        inMemoryDepositDao.addDepositRecord(record);
    }

    @Test
    public void testListDepositRecords() {
        DepositRecord record = EasyMock.createMock(DepositRecord.class);
        record.setId(EasyMock.anyInt());
        EasyMock.expectLastCall();
        EasyMock.expect(record.getId()).andReturn(1L);
        EasyMock.replay(record);
        Assert.assertNotNull(inMemoryDepositDao.listDepositRecords("u1", "cfx", 1, 1, true));
    }
}
