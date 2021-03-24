package conflux.dex.controller.request;

import java.util.Arrays;
import java.util.List;

import org.springframework.util.StringUtils;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.Validators;
import conflux.dex.model.Product;
import conflux.web3j.types.AddressType;

public class AdminCancelOrdersRequest extends AdminRequest {
	/**
	 * User address to cancel orders, empty for all users.
	 */
	public String userAddress;
	/**
	 * Product name to cancel orders, empty for all products.
	 */
	public String product;
	
	public static AdminCancelOrdersRequest ofUser(String userAddress) {
		return create(userAddress, null);
	}
	
	public static AdminCancelOrdersRequest ofProduct(String product) {
		return create(null, product);
	}
	
	public static AdminCancelOrdersRequest create(String userAddress, String product) {
		AdminCancelOrdersRequest request = new AdminCancelOrdersRequest();
		request.userAddress = userAddress;
		request.product = product;
		return request;
	}

	@Override
	protected void validate() {
		if (!StringUtils.isEmpty(this.userAddress)) {
			Validators.validateAddress(this.userAddress, AddressType.User, "address");
		}
		
		if (!StringUtils.isEmpty(this.product)) {
			Validators.validateName(this.product, Product.MAX_LEN, "product name");
		}
	}

	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(this.userAddress == null ? "" : this.userAddress),
				RlpString.create(this.product == null ? "" : this.product));
	}

}
