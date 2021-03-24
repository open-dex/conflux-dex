package conflux.dex.service;

import conflux.dex.config.PruneConfig;
import conflux.dex.dao.ConfigDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Clean useless history data in database.
 * This task repeatedly run with fixed delays.
 * For each round, it queries order from DB with the primary key `id` in a range (start from `pos`),
 * these orders are checked by some conditions(status, creation time ...) ,
 * then the matched orders are deleted by id(s) (in batch way),
 * and the newest `pos` is saved into DB for next round using.
 *
 * Consider that it may need to delete some other related data (nothing at this time in fact),
 * it queries data and then do the deletion.
 * Maybe it can do deletion directly by one sql with conditions.
 */
@Component
public class CleanerService {
    private final PruneConfig pruneConfig;
    private DeleteOrderWithoutTradeCleaner orderCleanerForAllUser;
    private TradeAndOrderCleaner tradeCleanerForAllUser;
    private TradeAndOrderCleaner tradeCleanerForMarketMaker;
    private TradeAndOrderCleaner tradeCleanerForKLineRobot;
    private boolean initialized = false;

    public CleanerService(@Autowired PruneConfig pruneConfig) {
        this.pruneConfig = pruneConfig;
    }

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // Since different time limitations and filtering rules are taken,
        // the working position(id) may be different also,
        // thus, query only once can't match the demands.
        // clean 3 times for different users.

        // general, for all users.
        orderCleanerForAllUser = new DeleteOrderWithoutTradeCleaner(
                ConfigDao.KEY_LAST_CLEAN_ORDER_ID,
                ConfigDao.KEY_CLEAN_DAYS_GAP,
                ConfigDao.KEY_CLEAN_BATCH_SIZE
        );
        tradeCleanerForAllUser = new TradeAndOrderCleaner(
                ConfigDao.KEY_LAST_CLEAN_TRAD_ID,
                ConfigDao.KEY_CLEAN_DAYS_GAP,
                ConfigDao.KEY_CLEAN_BATCH_SIZE
        );
        // market maker
        tradeCleanerForMarketMaker = new TradeAndOrderCleaner(
                ConfigDao.KEY_LAST_CLEAN_TRADE_ID_MARKET_MAKER,
                ConfigDao.KEY_CLEAN_DAYS_GAP_MARKET_MAKER,
                ConfigDao.KEY_CLEAN_BATCH_SIZE,
                pruneConfig.getMarketmaker()
        );
        // robot
        tradeCleanerForKLineRobot = new TradeAndOrderCleaner(
                ConfigDao.KEY_LAST_CLEAN_TRADE_ID_ROBOT,
                ConfigDao.KEY_CLEAN_DAYS_GAP_ROBOT,
                ConfigDao.KEY_CLEAN_BATCH_SIZE,
                pruneConfig.getKLineRobots()
        );
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 5000)
    public void clean() {
        init();
        orderCleanerForAllUser.run();
        tradeCleanerForAllUser.run();
        tradeCleanerForMarketMaker.run();
        tradeCleanerForKLineRobot.run();
    }

}
