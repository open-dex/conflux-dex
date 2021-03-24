package conflux.dex.worker.batch;

import java.util.List;

import conflux.dex.service.FeeService;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import com.codahale.metrics.Histogram;

import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.dao.DexDao;
import conflux.dex.matching.Log;
import conflux.dex.worker.ticker.Ticker;

public class BatchTradeSettlement implements Handler<List<Log>> {
	
	private static Histogram statTotal = Metrics.histogram(BatchTradeSettlement.class, "latency", "total");
	private static Histogram statTrades = Metrics.histogram(BatchTradeSettlement.class, "latency", "trades");
	private static Histogram statNonTrades = Metrics.histogram(BatchTradeSettlement.class, "latency", "nonTrades");
	
	private DexDao dao;
	private Ticker ticker;
	FeeService feeService;
	
	public BatchTradeSettlement(DexDao dao, Ticker ticker, FeeService feeService) {
		this.dao = dao;
		this.ticker = ticker;
		this.feeService = feeService;
	}

	@Override
	public void handle(List<Log> data) {
		SettlementAggregator aggregator = SettlementAggregator.aggregate(data, feeService);
		SettlementHelper helper = new SettlementHelper(this.dao);
		
		long start = System.currentTimeMillis();
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				long startTime = System.currentTimeMillis();
				aggregator.persist(status, dao, ticker);
				statTrades.update(System.currentTimeMillis() - startTime);
				
				startTime = System.currentTimeMillis();
				helper.persist(status, data);
				statNonTrades.update(System.currentTimeMillis() - startTime);
			}
			
		});
		statTotal.update(System.currentTimeMillis() - start);
		
		aggregator.fires();
		helper.fires();
	}

}
