package conflux.dex.controller.request;

import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.dao.DexDao;
import conflux.dex.model.InstantExchangeProduct;

public class AddInstantExchangeProductRequest extends AdminRequest {
	/**
	 * New product to create.
	 */
	public InstantExchangeProduct product;
	
	public AddInstantExchangeProductRequest() {
	}
	
	public AddInstantExchangeProductRequest(InstantExchangeProduct product) {
		this.product = product;
	}
	
	@Override
	protected void validate() {
		// do nothing
	}
	
	public void validate(DexDao dao) {
		AddProductRequest.validate(this.product, dao);
	}
	
	@Override
	protected List<RlpType> getEncodeValues() {
		List<RlpType> list = AddProductRequest.encode(this.product);
		
		list.add(RlpString.create(String.valueOf(this.product.getBaseProductId())));
		list.add(RlpString.create(this.product.isBaseIsBaseSide() ? "1" : "0"));
		list.add(RlpString.create(String.valueOf(this.product.getQuoteProductId())));
		list.add(RlpString.create(this.product.isQuoteIsBaseSide() ? "1" : "0"));
		
		return list;
	}
}
