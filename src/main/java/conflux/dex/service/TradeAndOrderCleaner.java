package conflux.dex.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import conflux.dex.dao.ConfigDao;
import conflux.dex.model.Order;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.UserTradeMap;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TradeAndOrderCleaner extends AbstractCleaner {
    private String orderTableName;
    private String tradeTableName;
    private List<String> userAddresses = Collections.emptyList();

    public TradeAndOrderCleaner(String positionKey, String timeGapKey, String batchSizeKey, List<String> userAddresses) {
        this(positionKey, timeGapKey, batchSizeKey);
        this.userAddresses = userAddresses;
    }
    public TradeAndOrderCleaner(String positionKey, String timeGapKey, String batchSizeKey) {
        super(positionKey, timeGapKey, batchSizeKey);
        init();
    }

    private void init() {
        // Default archive table and config are created in initdb.sql.
        orderTableName = getTableName(ConfigDao.KEY_ORDER_ARCHIVE_TABLE);
        tradeTableName = getTableName(ConfigDao.KEY_TRADE_ARCHIVE_TABLE);
    }

    private String getTableName(String key) {
        return dexDao.getConfig(key).orElseThrow(() ->
                new IllegalArgumentException(
                        String.format("Archive table not set, key %s", key))
                );
    }

    @Override
    protected List<String> getUserAddresses() {
        return userAddresses;
    }

    @Override
    protected long doWork(int pos, int batchSize, Date timeLimit) {
        logger.trace("\n begin {} pos {}, size {}, time limit {}", positionKey, pos, batchSize, timeLimit);
        int upperBound = pos + batchSize;
        // find trade by id range
        // check create time (it's in sql now)
        List<Trade> trades = archiveDao.listTradeByIdRange(pos, upperBound, timeLimit);
        // load both side order
        Set<Long> bothSideOrderIds = new HashSet<>(trades.size() * 2);
        trades.stream().flatMap(t -> Stream.of(t.getMakerOrderId(), t.getTakerOrderId()))
                .forEach(bothSideOrderIds::add);
        ImmutableMap<Long, Order> orderMap = Maps.uniqueIndex(
                archiveDao.listOrderByIds(bothSideOrderIds), Order::getId
        );
        // check both side user id in scope. if refers to other user, do nothing for the trade.
        logger.trace("trades size {}", trades.size());
        trades = filterByUser(trades, orderMap);
        logger.trace("trades size after filter user {}", trades.size());
        // both side order should be Filled or Cancelled.
        final List<Trade> archTrades = trades.stream().filter(t->checkBothSideOrderComplete(t, orderMap))
                .collect(Collectors.toList());
        logger.trace("trades size after filter order status {}", trades.size());
        trades = null; // prevent mistake usage.
        // build archive orders, skip already archived ones.
        final Set<Long> preArchivedOrderIds = archiveDao.listOrderByIds(bothSideOrderIds, orderTableName)
                .stream().map(Order::getId).collect(Collectors.toSet());
        // unique order
        Set<Long> uniqOrderIds = new HashSet<>(archTrades.size() * 2);
        final List<Order> archOrders = archTrades.stream().flatMap(t -> Stream.of(t.getMakerOrderId(), t.getTakerOrderId()))
                .filter(orderId -> !preArchivedOrderIds.contains(orderId))
                .filter(uniqOrderIds::add) // unique order
                .map(orderMap::get).collect(Collectors.toList());
        // build order trade map to delete
        final List<UserTradeMap> userTrades = computeUserTradeMap(archTrades, orderMap);
        final List<Long> tradeIds = Lists.transform(archTrades, Trade::getId);
        Optional<Long> min = orderMap.keySet().stream().min(Long::compareTo);
        Optional<Long> max = orderMap.keySet().stream().max(Long::compareTo);

        if (!archTrades.isEmpty()) dexDao.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // archive both side order
                int archOrderCnt = archiveDao.archiveOrders(archOrders, orderTableName);
                // archive trade
                int archTradeCnt = archiveDao.archiveTrades(archTrades, tradeTableName, logger);
                // delete order->trade map
                int orderTradeMapDeleted = archiveDao.deleteOrderTradeMap(archTrades);
                // delete user->trade map
                int userTradeMapDeleted = archiveDao.deleteUserTrades(userTrades);
                // delete t_trade
                int tradesDeleted = archiveDao.deleteTrades(tradeIds);
                // delete order without order->trade map.
                // others that still refers to other trade won't be deleted
                int deletedOrder = archiveDao.deleteOrderWithoutTradeMap(min.get(), max.get());

                logger.debug("delete order count {}, archive order {}, already archived order {}, cleaner {}"
                        , deletedOrder, archOrderCnt, preArchivedOrderIds.size(), positionKey);
                logger.debug("delete user trade map {}, order trade map {}, trades {}, expect archive trade {} actual {}",
                        userTradeMapDeleted, orderTradeMapDeleted, tradesDeleted, archTrades.size(), archTradeCnt);
            }
        });
        Trade tradeReachTimeLimit = archiveDao.findTradeReachTimeLimit(pos, upperBound, timeLimit);
        if (tradeReachTimeLimit == null) {
            return upperBound;
        }
        return tradeReachTimeLimit.getId();
    }

    private boolean checkBothSideOrderComplete(Trade trade, ImmutableMap<Long, Order> orderMap) {
        return checkOrderComplete(orderMap.get(trade.getMakerOrderId()))
                && checkOrderComplete(orderMap.get(trade.getTakerOrderId()));
    }

    private boolean checkOrderComplete(Order order) {
        OrderStatus status = order.getStatus();
        return status.equals(OrderStatus.Cancelled) || status.equals(OrderStatus.Filled);
    }

    private List<Trade> filterByUser(List<Trade> trades, ImmutableMap<Long, Order> orderMap) {
        List<Long> userIds = buildUserIds();
        logger.trace("user ids {}", userIds);
        return trades.stream().filter(t ->
                userIdInRange(userIds, orderMap, t.getMakerOrderId())
                        && userIdInRange(userIds, orderMap, t.getTakerOrderId())
        ).collect(Collectors.toList());
    }

    private boolean userIdInRange(List<Long> userIds, ImmutableMap<Long, Order> orderMap, long orderId) {
        return userIds.isEmpty() || userIds.contains(orderMap.get(orderId).getUserId());
    }

    private List<UserTradeMap> computeUserTradeMap(List<Trade> trades, Map<Long, Order> orderIdMap) {
        List<UserTradeMap> list = new ArrayList<>(trades.size() * 2);
        trades.forEach(t -> {
            buildUserTradeMap(t, t.getTakerOrderId(), orderIdMap, list);
            buildUserTradeMap(t, t.getMakerOrderId(), orderIdMap, list);
        });
        return list;
    }

    private void buildUserTradeMap(Trade trade, long orderId, Map<Long, Order> orderIdMap, List<UserTradeMap> list) {
        Order order = orderIdMap.get(orderId);
        if (order == null) {
            logger.warn("Order not found, buildUserTradeMap, oid {}, tid {}", orderId, trade.getId());
            return;
        }
        UserTradeMap entry = new UserTradeMap();
        entry.setUserId(order.getUserId());
        entry.setProductId(order.getProductId());
        entry.setCreateTime(trade.getCreateTime());
        entry.setTradeId(trade.getId());
        list.add(entry);
    }

    public void setUserAddresses(List<String> userAddresses) {
        this.userAddresses = userAddresses;
    }
}
