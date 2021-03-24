package conflux.dex.controller.request;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.dao.DexDao;
import conflux.dex.model.Product;

public class UpdateProductRequest extends AdminRequest {
	
	/**
	 * Product name to update.
	 */
	public String product;
	/**
	 * Quote currency precision when quote price (decimal places).
	 */
	public int pricePrecision;
	/**
	 * Base currency precision when quote amount (decimal places).
	 */
	public int amountPrecision;
	/**
	 * Quote currency precision when quote funds for market-buy orders (funds = price * amount).
	 */
	public int fundsPrecision;
	/**
	 * Minimum order amount for limit orders or market-sell orders.
	 */
	public BigDecimal minOrderAmount;
	/**
	 * Maximum order amount for limit orders or market-sell orders.
	 */
	public BigDecimal maxOrderAmount;
	/**
	 * Minimum order funds. (For limit orders, funds = order.price * order.amount; for market-buy orders, funds = order.amount)
	 */
	public BigDecimal minOrderFunds;

	@Override
	protected void validate() {
		// do nothing
	}
	
	public Product validate(DexDao dao) {
		Product oldProduct = dao.getProductByName(this.product).mustGet();
		Product newProduct = new Product();
		BeanUtils.copyProperties(oldProduct, newProduct);
		
		newProduct.setPricePrecision(this.pricePrecision);
		newProduct.setAmountPrecision(this.amountPrecision);
		newProduct.setFundsPrecision(this.fundsPrecision);
		newProduct.setMinOrderAmount(this.minOrderAmount);
		newProduct.setMaxOrderAmount(this.maxOrderAmount);
		newProduct.setMinOrderFunds(this.minOrderFunds);
		
		AddProductRequest.validate(newProduct, dao);

		return newProduct;
	}

	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(this.product),
				RlpString.create(String.valueOf(this.pricePrecision)),
				RlpString.create(String.valueOf(this.amountPrecision)),
				RlpString.create(String.valueOf(this.fundsPrecision)),
				RlpString.create(this.minOrderAmount.toPlainString()),
				RlpString.create(this.maxOrderAmount.toPlainString()),
				RlpString.create(this.minOrderFunds.toPlainString()));
	}

}
