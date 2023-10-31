package conflux.dex.tool;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;
import conflux.dex.model.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.client.RestTemplate;

import conflux.dex.blockchain.TypedWithdraw;
import conflux.dex.blockchain.TypedWithdrawCrossChain;
import conflux.dex.blockchain.crypto.TypedData;
import conflux.dex.common.BusinessFault;
import conflux.dex.controller.Response;
import conflux.dex.controller.request.AddCurrencyRequest;
import conflux.dex.controller.request.AddProductRequest;
import conflux.dex.controller.request.AdminCancelOrdersRequest;
import conflux.dex.controller.request.ChangeProductOpenStatusRequest;
import conflux.dex.controller.request.DailyLimitRateRequest;
import conflux.dex.controller.request.DailyLimitRequest;
import conflux.dex.controller.request.SystemCommand;
import conflux.dex.controller.request.WithdrawRequest;
import conflux.dex.service.PlaceOrderRequest;
import conflux.web3j.AccountManager;

import static conflux.dex.common.BusinessFault.SystemSuspended;

public class Client {
	
	private String url;
	private RestTemplate rest = new RestTemplate();
	
	public Client(String url) {
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		
		this.url = url;
	}
	
	public String getBoomflowAddress() {
		String url = String.format("%s/common/boomflow", this.url);
		Response response = this.rest.getForObject(url, Response.class);
		return response.getEntity(String.class).get();
	}
	
	public long getChainId() {
		String url = String.format("%s/common/chainid", this.url);
		Response response = this.rest.getForObject(url, Response.class);
		return response.getEntity(Long.class).get();
	}
	
	public Optional<Currency> getCurrency(String name) {
		String url = String.format("%s/currencies/%s", this.url, name);
		System.out.println("getCurrency from "+url);
		Response response = this.rest.getForObject(url, Response.class);
		return response.getEntity(Currency.class, BusinessFault.CurrencyNotFound);
	}
	
	public int addCurrency(AddCurrencyRequest request) {
		String url = String.format("%s/currencies", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		return response.getEntity(Currency.class).get().getId();
	}
	
	public Optional<Product> getProduct(String name) {
		String url = String.format("%s/products/%s", this.url, name);
		Response response = this.rest.getForObject(url, Response.class);
		return response.getEntity(Product.class, BusinessFault.ProductNotFound);
	}
	
	public int addProduct(AddProductRequest request) {
		String url = String.format("%s/products", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		return response.getEntity(Product.class).get().getId();
	}
	
	public void addDailyLimitRate(DailyLimitRateRequest request) {
		String url = String.format("%s/products/dailyLimitRate", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		response.ensureSucceeded();
	}
	
	public void addDailyLimit(DailyLimitRequest request) {
		String url = String.format("%s/products/dailyLimit", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		response.ensureSucceeded();
	}
	
	public void changeProductOpenStatus(ChangeProductOpenStatusRequest request) {
		String url = String.format("%s/products/dailyLimit/changeStatus", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		response.ensureSucceeded();
	}
	
	public Optional<User> getUser(String name) {
		String url = String.format("%s/users/%s", this.url, name);
		Response response = this.rest.getForObject(url, Response.class);
		return response.getEntity(User.class, BusinessFault.UserNotFound);
	}
	
	public Optional<Account> getAccount(String user, String currency) {
		String url = String.format("%s/accounts/%s/%s", this.url, user, currency);
		Response response = this.rest.getForObject(url, Response.class);
		return response.getEntity(Account.class, BusinessFault.UserNotFound, BusinessFault.AccountNotFound);
	}
	
	public long placeOrder(PlaceOrderRequest request) {
		String url = String.format("%s/orders/place", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		if (Objects.equals(response.getData(), SystemSuspended.getCode())) {
			System.out.println("place order :" + response.getMessage());
			return 0;
		}
		return response.getValue(Long.class);
	}

	public long placeOrderTest(PlaceOrderRequest request) {
		String url = String.format("%s/orders/place/test", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		if (Objects.equals(response.getData(), SystemSuspended.getCode())) {
			System.out.println("place test order :" + response.getMessage());
			return 0;
		}
		EIP712Data value = response.getValue(EIP712Data.class);
		System.out.println("place test order :" + Utils.toJson(value));
		return 0;
	}
	
	public void suspend(SystemCommand command) {
		String url = String.format("%s/system/suspend", this.url);
		Response response = this.rest.postForObject(url, command, Response.class);
		response.ensureSucceeded();
	}
	
	public void resume(SystemCommand command) {
		String url = String.format("%s/system/resume", this.url);
		Response response = this.rest.postForObject(url, command, Response.class);
		response.ensureSucceeded();
	}
	
	public void cancelOrders(AdminCancelOrdersRequest request) {
		String url = String.format("%s/system/orders/cancel", this.url);
		Response response = this.rest.postForObject(url, request, Response.class);
		response.ensureSucceeded();
	}

	public void cancelOrder(long id, long timestamp, String sign) {
		String url = String.format("%s/orders/%s/cancel", this.url, id);
		CancelOrderRequest request = CancelOrderRequest.fromUser(id, timestamp, sign);
		Response response = this.rest.postForObject(url, request, Response.class);
		System.out.println("cancel order result : "+response);
	}

	public Order getOrder(long id) {
		String url = String.format("%s/orders/%s", this.url, id);

		Response response = this.rest.getForObject(url, Response.class);
		ObjectMapper mapper = buildOrderMapper();

		return mapper.convertValue(response.getData(), Order.class);
	}

	@NotNull
	private static ObjectMapper buildOrderMapper() {
		ObjectMapper mapper = new ObjectMapper();

		SimpleBeanPropertyFilter theFilter = SimpleBeanPropertyFilter
				.serializeAllExcept("holdAmount");
		FilterProvider filter = new SimpleFilterProvider()
				.addFilter("myFilter", theFilter);

		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return mapper;
	}

	public List<Order> getOrders(String addr) {
		String url = String.format("%s/orders/incompleted?address=%s", this.url, addr);

		Response response = this.rest.getForObject(url, Response.class);
		ObjectMapper mapper = buildOrderMapper();
		if (!response.isSuccess()) {
			System.out.println(url + " failed " + response);
			return Collections.emptyList();
		}
//		System.out.println(url + " order data:"+response.getData());
		return mapper.convertValue(response.getData(), new TypeReference<List<Order>>(){});
	}
	
	public void withdraw(WithdrawRequest requestWithoutSignature, AccountManager am, String... password) throws Exception {
		Optional<Currency> currency = this.getCurrency(requestWithoutSignature.currency);
		if (!currency.isPresent()) {
			throw BusinessFault.CurrencyNotFound.rise();
		}
		
		WithdrawRecord record = requestWithoutSignature.toRecord();
		TypedData message = record.isCrossChain()
				? TypedWithdrawCrossChain.create(record, currency.get())
				: TypedWithdraw.create(record, currency.get());
		requestWithoutSignature.signature = am.signMessage(message.hash(), false, AddressTool.address(requestWithoutSignature.userAddress), password);
		
		String url = String.format("%s/accounts/withdraw", this.url);
		Response response = this.rest.postForObject(url, requestWithoutSignature, Response.class);
		response.ensureSucceeded();
	}

}
