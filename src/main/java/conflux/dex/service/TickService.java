package conflux.dex.service;

import conflux.dex.dao.TickDao;
import conflux.dex.event.Events;
import conflux.dex.model.Tick;
import conflux.dex.worker.ticker.TickGranularity;
import conflux.dex.worker.ticker.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TickService {
    public static final int MAX_LIST_SIZE = 2000;
    protected Logger logger = LoggerFactory.getLogger(getClass());

    private TickDao dao;
    /**
     * product id -> value
     *                  \
     *                  granularity -> TickData(holds ticks in tree map and candle line in list)
     */
    private Map<Integer, Map<Integer, TickData>> product2cache = new ConcurrentHashMap<>();

    @Autowired
    public TickService(TickDao dao) {
        this.dao = dao;
        Events.TICK_CHANGED.addHandler(this::addTickCache);
    }

    /**
     * Cache contains latest ticks and candle line.
     * @param tick
     */
    public void addTickCache(Tick tick) {
        int granularity = tick.getGranularity();
        TickData cache = getCache(tick.getProductId(), granularity);

        TickGranularity g = getTickGranularity(granularity);
        Instant end = tick.getCreateTime().toInstant();
        synchronized (cache){
            Map.Entry<Timestamp, Tick> lastEntry = cache.candleLine.lastEntry();
            if (lastEntry != null) {
                Tick lastTick = lastEntry.getValue();
                // It's an update so just copy properties.
                if (lastTick.getCreateTime().equals(tick.getCreateTime())) {
                    BeanUtils.copyProperties(tick, lastTick);
                    return;
                } else if(lastTick.getCreateTime().before(tick.getCreateTime())){
                    // last entry is before current one
                    // expand to earlier tick time.
                    expandLastTick(g, cache, g.earlierOne(end));
                } else {
                    // last entry is after current one
                    while(cache.candleLine.size() > 0
                            && !cache.candleLine.lastEntry().getKey().before(tick.getCreateTime())) {
                        // remove tail.
                        cache.candleLine.pollLastEntry();
                    }
                }
            }
            cache.candleLine.put(tick.getCreateTime(), tick);
            cache.end = end;
            checkCacheSize(cache);
        }
    }

    /**
     * It's complex to control expanding/updating cache, so, make it synchronized.
     */
    public synchronized List<Tick> getLatest(int product, TickGranularity granularity, int limit, Instant end) {
        TickData cache = getCache(product, granularity.getValue());
        if (cache.candleLine.isEmpty()) {
            return Collections.emptyList();
        }

        Instant targetTruncatedEnd = granularity.truncate(end);
        if (cache.end.isBefore(targetTruncatedEnd)) {
            expandLastTick(granularity, cache, targetTruncatedEnd);
        }
        //
        Timestamp endTsInclude = Timestamp.from(targetTruncatedEnd);
        Timestamp startExclusive = Timestamp.from(granularity.diff(targetTruncatedEnd, -limit));
        Collection<Tick> ret =
                cache.candleLine.subMap(startExclusive, false, endTsInclude, true)
                        .values();

        if (ret.size() == limit || cache.databaseSize < MAX_LIST_SIZE) {
            // full filled or database doesn't have more record.
        } else {
            ret = this.loadTickData(product, granularity.getValue(), limit, end).candleLine.values();
        }
        return new ArrayList<>(ret);
    }

    // expand last tick, to targetTruncatedEnd(inclusive)
    private void expandLastTick(TickGranularity granularity, TickData cache, Instant targetTruncatedEnd) {
        if (cache.candleLine.isEmpty()) {
            return;
        }
        // if tick data is updated timely, this will rarely happens,
        // because : truncated target end time should always be the same as cache end time in one granularity period.
        // fill with last tick, until time reach target end
        Tick last = cache.candleLine.lastEntry().getValue();//
        Timestamp cacheTime = last.getCreateTime();
        Timestamp endTimestamp = Timestamp.from(targetTruncatedEnd);

        boolean expanded = false;
        while (endTimestamp.after(cacheTime)) {
            Instant laterOne = granularity.laterOne(cacheTime.toInstant());
            cacheTime = Timestamp.from(laterOne);
            cache.candleLine.put(cacheTime, Tick.placeholder(last, cacheTime));
            expanded = true;
        }
        if (!expanded) {
            return;
        }
        // update end time.
        cache.end = cacheTime.toInstant();
        //
        checkCacheSize(cache);
    }

    private void checkCacheSize(TickData cache) {
        int maxListSize = MAX_LIST_SIZE * 2;
        while((cache.candleLine.size() > maxListSize)) {
            cache.candleLine.pollFirstEntry();
        }
    }


    public static List<Tick> toCandleLines(Collection<Tick> tradeTicks, int limit, TickGranularity granularity, Instant end) {
        return new ArrayList<>(toCandleLinesTree(tradeTicks, limit, granularity, end).values());
    }
    public static TreeMap<Timestamp, Tick> toCandleLinesTree(Collection<Tick> tradeTicks, int limit, TickGranularity granularity, Instant end) {
        if (tradeTicks.isEmpty()) {
            return new TreeMap<>();
        }

        // Supplement the missed tick in case of no trade in a short time.
        // lines are in ascending order
        TreeMap<Timestamp, Tick> lines = new TreeMap<>();
        Instant time = granularity.truncate(end);
        // ticks from database are in descending order
        Iterator<Tick> iterator = tradeTicks.iterator();
        Tick lastTick = iterator.next();
        Instant lastTime = lastTick.getCreateTime().toInstant();

        for (int i = 0; i < limit; i++) {
            if (lastTime.isBefore(time)) {
                // fill gap
                Timestamp tickTs = Timestamp.from(time);
                lines.put(tickTs, Tick.placeholder(lastTick, tickTs));
            } else {
                lines.put(lastTick.getCreateTime(), lastTick);

                if (!iterator.hasNext()) {
                    break;
                }

                lastTick = iterator.next();
                lastTime = lastTick.getCreateTime().toInstant();
            }
            // minus one second and truncate, makes tick time move to earlier period
            time = granularity.earlierOne(time);
        }

        return lines;
    }

    private TickData getCache(int productId, int granularity) {
        // List<Tick> ticks = this.dao.listTicks(productId, granularity.getValue(), Timestamp.from(end), limit);
        Map<Integer, TickData> granularities = product2cache
                .computeIfAbsent(productId, k->new ConcurrentHashMap<>());
        TickData tickData = granularities.computeIfAbsent(granularity,
                g -> {
                    Instant now = Instant.now();
                    // load latest max size records to cover most request.
                    return loadTickData(productId, g, MAX_LIST_SIZE, now);
                });
        return tickData;
    }

    private TickData loadTickData(int productId, Integer granularity, int limit, Instant end) {
        Timestamp endTs = Timestamp.from(end);
        List<Tick> ticksFromDB = dao.listTicks(productId, granularity, endTs, limit);
        TreeMap<Long, Tick> ticks = ticksFromDB
                .stream()
                .collect(Collectors.toMap(Tick::getId, Function.identity(), (k1, k2) -> k1,
                        // chang default ascending to descending
                        () -> new TreeMap<>(Collections.reverseOrder())));
        TickData data = new TickData();
        data.databaseSize = ticks.size();
        TickGranularity g = getTickGranularity(granularity);
        data.candleLine = toCandleLinesTree(ticksFromDB, limit, g, end);
        data.end = end;
        return data;
    }

    private TickGranularity getTickGranularity(int granularity) {
        return Ticker.value2granularityMap.get(granularity);
    }

    private static class TickData{
        Instant end;
        int databaseSize;
        TreeMap<Timestamp, Tick> candleLine;
    }
}
