package conflux.dex.service;

import com.codahale.metrics.Histogram;
import com.google.common.collect.Lists;
import conflux.dex.common.Metrics;
import conflux.dex.dao.ArchiveDao;
import conflux.dex.dao.ConfigDao;
import conflux.dex.dao.DexDao;
import conflux.dex.model.User;
import conflux.dex.tool.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class AbstractCleaner {
    protected boolean paused = false;
    protected String message;
    protected Logger logger = LoggerFactory.getLogger(getClass());
    private static final Histogram latencyStat = Metrics.histogram(CleanerService.class, "latency");
    // assume the TPS is 10 and clean every 5 seconds.
    private int DEFAULT_CLEAN_BATCH_SIZE = 50;
    protected String positionKey;
    private String timeGapKey;
    private String batchSizeKey;
    private ConfigDao configDao;
    protected DexDao dexDao;
    protected ArchiveDao archiveDao;

    public AbstractCleaner(String positionKey, String timeGapKey, String batchSizeKey) {
        this.positionKey = positionKey;
        this.timeGapKey = timeGapKey;
        this.batchSizeKey = batchSizeKey;
        initBeans();
    }

    protected void initBeans() {
        dexDao = SpringTool.getBean(DexDao.class);
        archiveDao = SpringTool.getBean(ArchiveDao.class);
        configDao = SpringTool.getBean(ConfigDao.class);
    }

    public void run() {
        if (paused) {
            return;
        }
        long startMS = System.currentTimeMillis();
        try {
            runUnsafe();
        } catch (Exception e) {
            paused = true;
            message = e.toString();
            logger.error("exception for {}", positionKey, e);
        } finally {
            long elapse = System.currentTimeMillis() - startMS;
            if (elapse > 500) {
                logger.debug("time costs {} ms, {}\n", elapse, positionKey);
            }
            latencyStat.update(elapse);
        }
    }
    private void runUnsafe() {
        int pos = configDao.getIntConfig(positionKey, 1);
        int batchSize = configDao.getIntConfig(batchSizeKey, DEFAULT_CLEAN_BATCH_SIZE);
        int gapDay = configDao.getIntConfig(timeGapKey, 180);
        ChronoUnit unit = ChronoUnit.DAYS;
        long newPos = doWork(pos, batchSize, getTimeLimit(gapDay, unit));
        savePos(pos, newPos);
    }

    protected List<Long> buildUserIds() {
        // entry count should be very small, less than 10, so list/set is ok.
        return Lists.transform(getUserAddresses(),
                addr->dexDao.getUserByName(addr).get().map(User::getId).orElse(0L));
    }

    protected List<String> getUserAddresses() {
        return Collections.emptyList();
    }

    private Date getTimeLimit(int diff, ChronoUnit unit) {
        Instant instant = Instant.now().plus(-diff, unit);
        return Date.from(instant);
    }

    private void savePos(int oldPos, long newPos) {
        if (newPos > oldPos) {
            configDao.setConfig(positionKey, String.valueOf(newPos));
            logger.debug("save position {}, cleaner {}", newPos, positionKey);
        }
    }

    protected long doWork(int pos, int batchSize, Date timeLimit) {
        return 0;
    }

}
