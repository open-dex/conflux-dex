package conflux.dex.service;

import conflux.dex.model.Order;

import java.util.Date;

public class DeleteOrderWithoutTradeCleaner extends AbstractCleaner {
    public DeleteOrderWithoutTradeCleaner(String positionKey, String timeGapKey, String batchSizeKey) {
        super(positionKey, timeGapKey, batchSizeKey);
    }

    @Override
    protected long doWork(int pos, int batchSize, Date timeLimit) {
        int upperBound = pos + batchSize;
        int delCnt = archiveDao.deleteZeroFilledOrder(pos, upperBound, timeLimit);
        Order orderReachTimeLimit = archiveDao.findOrderReachTimeLimit(pos, upperBound, timeLimit);
        if (delCnt > 0) {
            logger.debug("cnt {}, cleaner {}", delCnt, positionKey);
        }
        if (orderReachTimeLimit == null) {
            return upperBound;
        }
        return orderReachTimeLimit.getId();
    }
}
