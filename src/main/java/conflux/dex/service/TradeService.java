package conflux.dex.service;

import conflux.dex.dao.TradeDao;
import conflux.dex.event.Events;
import conflux.dex.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradeService {
    public static final int MAX_LIST_SIZE = 200;
    protected Logger logger = LoggerFactory.getLogger(getClass());

    private TradeDao dao;
    /**
     * product id -> cache
     */
    private Map<Integer, LinkedList<Trade>> product2cache = new ConcurrentHashMap<>();

    @Autowired
    public TradeService(TradeDao dao) {
        this.dao = dao;
        Events.ORDER_MATCHED.addHandler(t->this.addTradeCache(t.getTrade()));
    }

    /**
     * Cache contains latest trades, add newest one, and remove oldest if needed.
     * @param trade
     */
    public void addTradeCache(Trade trade) {
        int key = trade.getProductId();
        LinkedList<Trade> cache = getCacheByProductId(key);
        // add new one, remove oldest one.
        // copy on write. perhaps use readwrite lock.
        LinkedList<Trade> newCache = new LinkedList<>(cache);
        newCache.addFirst(trade);
        if (newCache.size() > MAX_LIST_SIZE) {
            newCache.removeLast();
        }
        product2cache.put(key, newCache);
    }

    /**
     * Retrieve latest trades by productId, limit by size.
     * if it haven't been cached, load trades from database under
     * limit {@link TradeService#MAX_LIST_SIZE}, then return a sub list.
     * @param productId productId
     * @param size limit
     * @return
     */
    public List<Trade> getLatest(int productId, int size) {
        List<Trade> trades = getCacheByProductId(productId);
        List<Trade> sub = trades.subList(0, Math.min(size, trades.size()));
        return sub;
    }

    private LinkedList<Trade> getCacheByProductId(int productId) {
        return product2cache.computeIfAbsent(productId,
                k -> new LinkedList<>(dao.listRecentTrades(k,0, MAX_LIST_SIZE)));
    }
}
