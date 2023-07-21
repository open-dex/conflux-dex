package conflux.dex.tool;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import conflux.dex.controller.AddressTool;
import conflux.web3j.CfxUnit;
import conflux.web3j.types.CfxAddress;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import conflux.dex.blockchain.CfxBuilder;
import conflux.dex.blockchain.TypedOrder;
import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.common.Utils;
import conflux.dex.model.Account;
import conflux.dex.model.Currency;
import conflux.dex.model.Product;
import conflux.dex.model.User;
import conflux.dex.service.PlaceOrderRequest;
import conflux.web3j.Account.Option;
import conflux.web3j.AccountManager;
import conflux.web3j.Cfx;
import conflux.web3j.contract.ContractCall;
import conflux.web3j.contract.ERC777;
import conflux.web3j.contract.abi.DecodeUtil;
import conflux.web3j.response.Receipt;
import conflux.web3j.types.RawTransaction;

/**
 * Test order. btc and usdt here are not real BTC/USDT, they are just variable names.
 * Please read bot-order.properties for the real value.
 * How it works:
 * This bot will automatically read account from specified keystore. If the keystore is empty,
 * Bot will generate new ones.
 * When running for the first time, developer should copy one of the generated account(hex)
 * and paste it to [user.erc777.address].
 * [user.erc777.address] should hold both base and quote token,
 * developer could use 'test/scripts/mint.sh [user.erc777.address(hex)]' to mint.
 * If everything is ok, Bot should place orders successfully.
 *
 * Properties in bot-order.properties.
 * dex.url: the dex backend url, it's localhost:8080 by default.
 * dex.cfx.url: conflux blockchain rpc url.
 * user.erc777.address: the address which hold tokens. hex format.
 * user.keystore: where the account should be saved, it's a directory.
 * user.keyfile.password: keystore password, set it with any string you want at the first time.
 * user.trader.predefined: predefined traders, hex format, separated by ','. leave it blank at the first time.
 */
@ComponentScan
@PropertySource("bot-order.properties")
public class OrderBot {
	
	private static final String FEE_ADDRESS = "0x1111111111111111111111111111111111111111";
	
	@Autowired
	private Context context;
	@Autowired
	private ProductFactory productFactory;
	@Autowired
	private Traders traders;
	@Autowired
	private PriceRandom priceRandom;
	
	@Value("${trade.number}")
	private long numTrades;
	@Value("${trade.interval.ms}")
	private long intervalMillis;
	@Value("${trade.amount.baseline}")
	private double amountBaseline;
	@Value("${trade.amount.multiple.max}")
	private int amountMultipleMax;
	private OrderMode orderMod = OrderMode.Both;
	enum OrderMode{
		OnlyBuy,
		OnlySell,
		Both
	}
	
	public static void main(String[] args) throws Exception {
		configLog();
		
		@SuppressWarnings("resource")
		ApplicationContext context = new AnnotationConfigApplicationContext(
				OrderBot.class, Context.class, ProductFactory.class, Traders.class, PriceRandom.class);
		OrderBot bot = context.getBean(OrderBot.class);
		bot.setUp();
//		bot.orderMod = OrderMode.OnlyBuy;
		bot.run();
//		deposit(context);
		System.out.println("\nDone.");
    }

	private static void balanceOf(OrderBot bot, String addr, String contract) {
		BigInteger b = bot.crclBalanceOf(contract,addr);
		System.out.printf("address %s, balance %s, contract %s%n", addr, b, contract);
	}

	void placeOrderDebug(BigDecimal price) throws Exception{
		User seller = new User("0x117fbb2a50697e2883395ff6f699818085c8abbe");
		BigDecimal amount = BigDecimal.valueOf(0.001);
//		BigDecimal t = price;
//		price = amount;
//		amount = t;
		PlaceOrderRequest o2 = PlaceOrderRequest.limitSell(seller.getName(), this.productFactory.product.getName(), price, amount);
		o2.setSignature(this.sign(o2, seller));
		long id = this.context.dexClient.placeOrder(o2);
		System.out.println("order id "+id);
	}

    /**
    Deposit currencies to account.
     */
	public static void deposit(ApplicationContext context) throws Exception {
		Traders traders = context.getBean(Traders.class);
		Context ctx = context.getBean(Context.class);
		ProductFactory productFactory = context.getBean(ProductFactory.class);
		conflux.web3j.Account erc777Account = conflux.web3j.Account.unlock(ctx.cfx.get(), ctx.am, traders.erc777User, ctx.password);
		traders.depositCurrency(ctx, productFactory.btc, erc777Account, 0);
		traders.depositCurrency(ctx, productFactory.usdt, erc777Account, 0);
	}

	private static void configLog() throws JoranException, IOException {
		JoranConfigurator jc = new JoranConfigurator();			
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		jc.setContext(lc);
		jc.doConfigure(new ClassPathResource("logback-bot.xml").getInputStream());
	}
	
	public void run() throws Exception {
		setUp();

		if (this.context.cfx.isPresent()) {
			this.debugBalance();
		}

		do {
			placeOrder();
			System.out.println("\n\nPress q to quit, [Enter] to run again:");
			int in = System.in.read();
			if (in == 'q') {
				break;
			}
		} while(true);
	}

	public void setUp() {
		Domain.boomflowAddress = this.context.dexClient.getBoomflowAddress();
		Domain.defaultChainId = this.context.dexClient.getChainId();
		Domain.boomflow();
	}

	private void placeOrder() throws InterruptedException {
		Random rand = new Random();

		for (int i = 0; i < this.numTrades; i++) {
			try {
				this.placeOrder(rand);
			} catch (Exception e) {
				System.err.println("failed to place order: " + e.getMessage());
				e.printStackTrace(System.err);
				break;
			}
			System.out.print(String.format("%5d", (i+1)));
			if (i % 20 == 19) {
				System.out.println();
			}
			Thread.sleep(this.intervalMillis);
		}
	}
	
	private void debugBalance() {
		System.out.printf("Balance (%s/%s) statistics:\n", this.productFactory.btc.getName(), this.productFactory.usdt.getName());
		
		for (User user : this.traders.getUsers()) {
			this.debugBalance(user.getName());
		}
		
		this.debugBalance(FEE_ADDRESS);
	}
	
	private void debugBalance(String address) {
		Currency btc = this.productFactory.btc;
		Currency usdt = this.productFactory.usdt;
		Client dexClient = this.context.dexClient;
		
		System.out.println("\nAccount:" + address);
		printBalance(dexClient, btc, address);
		printBalance(dexClient, usdt, address);
	}

	private void printBalance(Client dexClient, Currency c, String address) {
		String crcl = c.getContractAddress();
		System.out.printf("\t\tCRCL.%4s : %12s \tcrcl :%s\n", c.getName(), CfxUnit.drip2Cfx(this.crclBalanceOf(crcl, address)), crcl);
		Optional<Account> optAccount = dexClient.getAccount(address, c.getName());
		String strAccount = optAccount.isPresent() ? optAccount.get().getAvailable().toString() : "not found";
		System.out.printf("\t\tDEX .%4s : %12s \ttoken:%s\n", c.getName(), strAccount, c.getTokenAddress());
	}
	
	private BigInteger crclBalanceOf(String crcl, String account) {
		ContractCall call = new ContractCall(this.context.cfx.get(), AddressTool.address(crcl));
		String encodedResult = call.call("balanceOf", new Address(account)).sendAndGet();
		return DecodeUtil.decode(encodedResult, Uint256.class);
	}
	
	private void placeOrder(Random rand) throws Exception {
		User buyer = this.traders.random(rand);
		User seller = this.traders.random(rand);
		
		BigDecimal price = Utils.mul(
				BigDecimal.valueOf(this.priceRandom.next()),
				BigDecimal.valueOf(0.1d),
				this.productFactory.product.getPricePrecision());
		BigDecimal amount = Utils.mul(
				BigDecimal.valueOf(this.amountBaseline),
				BigDecimal.valueOf(rand.nextInt(this.amountMultipleMax) + 1),
				this.productFactory.product.getAmountPrecision());
		if (orderMod.equals(OrderMode.Both) || orderMod.equals(OrderMode.OnlyBuy)) {
			PlaceOrderRequest o1 = PlaceOrderRequest.limitBuy(buyer.getName(), this.productFactory.product.getName(), price, amount);
			o1.setSignature(this.sign(o1, buyer));
			this.context.dexClient.placeOrder(o1);
		}
		if (orderMod.equals(OrderMode.Both) || orderMod.equals(OrderMode.OnlySell)) {
			PlaceOrderRequest o2 = PlaceOrderRequest.limitSell(seller.getName(), this.productFactory.product.getName(), price, amount);
			o2.setSignature(this.sign(o2, seller));
			this.context.dexClient.placeOrder(o2);
		}
	}
	
	private String sign(PlaceOrderRequest request, User user) throws Exception {
		request.setTimestamp(System.currentTimeMillis());
		request.setFeeAddress(FEE_ADDRESS);
		request.setFeeRateTaker(PlaceOrderRequest.FEE_RATE_TAKER_MIN);
		
		if (this.traders.signIgnored) {
			return "0x123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456781b";
		} else {
			TypedOrder order = TypedOrder.from(request.toOrder(
					this.productFactory.product.getId(), user.getId()), user.getName(), this.productFactory.btc, this.productFactory.usdt);
			try {
				return this.context.am.signMessage(order.hash(), false, AddressTool.address(request.getAddress()));
			} catch (IllegalArgumentException e) {
				System.out.println("fail sign " + request.getAddress());
				throw e;
			}
		}
	}
}

class Context {
	public Client dexClient;
	public Optional<Cfx> cfx;
	public AccountManager am;
	public String password;
	
	@Autowired
	public void init(
			@Value("${dex.url}") String dexUrl,
			@Value("${dex.cfx.disabled}") boolean cfxDisabled,
			@Value("${dex.cfx.url}") String cfxUrl,
			@Value("${user.keystore}") String keystore,
			@Value("${user.keyfile.password}") String password) throws IOException,Exception {
		this.dexClient = new Client(dexUrl);
		this.cfx = cfxDisabled ? Optional.empty() : Optional.of(new CfxBuilder(cfxUrl).withRetry(3, 1000).withCallTimeout(3000).build());
		this.password = password;

		if (this.cfx.isPresent()) {
			System.out.println("cfx url is " + cfxUrl);
			System.out.println("dex url is " + dexUrl);
			BigInteger chainId = this.cfx.get().getStatus().sendAndGet().getChainId();
			Domain.defaultChainId = chainId.longValueExact();
			RawTransaction.setDefaultChainId(chainId);
			this.am = new AccountManager(keystore, this.cfx.get().getIntNetworkId());
		} else {
			Domain.defaultChainId = this.dexClient.getChainId();
			this.am = new AccountManager(keystore, Math.toIntExact(Domain.defaultChainId));
		}
	}
}

class ProductFactory {
	@Value("${currency.btc.name}")
	private String btcName;
	@Value("${currency.usdt.name}")
	private String usdtName;
	@Value("${product.name}")
	private String btcUsdt;
	
	public Currency btc;
	public Currency usdt;
	public Product product;
	
	@Autowired
	public void init(Context context) {
		this.btc = this.setupCurrency(context.dexClient, this.btcName);
		this.usdt = this.setupCurrency(context.dexClient, this.usdtName);
		this.product = this.setupProduct(context.dexClient);
	}
	
	private Currency setupCurrency(Client client, String name) {
		Optional<Currency> existingCurrency = client.getCurrency(name);
		if (!existingCurrency.isPresent()) {
			System.out.println(String.format("Currency [%s] not found!", name));
			System.exit(1);
		}
		
		return existingCurrency.get();
	}
	
	private Product setupProduct(Client client) {
		Optional<Product> existingProduct = client.getProduct(this.btcUsdt);
		if (!existingProduct.isPresent()) {
			throw new RuntimeException(String.format("Product [%s] not found!", this.btcUsdt));
		}
		
		return existingProduct.get();
	}
}

class Traders {
	@Value("${user.erc777.address}")
	protected String erc777UserHex;
	protected conflux.web3j.types.Address erc777User;
	@Value("${user.trader.number}")
	private int numTraders;
	@Value("#{'${user.trader.predefined}'.split(',')}")
	private List<String> tradersHex;
	private List<conflux.web3j.types.Address> traders;
	static boolean needUnlock = true;
	
	@Value("${user.sign.ignored}")
	public boolean signIgnored;
	
	private List<User> users = new ArrayList<User>();
	
	public List<User> getUsers() {
		return this.users;
	}
	
	public User random(Random rand) {
		int index = rand.nextInt(this.users.size());
		return this.users.get(index);
	}
	
	@Autowired
	public void init(Context context, ProductFactory factory) throws Exception {
		this.erc777User = AddressTool.address(this.erc777UserHex);
		if (!needUnlock) {
			return;
		}
		this.tradersHex.removeIf(String::isEmpty);
		tradersHex = tradersHex.stream().map(String::toLowerCase).collect(Collectors.toList());
		if (this.tradersHex.size() == 1 || (this.tradersHex.isEmpty() && this.numTraders < 2)) {
			throw new Exception("too few traders configured, requires 2 at least");
		}

		if (this.tradersHex.isEmpty()) {
			this.tradersHex = this.generateNewTraders(context);
		}
		this.traders = tradersHex.stream().map(AddressTool::address).collect(Collectors.toList());
		for (conflux.web3j.types.Address trader : this.traders) {
			if (!needUnlock) {
				// unload is too slow, so skip it if you want.
				break;
			}
			boolean unlock = context.am.unlock(trader, context.password, Duration.ofDays(1000));
			if (unlock) {
				System.out.println("unlock ok, "+trader);
			} else {
				throw new IllegalArgumentException("Unlock failed for " + trader);
			}
		}
		
		boolean depositRequired = false;
        Currency baseCurrency = factory.btc;
        for (String trader : this.tradersHex) {
			Optional<User> user = context.dexClient.getUser(trader);
			Optional<Account> btcAccount = context.dexClient.getAccount(trader, baseCurrency.getName());
			Optional<Account> usdtAccount = context.dexClient.getAccount(trader, factory.usdt.getName());
			
			if (user.isPresent() && btcAccount.isPresent() && usdtAccount.isPresent()) {
				this.users.add(user.get());
			} else {
				depositRequired = true;
				this.users.clear();
				break;
			}
		}

		if (!depositRequired) {
			return;
		}
		
		if (!context.cfx.isPresent()) {
			throw new Exception("CFX disabled and user not initialized in database");
		}
		
		BigInteger balance = context.cfx.get().getBalance(this.erc777User).sendAndGet();
		if (balance.compareTo(BigInteger.ZERO) == 0) {
			throw new Exception("CFX not enough for ERC777 user: " + this.erc777User);
		}
		
		System.out.println("Begin to deposit 10 BTCs and 100 USDTs for each trader ...");
		
		conflux.web3j.Account erc777Account = conflux.web3j.Account.unlock(context.cfx.get(), context.am, this.erc777User, context.password);
        depositCurrency(context, baseCurrency, erc777Account, 1);
        String lastTxHash = depositCurrency(context, factory.usdt, erc777Account, 1);

        // wait for the receipt of last tx
		System.out.println("Waiting for the completion of deposit txs ...");
		this.waitForReceiptAndNonce(context.cfx.get(), lastTxHash, this.erc777User, erc777Account.getPoolNonce());
		System.out.println("All txs completed");
		
		// wait for DEX to monitor the contract event logs to mint for each trader
		System.out.println("Waiting for the DEX to poll event logs to mint for traders ...");
		for (int i = 0; i < this.traders.size(); i++) {
			this.waitForAccounts(context.dexClient, factory, this.tradersHex.get(i));
			System.out.printf("[progress] %d/%d\n", i + 1, this.traders.size());
		}
	}

    protected String depositCurrency(Context context, Currency currency, conflux.web3j.Account erc777Account, int tenPower) throws Exception {
        BigInteger depositAmount;//
        depositAmount = BigInteger.TEN.pow(currency.getDecimalDigits() + tenPower);
		String tokenAddressHex = currency.getTokenAddress();
		CfxAddress tokenAddress = new CfxAddress(tokenAddressHex, context.cfx.get().getIntNetworkId());
		CfxAddress crclAddress = new CfxAddress(currency.getContractAddress(), context.cfx.get().getIntNetworkId());

		this.ensureErc777Balance(context.cfx.get(), tokenAddress, depositAmount);
        ERC777 usdtExecutor = new ERC777(context.cfx.get(), tokenAddress, erc777Account);
        String lastTxHash = "";
        for (String recipient : this.tradersHex) {
            lastTxHash = usdtExecutor.send(new Option(), crclAddress, depositAmount,
					Numeric.hexStringToByteArray(recipient));
			System.out.println("deposit " + currency.getName() + " x " + depositAmount
					+ ", to " + recipient + ", hash " + lastTxHash);
        }
        return lastTxHash;
    }

    private List<String> generateNewTraders(Context context) throws Exception {
		System.out.printf("Begin to create %d traders to place orders ...\n", this.numTraders);
		
		List<String> traders = new ArrayList<String>(this.numTraders);
		List<conflux.web3j.types.Address> list = context.am.list();
		for (int i = 0; i < this.numTraders; i++) {
			String newTrader;
			if (i<list.size()) {
				newTrader = list.get(i).getHexAddress();
			} else {
				newTrader = context.am.create(context.password).getHexAddress();
			}
			traders.add(newTrader);
			System.out.println("\tnew trader: " + newTrader);
		}
		
		return traders;
	}
	
	private void waitForReceiptAndNonce(Cfx cfx, String txHash, conflux.web3j.types.Address txSender, BigInteger expectedNonce) throws InterruptedException {
		Receipt receipt = cfx.waitForReceipt(txHash);
		if (receipt.getOutcomeStatus() != 0) {
			throw new RuntimeException(String.format("Receipt failed, outcome = %s, tx = %s", receipt.getOutcomeStatus(), txHash));
		}

		cfx.waitForNonce(txSender, expectedNonce);
	}
	
	private void ensureErc777Balance(Cfx cfx, conflux.web3j.types.Address erc777, BigInteger amountPerTrader) throws Exception {
		ERC777 call = new ERC777(cfx, new CfxAddress(erc777.getAddress()));
		BigInteger balance = call.balanceOf(this.erc777User.getHexAddress());
		BigInteger totalDeposit = amountPerTrader.multiply(BigInteger.valueOf(this.traders.size()));
		System.out.println("");
		if (balance == null || balance.compareTo(totalDeposit) < 0) {
			throw new Exception(String.format("ERC777 [%s] balance not enough to deposit" +
					"\n need %s , actual %s, user %s", erc777, totalDeposit, balance, erc777User));
		}
	}
	
	private void waitForAccounts(Client client, ProductFactory factory, String username) throws InterruptedException {
		Optional<User> user = Optional.empty();
		while (!user.isPresent()) {
			Thread.sleep(1000);
			user = client.getUser(username);
		}
		
		this.users.add(user.get());
		
		while (!client.getAccount(username, factory.btc.getName()).isPresent()) {
			Thread.sleep(1000);
		}
		
		while (!client.getAccount(username, factory.usdt.getName()).isPresent()) {
			Thread.sleep(1000);
		}
	}
}

class PriceRandom {
	private Random rand = new Random();
	private Duration granularity = Duration.ofMinutes(1);
	
	@Value("${trade.price.limit}")
	private double limitRatio;
	@Value("${trade.price.max}")
	private double max;
	@Value("${trade.price.min}")
	private double min;
	
	private Instant tickCloseTime;
	private double tickOpen;
	private double tickClose;
	private double tickMax;
	private double tickMin;
	
	@Autowired
	public void initialize(@Value("${trade.price.init}") double open) {
		this.newTick(open);
	}
	
	private void newTick(double open) {
		this.tickCloseTime = Instant.now().plus(this.granularity);
		this.tickOpen = open;
		this.tickClose = open;
		this.tickMax = Math.min(open * (1 + limitRatio), this.max);
		this.tickMin = Math.max(open * (1 - limitRatio), this.min);
	}
	
	public double next() {
		if (Instant.now().isAfter(this.tickCloseTime)) {
			this.newTick(this.tickClose);
		}
		
		this.tickClose = this.tickOpen + this.rand.nextGaussian();
		
		if (this.tickClose > this.tickMax) {
			this.tickClose = this.tickMax;
		}
		
		if (this.tickClose < this.tickMin) {
			this.tickClose = this.tickMin;
		}
		
		return this.tickClose;
	}
}
