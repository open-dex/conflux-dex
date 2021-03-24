package conflux.dex.service;

import conflux.dex.dao.DexDao;
import conflux.dex.model.Tick;
import conflux.dex.worker.ticker.DefaultTickGranularity;
import conflux.dex.worker.ticker.Ticker;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TickServiceTest {
    TickService tickService;
    DexDao dexDao;

    @Test
    public void testTickTimeMoving() {
        Instant instant = Instant.now();
        Arrays.stream(Ticker.DEFAULT_GRANULARITIES).forEach(g->{
            Instant later = g.laterOne(instant);
            if (g != DefaultTickGranularity.Month) {
                long diffMinutes = Duration.between(instant, later).toMinutes();
                Assert.assertEquals("later with right minutes",
                        g.getValue(), diffMinutes);
            } else {
                int laterM = later.atZone(ZoneId.systemDefault()).getMonthValue();
                int M = instant.atZone(ZoneId.systemDefault()).getMonthValue();
                Assert.assertTrue("later with right month",
                        (laterM == 1 && M == 12) || (laterM - M == 1) );
            }
        });
    }

    Instant beginTime = Instant.now()
            .minus(TickService.MAX_LIST_SIZE / 2, ChronoUnit.MINUTES);
    Logger log = LoggerFactory.getLogger(getClass());
    @Test
    public void whenDBisEmpty() {
        int pid = 1;
        int minutesPassed =  1;
        //
        dexDao = Mockito.mock(DexDao.class);
        DefaultTickGranularity g = DefaultTickGranularity.Minute;
        beginTime = g.truncate(beginTime);
        log.info("begin time is {}", beginTime);
        // when db is empty
        Mockito.when(dexDao
                .listTicks(Mockito.eq(1), Mockito.anyInt(), Mockito.any(), Mockito.anyInt()))
                .thenReturn(Collections.EMPTY_LIST);

        tickService = new TickService(dexDao);


        Instant end = Instant.now();
        List<Tick> latest = tickService.getLatest(pid, g, 10, end);
        Assert.assertTrue("should be empty", latest.isEmpty());

        // Add first one,
        Tick tick1 = buildTick(pid, minutesPassed++, g);
        tickService.addTickCache(tick1);
        // get , with time later 1 second.
        Instant timeAfterTick1_1s = tick1.getCreateTime().toInstant().plus(1, ChronoUnit.SECONDS);
        List<Tick> latest1 =
                tickService.getLatest(pid, g,
                TradeService.MAX_LIST_SIZE,
                        timeAfterTick1_1s);
        Assert.assertEquals("should have the first tick",
                tick1.getOpen(),
                latest1.get(0).getOpen());
        Assert.assertEquals("should have one tick",
                1,
                latest1.size());

        // another tick
        Tick tick2 = buildTick(pid, minutesPassed++, g);
        tickService.addTickCache(tick2);
        Instant timeAfterTick2_1s = tick2.getCreateTime().toInstant().plus(1, ChronoUnit.SECONDS);
        List<Tick> ticks = tickService.getLatest(pid, g, 11,
                timeAfterTick2_1s);
        Assert.assertEquals("should have  two ticks",
                2, ticks.size());
        Assert.assertTrue("should be in ascending order",
                ticks.get(0).getCreateTime().before(ticks.get(1).getCreateTime()));


        // update status
        tickService.addTickCache(tick2);
        ticks = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE, timeAfterTick2_1s);
        Assert.assertEquals("should have  two ticks",
                2, ticks.size());
        Assert.assertTrue("should updated", ticks.stream().anyMatch(t->t.getOpen().equals(tick2.getOpen())));

        // test earlier end time
        ticks = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE,
                timeAfterTick1_1s);
        Assert.assertEquals("should have one tick",
                1 , ticks.size());
        Assert.assertTrue("should have the tick",
                ticks.get(0).equals(tick1));

        // test expand
        Tick tick3 = buildTick(pid, minutesPassed++, g);
        // create the right tick3, bug doesn't add, it should expand.
        Instant timeAfterTick3_1s = tick3.getCreateTime().toInstant().plus(1, ChronoUnit.SECONDS);
        ticks = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE, timeAfterTick3_1s);
        Assert.assertEquals("should have 3 ticks",
                3 , ticks.size());
        Assert.assertEquals("the 3rd tick should be an expanded one.",
                0, ticks.get(2).getId());

        // then , add the 3rd tick
        tickService.addTickCache(tick3);
        ticks = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE, timeAfterTick3_1s);
        Assert.assertEquals("should have 3 ticks",
                3 , ticks.size());
        Assert.assertEquals("should have the tick", ticks.get(2).getId(), tick3.getId());

        // trigger expanding
        Instant timeAfterTick3_10m = tick3.getCreateTime().toInstant().plus(10, ChronoUnit.MINUTES);
        ticks = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE, timeAfterTick3_10m);
        Assert.assertEquals("should have 13 ticks",
                13 , ticks.size());
        // add tick2 again, simulate that tick comes late.
        tickService.addTickCache(tick3);
        ticks = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE, timeAfterTick2_1s);
        Assert.assertEquals("should have two ticks, when tick comes late",
                2, ticks.size());


        // test entries over limit
        int baseId = 10;
        int largeCountBase = Integer.MAX_VALUE/2;
        Tick lastTick = null;
        Tick firstTick = null;
        for (int i = 0; i < TickService.MAX_LIST_SIZE; i++) {
            Tick tk = buildTick(1, i + baseId, g);
            tk.setOpen(BigDecimal.valueOf(largeCountBase + i));
            tickService.addTickCache(tk);
            lastTick = tk;
            if (i == 0) {
                firstTick = tk;
            }
        }
        Instant timeAfterLastTick_1s = lastTick.getCreateTime().toInstant().plus(1, ChronoUnit.SECONDS);
        List<Tick> latestAll = tickService.getLatest(pid, g, TickService.MAX_LIST_SIZE, timeAfterLastTick_1s);
        Assert.assertEquals("should have max size", TickService.MAX_LIST_SIZE, latestAll.size());
        Assert.assertTrue("should evict oldest",
                latestAll.get(0).getId() == firstTick.getId());
    }

    private Tick buildTick(int productIdAndName, int id, DefaultTickGranularity g) {
        Tick t = new Tick();
        t.setProductId(productIdAndName);
        t.setId(id);
        t.setOpen(BigDecimal.valueOf(id));
        t.setClose(t.getOpen());
        t.setGranularity(g.getValue());
        // simulate time difference, id is smaller, creation time is earlier
        Instant instant = beginTime.plus(id, ChronoUnit.MINUTES);
        t.setCreateTime(Timestamp.from(instant));
        log.info("create tick {} , time is {}", id, instant);
        return t;
    }
}
