package conflux.dex.controller.request;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Validators;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.Product;

public class AddProductRequest extends AdminRequest {
	/**
	 * New product to create.
	 */
	public Product product;
	
	public AddProductRequest() {
	}
	
	public AddProductRequest(Product product) {
		this.product = product;
	}
	
	@Override
	protected void validate() {
		// do nothing
	}
	
	public void validate(DexDao dao) {
		validate(this.product, dao);
	}
	
	static void validate(Product product, DexDao dao) {
		Validators.validateName(product.getName(), Product.MAX_LEN, "product name");
		
		if (product.getMinOrderAmount() == null) {
			throw BusinessException.validateFailed("minOrderAmount not specified");
		}
		if (product.getMinOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw BusinessException.validateFailed("minOrderAmount should be greater than 0");
		}
		if (product.getMinOrderAmount().scale() > product.getAmountPrecision()) {
			throw BusinessException.validateFailed("minOrderAmount precision mismatch with amount precision");
		}
		
		if (product.getMaxOrderAmount() == null) {
			throw BusinessException.validateFailed("maxOrderAmount not specified");
		}
		if (product.getMaxOrderAmount().compareTo(product.getMinOrderAmount()) < 0) {
			throw BusinessException.validateFailed("maxOrderAmount is less than minOrderAmount");
		}
		if (product.getMaxOrderAmount().scale() > product.getAmountPrecision()) {
			throw BusinessException.validateFailed("maxOrderAmount precision mismatch with amount precision");
		}
		
		if (product.getMinOrderFunds() == null) {
			throw BusinessException.validateFailed("minOrderFunds not specified");
		}
		if (product.getMinOrderFunds().compareTo(BigDecimal.ZERO) <= 0) {
			throw BusinessException.validateFailed("minOrderFunds should be greater than 0");
		}
		if (product.getMinOrderFunds().scale() > product.getFundsPrecision()) {
			throw BusinessException.validateFailed("minOrderFunds precision mismatch with funds precision");
		}
		
		Currency baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		
		if (product.getAmountPrecision() > baseCurrency.getDecimalDigits()) {
			throw BusinessException.validateFailed("amount precision exceeds %d", baseCurrency.getDecimalDigits());
		}
		
		Currency quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		
		if (product.getPricePrecision() > quoteCurrency.getDecimalDigits()) {
			throw BusinessException.validateFailed("price precision exceeds %d", quoteCurrency.getDecimalDigits());
		}
		
		if (product.getFundsPrecision() > quoteCurrency.getDecimalDigits()) {
			throw BusinessException.validateFailed("funds precision exceeds %d", quoteCurrency.getDecimalDigits());
		}
		
		if (product.getPricePrecision() + product.getAmountPrecision() > quoteCurrency.getDecimalDigits()) {
			throw BusinessException.validateFailed("precision of price plus the precision of amount exceeds %d", quoteCurrency.getDecimalDigits());
		}
	}
	
	@Override
	protected List<RlpType> getEncodeValues() {
		return encode(this.product);
	}
	
	static List<RlpType> encode(Product product) {
		List<RlpType> list = new LinkedList<RlpType>();
		
		list.add(RlpString.create(product.getName()));
		list.add(RlpString.create(String.valueOf(product.getBaseCurrencyId())));
		list.add(RlpString.create(String.valueOf(product.getQuoteCurrencyId())));
		list.add(RlpString.create(String.valueOf(product.getPricePrecision())));
		list.add(RlpString.create(String.valueOf(product.getAmountPrecision())));
		list.add(RlpString.create(String.valueOf(product.getFundsPrecision())));
		list.add(RlpString.create(product.getMinOrderAmount().toPlainString()));
		list.add(RlpString.create(product.getMaxOrderAmount().toPlainString()));
		list.add(RlpString.create(product.getMinOrderFunds().toPlainString()));
		
		return list;
	}
}
