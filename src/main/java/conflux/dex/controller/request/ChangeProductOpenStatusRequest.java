package conflux.dex.controller.request;

import java.util.Arrays;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class ChangeProductOpenStatusRequest extends AdminRequest {
	/**
	 * Product name.
	 */
	public String product;
	/**
	 * Open(true) or close(false) trading for corresponding product.
	 */
	public boolean open;
	
	public ChangeProductOpenStatusRequest() {
	}
	
	public ChangeProductOpenStatusRequest(String product, boolean open) {
		this.product = product;
		this.open = open;
	}
	
	@Override
	protected void validate() {
	}
	
	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(this.product),
				RlpString.create(String.valueOf(this.open)));
	}
}
