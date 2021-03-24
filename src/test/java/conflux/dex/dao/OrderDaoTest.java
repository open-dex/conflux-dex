package conflux.dex.dao;

import conflux.dex.model.Order;
import conflux.dex.model.OrderFilter;
import conflux.dex.model.OrderStatus;
import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;

public class OrderDaoTest {
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

    @Test
    public void testListOrdersByStatus() {
        InMemoryOrderDao inMemoryOrderDao = new InMemoryOrderDao();
        OrderStatus status = OrderStatus.valueOf("Open");
        Assert.assertNotNull(inMemoryOrderDao.listOrdersByStatus(1L, status, 1, 1, true));
    }

    @Test
    public void testListOrdersByTimeRange() {
        InMemoryOrderDao inMemoryOrderDao = new InMemoryOrderDao();
        this.createTimestamp();
        Assert.assertNotNull(
                inMemoryOrderDao.listOrdersByTimeRange(1L, start, end,1, 1, true));
        Assert.assertNotNull(
                inMemoryOrderDao.listOrdersByTimeRange(1L, 1, start, end,1, 1, true));
    }

    @Test
    public void testGetOrders() {
        InMemoryOrderDao inMemoryOrderDao = new InMemoryOrderDao();
        Assert.assertNotNull(inMemoryOrderDao.getOrderByHash("o1"));
        Assert.assertNotNull(inMemoryOrderDao.getCancelOrderRequest(1L));
        Assert.assertNotNull(inMemoryOrderDao.getOrderByClientOrderId(1L, "alice"));
    }

    // test OrderDaoImpl
    @Test(expected = NullPointerException.class)
    public void testAddOrder() {
        Order order = new Order();
        OrderDaoImpl orderDao = new OrderDaoImpl();
        orderDao.addOrder(order);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrderDaoImplListOrdersByStatus() {
        OrderStatus status = OrderStatus.valueOf("open");
        OrderDaoImpl orderDao = new OrderDaoImpl();
        orderDao.listOrdersByStatus(1L, status, 1,1, true);
        orderDao.listOrdersByStatus(1L, 1, status, 1,1, true);
    }

    @Test(expected = NullPointerException.class)
    public void testOrderDaoImplListOrdersByTimeRange() {
        OrderDaoImpl orderDao = new OrderDaoImpl();
        this.createTimestamp();
        orderDao.listOrdersByTimeRange(1L, start, end, 1, 1, true);
    }

    @Test(expected = NullPointerException.class)
    public void testOrderDaoImplListOrdersByTimeRange2() {
        OrderDaoImpl orderDao = new OrderDaoImpl();
        this.createTimestamp();
        orderDao.listOrdersByTimeRange(1L, 1, start, end, 1, 1, true);
    }

    @Test(expected = NullPointerException.class)
    public void testOrderDaoImplListOrdersByPhase() {
        OrderDaoImpl orderDao = new OrderDaoImpl();
        OrderFilter.Phase filter = OrderFilter.Phase.Incompleted;
        this.createTimestamp();
        orderDao.listOrdersByPhase(1L, 1, filter, start, end, 1, 1, true);
    }

    @Test(expected = NullPointerException.class)
    public void testOrderDaoImplListOrdersByPhase2() {
        OrderDaoImpl orderDao = new OrderDaoImpl();
        OrderFilter.Phase filter = OrderFilter.Phase.Incompleted;
        this.createTimestamp();
        orderDao.listOrdersByPhase(1L, filter, start, end, 1, 1, true);
    }

}
