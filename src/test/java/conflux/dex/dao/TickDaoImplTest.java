package conflux.dex.dao;

import conflux.dex.model.Tick;
import org.junit.Test;

import java.util.Calendar;

public class TickDaoImplTest {
    private java.sql.Timestamp start;
    private java.sql.Timestamp end;

    private void createTimestamp() {
        Calendar calendar = Calendar.getInstance();
        java.util.Date now = calendar.getTime();
        java.sql.Timestamp start = new java.sql.Timestamp(now.getTime());
        java.sql.Timestamp end = new java.sql.Timestamp(now.getTime());
        this.start = start;
        this.end = end;
    }

    @Test(expected = NullPointerException.class)
    public void testAddTick() {
        Tick tick = new Tick();
        TickDaoImpl tickDao = new TickDaoImpl();
        tickDao.addTick(tick);
    }

    @Test(expected = NullPointerException.class)
    public void testGetLastTick() {
        TickDaoImpl tickDao = new TickDaoImpl();
        tickDao.getLastTick(1, 1);
    }

    @Test(expected = NullPointerException.class)
    public void testUpdateTick() {
        Tick tick = new Tick();
        TickDaoImpl tickDao = new TickDaoImpl();
        tickDao.updateTick(tick);
    }

    @Test(expected = NullPointerException.class)
    public void testListTicks() {
        TickDaoImpl tickDao = new TickDaoImpl();
        this.createTimestamp();
        tickDao.listTicks(1, 1, start, end);
    }

    @Test(expected = NullPointerException.class)
    public void testListTicks2() {
        TickDaoImpl tickDao = new TickDaoImpl();
        this.createTimestamp();
        tickDao.listTicks(1, 1, end, 1);
    }
}
