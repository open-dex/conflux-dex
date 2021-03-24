package conflux.dex.service.statistics;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.influxdb.dto.Point.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.Metrics.ReportableGauge;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Trade;
import conflux.dex.worker.TradeDetails;

@Component
public class TodayTradeFeeStat extends TodayDataStat<TradeDetails, Map<String, TodayTradeFeeStat.FeeItem>> implements ReportableGauge<Map<String, TodayTradeFeeStat.FeeItem>> {
	
	public static class FeeItem {
		public BigDecimal baseAssetFee;
		public BigDecimal quoteAssetFee;
		
		public FeeItem() {
			this.baseAssetFee = BigDecimal.ZERO;
			this.quoteAssetFee = BigDecimal.ZERO;
		}
		
		public FeeItem add(BigDecimal baseAssetFee, BigDecimal quoteAssetFee) {
			FeeItem item = new FeeItem();
			item.baseAssetFee = this.baseAssetFee.add(baseAssetFee);
			item.quoteAssetFee = this.quoteAssetFee.add(quoteAssetFee);
			return item;
		}
	}
	
	private static ConcurrentMap<String, String> baseFeeKeys = new ConcurrentHashMap<String, String>();
	private static ConcurrentMap<String, String> quoteFeeKeys = new ConcurrentHashMap<String, String>();
	
	private DexDao dao;
	private ConcurrentMap<String, FeeItem> product2Fees = new ConcurrentHashMap<String, FeeItem>();

	@Autowired
	public TodayTradeFeeStat(DexDao dao) {
		super(Events.ORDER_MATCHED);
		
		this.dao = dao;
	}

	// Each product settle trade in sequence, so need not to lock.
	@Override
	protected void update(TradeDetails data) {
		Trade trade = data.getTrade();
		String product = this.dao.getProduct(trade.getProductId()).mustGet().getName();
		FeeItem current = this.product2Fees.computeIfAbsent(product, n -> new FeeItem());
		
		if (trade.getSide() == OrderSide.Buy) {
			this.product2Fees.put(product, current.add(trade.getTakerFee(), trade.getMakerFee()));
		} else {
			this.product2Fees.put(product, current.add(trade.getMakerFee(), trade.getTakerFee()));
		}
	}

	@Override
	protected void reset() {
		this.product2Fees.clear();
	}

	@Override
	protected Map<String, FeeItem> get() {
		return new HashMap<String, FeeItem>(this.product2Fees);
	}

	@Override
	public Builder buildInfluxDBPoint(Builder builder, Map<String, FeeItem> value) {
		for (Map.Entry<String, FeeItem> entry : value.entrySet()) {
			FeeItem item = entry.getValue();
			
			String baseKey = baseFeeKeys.computeIfAbsent(entry.getKey(), product -> String.format("%s.base", product));
			builder.addField(baseKey, item.baseAssetFee.doubleValue());
			
			String quoteKey = quoteFeeKeys.computeIfAbsent(entry.getKey(), product -> String.format("%s.quote", product));
			builder.addField(quoteKey, item.quoteAssetFee.doubleValue());
		}
		
		return builder;
	}

}
