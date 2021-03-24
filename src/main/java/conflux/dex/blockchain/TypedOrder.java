package conflux.dex.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.blockchain.crypto.Entry;
import conflux.dex.blockchain.crypto.TypedData;
import conflux.dex.common.Utils;
import conflux.dex.model.Currency;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderType;

public class TypedOrder extends StaticStruct implements TypedData {
	
	public static final String PRIMARY_TYPE = "Order";
	
	public static final List<Entry> SCHEMA = Arrays.asList(
			new Entry("userAddress", "address"),
			new Entry("amount", "uint256"),
			new Entry("price", "uint256"),
			new Entry("orderType", "uint256"),
			new Entry("side", "bool"),
			new Entry("salt", "uint256"),
			new Entry("baseAssetAddress", "address"),
			new Entry("quoteAssetAddress", "address"),
			new Entry("feeAddress", "address"),
			new Entry("makerFeePercentage", "uint256"),
			new Entry("takerFeePercentage", "uint256"));
	
	private static final Map<String, List<Entry>> SCHEMAS = Collections.singletonMap(PRIMARY_TYPE, SCHEMA);
	
	public static final long TYPE_LIMIT = 0;
	public static final long TYPE_MARKET = 1;
	
	public String userAddress;
	public BigInteger amount;
	public BigInteger price;
	public long orderType;
	public boolean side;
	public long salt;
	public String baseAssetAddress;
	public String quoteAssetAddress;
	public String feeAddress;
	public BigInteger makerFeePercentage;
	public BigInteger takerFeePercentage;
	
	public TypedOrder(String userAddress, BigInteger amount, BigInteger price, OrderType type, OrderSide side, long nonce,
			String baseAssetAddress, String quoteAssetAddress, String feeAddress, BigInteger makerFeePercentage, BigInteger takerFeePercentage) {
		super(new Address(userAddress),
				new Uint256(amount),
				new Uint256(price),
				new Uint256(type == OrderType.Limit ? TYPE_LIMIT : TYPE_MARKET),
				new Bool(side == OrderSide.Buy),
				new Uint256(nonce),
				new Address(baseAssetAddress),
				new Address(quoteAssetAddress),
				new Address(feeAddress),
				new Uint256(makerFeePercentage),
				new Uint256(takerFeePercentage));
		
		this.userAddress = userAddress;
		this.amount = amount;
		this.price = price;
		this.orderType = type == OrderType.Limit ? TYPE_LIMIT : TYPE_MARKET;
		this.side = side == OrderSide.Buy;
		this.salt = nonce;
		this.baseAssetAddress = baseAssetAddress;
		this.quoteAssetAddress = quoteAssetAddress;
		this.feeAddress = feeAddress;
		this.makerFeePercentage = makerFeePercentage;
		this.takerFeePercentage = takerFeePercentage;
	}
	
	public static TypedOrder from(Order order, String user, Currency baseCurrency, Currency quoteCurrency) {
		return new TypedOrder(
				user,
				order.isMarketBuy() ? quoteCurrency.toIntegerFormat(order.getAmount()) : baseCurrency.toIntegerFormat(order.getAmount()),
				order.getType() == OrderType.Limit ? quoteCurrency.toIntegerFormat(order.getPrice()) : BigInteger.ZERO,
				order.getType(),
				order.getSide(),
				order.getTimestamp(),
				baseCurrency.getContractAddress(),
				quoteCurrency.getContractAddress(),
				order.getFeeAddress(),
				Utils.toContractValue(BigDecimal.valueOf(order.getFeeRateMaker())),
				Utils.toContractValue(BigDecimal.valueOf(order.getFeeRateTaker())));
	}
	
	@Override
	public String primaryType() {
		return PRIMARY_TYPE;
	}

	@Override
	public Map<String, List<Entry>> schemas() {
		return SCHEMAS;
	}
	
	@Override
	public Domain domain() {
		return Domain.boomflow();
	}
	
	@Override
	public List<Object> toArray() {
		return Arrays.asList(
				this.userAddress,
				this.amount,
				this.price,
				this.orderType,
				this.side,
				this.salt,
				this.baseAssetAddress,
				this.quoteAssetAddress,
				this.feeAddress,
				this.makerFeePercentage,
				this.takerFeePercentage);
	}

}