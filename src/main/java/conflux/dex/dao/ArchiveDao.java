package conflux.dex.dao;

import conflux.dex.model.Order;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.UserTradeMap;
import org.slf4j.Logger;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Repository
public class ArchiveDao extends BaseDaoImpl{
    static final RowMapper<Order> orderRowMapper = BeanPropertyRowMapper.newInstance(Order .class);

    public List<Order> listOrderByIdRange(int lowerBoundInclusive, int upperBoundExclusive) {
        if (upperBoundExclusive <= lowerBoundInclusive) {
            return Collections.emptyList();
        }
        String sql = "select * from t_order where id >= ? and id < ?";
        return getJdbcTemplate().query(sql, orderRowMapper, lowerBoundInclusive, upperBoundExclusive);
    }

    public List<Order> listOrderByIds(Collection<Long> ids) {
        return listOrderByIds(ids, "t_order");
    }
    public List<Order> listOrderByIds(Collection<Long> ids, String table) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = String.format("select * from %s where id in (%s)", table,
                String.join(",", Collections.nCopies(ids.size(), "?")));
        return getJdbcTemplate().query(sql, ids.toArray(), orderRowMapper);
    }

    public List<Trade> listTradeByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        String inSql = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        String sql = String.join(" ",
                "SELECT t_trade.*",
                "FROM t_trade",
                "INNER JOIN ",
                " ( select distinct(trade_id) as trade_id from t_trade_order_map where order_id in (%s) ) m ",
                "ON t_trade.id = m.trade_id "
        );
        sql = String.format(sql, inSql);
        return getJdbcTemplate().query(sql, orderIds.toArray(), MiscDAO.tradeRowMapper);
    }

    public int deleteZeroFilledOrder(long minInclusive, long maxExclusive, Date beforeTime) {
        return getJdbcTemplate().update("delete from t_order where id >= ? and id < ? and create_time < ?" +
                        " and filled_amount = 0 and status = ?",
                minInclusive, maxExclusive, beforeTime, OrderStatus.Cancelled.name());
    }

    public int deleteOrderWithoutTradeMap(long minInclusive, long maxInclusive) {
        // delete cancelled and filled order that have no trade map (mapping is deleted after archiving.)
        return getJdbcTemplate().update("delete from t_order where id >= ? and id <= ? and status in (?,?)" +
                        " and not exists(select * from t_trade_order_map m where m.order_id = t_order.id)",
                minInclusive, maxInclusive, OrderStatus.Filled.name(), OrderStatus.Cancelled.name());
    }

    public Order findOrderReachTimeLimit(long minInclusive, long maxExclusive, Date beforeTime) {
        List<Order> list = getJdbcTemplate().query("select * from t_order where id >= ? and id < ? and create_time >= ? " +
                " order by id asc limit 1", orderRowMapper, minInclusive, maxExclusive, beforeTime);
        return list.size() > 0 ? list.get(0) : null;
    }

    public List<Trade> listTradeByIdRange(long minInclusive, long maxExclusive, Date beforeTime) {
        String sql = "select * from t_trade where id >= ? and id < ? and create_time < ? ";
        return getJdbcTemplate().query(sql, MiscDAO.tradeRowMapper, minInclusive, maxExclusive, beforeTime);
    }

    public Trade findTradeReachTimeLimit(long minInclusive, long maxExclusive, Date beforeTime) {
        String sql = "select * from t_trade where id >= ? and id < ? and create_time >= ? order by id asc limit 1";
        List<Trade> list = getJdbcTemplate().query(sql, MiscDAO.tradeRowMapper, minInclusive, maxExclusive, beforeTime);
        return list.size() > 0 ? list.get(0) : null;
    }

    public int archiveTrades(List<Trade> trades, String archiveTable, Logger log) {
        if (trades.isEmpty()) {
            log.debug("archiveTrades, empty.");
            return 0;
        }
        String sql = TradeDaoImpl.SQL_INSERT.replaceFirst("t_trade", archiveTable);
        Iterator<Trade> it = trades.iterator();
        return Arrays.stream(getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Trade order = it.next();
                TradeDao.fillStatement(ps, order);
            }

            @Override
            public int getBatchSize() {
                return trades.size();
            }
        })).sum();
    }
    public int archiveOrders(List<Order> archiveOrders, String archiveTable) {
        if (archiveOrders.isEmpty()) {
            return 0;
        }
        String sql = OrderDaoImpl.SQL_INSERT.replaceFirst("t_order", archiveTable);
        return Arrays.stream(getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Order order = archiveOrders.get(i);
                OrderDao.fillStatement(ps, order);
            }

            @Override
            public int getBatchSize() {
                return archiveOrders.size();
            }
        })).sum();
    }

    public int deleteUserTrades(List<UserTradeMap> userTrades) {
        if (userTrades.isEmpty()) {
            return 0;
        }
        String singleCondition = " ( user_id = ? and product_id = ? and create_time = ? and trade_id = ? ) ";
        String sql = "delete from t_trade_user_map where " + singleCondition;
        return Arrays.stream(getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter(){
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                int idx = 1;
                UserTradeMap e = userTrades.get(i);
                ps.setLong(idx++, e.getUserId());
                ps.setInt(idx++, e.getProductId());
                ps.setTimestamp(idx++, e.getCreateTime());
                ps.setLong(idx++, e.getTradeId());
            }
            public int getBatchSize() {
                return userTrades.size();
            }
        })).sum();
    }

    // delete both maker and taker order->trade mapping.
    public int deleteOrderTradeMap(List<Trade> trades) {
        if (trades.isEmpty()) {
            return 0;
        }
        String singleCondition = " ( order_id = ? and trade_id = ? ) ";
        String sql = "delete from t_trade_order_map where " + singleCondition;
        Iterator<Trade> it = trades.iterator();
        return Arrays.stream(getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter(){
            boolean maker = true;
            Trade cursor;
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                if (maker) {
                    cursor = it.next();
                    maker = false;
                    fillOrderTradeMap(ps, cursor, cursor.getMakerOrderId());
                } else {
                    fillOrderTradeMap(ps, cursor, cursor.getTakerOrderId());
                    maker = true;
                }
            }
            public int getBatchSize() {
                return trades.size() * 2;
            }
        })).sum();
    }

    private void fillOrderTradeMap(PreparedStatement ps, Trade e, long orderId) throws SQLException {
        ps.setLong(1, orderId);
        ps.setLong(2, e.getId());
    }


    public int deleteOrders(Collection<Long> ids) {
        return deleteByIds(ids, "t_order");
    }

    public int deleteTrades(List<Long> tradeIds) {
        return deleteByIds(tradeIds, "t_trade");
    }
}
