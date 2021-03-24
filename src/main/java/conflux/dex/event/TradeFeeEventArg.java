package conflux.dex.event;

import java.math.BigDecimal;

public class TradeFeeEventArg {
	
	public String product;
	public BigDecimal baseAssetFee;
	public BigDecimal quoteAssetFee;
	
	public TradeFeeEventArg(String product, BigDecimal baseAssetFee, BigDecimal quoteAssetFee) {
		this.product = product;
		this.baseAssetFee = baseAssetFee;
		this.quoteAssetFee = quoteAssetFee;
	}

}
