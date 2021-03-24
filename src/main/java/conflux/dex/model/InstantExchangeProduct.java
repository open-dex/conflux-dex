package conflux.dex.model;

public class InstantExchangeProduct extends Product {
	/**
	 * product id of base currency instant exchange.
	 */
	private int baseProductId;
	/**
	 * if base currency in baseProductId is base.
	 */
	private boolean baseIsBaseSide;
	/**
	 * product id of quote currency instant exchange.
	 */
	private int quoteProductId;
	/**
	 * if quote currency in baseProductId is base.
	 */
	private boolean quoteIsBaseSide;
	
	public int getBaseProductId() {
		return baseProductId;
	}

	public void setBaseProductId(int baseProductId) {
		this.baseProductId = baseProductId;
	}

	public boolean isBaseIsBaseSide() {
		return baseIsBaseSide;
	}

	public void setBaseIsBaseSide(boolean baseIsBaseSide) {
		this.baseIsBaseSide = baseIsBaseSide;
	}

	public int getQuoteProductId() {
		return quoteProductId;
	}

	public void setQuoteProductId(int quoteProductId) {
		this.quoteProductId = quoteProductId;
	}

	public boolean isQuoteIsBaseSide() {
		return quoteIsBaseSide;
	}

	public void setQuoteIsBaseSide(boolean quoteIsBaseSide) {
		this.quoteIsBaseSide = quoteIsBaseSide;
	}
}
