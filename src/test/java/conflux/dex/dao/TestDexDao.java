package conflux.dex.dao;

import java.math.BigDecimal;

import conflux.dex.blockchain.crypto.Domain;
import conflux.web3j.types.Address;
import org.junit.Assert;

import conflux.dex.controller.ProductController;
import conflux.dex.model.Account;
import conflux.dex.model.Currency;
import conflux.dex.model.DailyLimit;
import conflux.dex.model.DailyLimitRate;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Product;
import conflux.dex.model.User;

public class TestDexDao {
	
	private final DexDao dao;
	
	public final Currency cfx;
	public final Currency cat;
	public final Currency dog;
	public final Currency bear;
	
	public final Product product;
	public final Product product2;
	public final InstantExchangeProduct product3; // instant exchange product
	public final Product product4; // daily limit product
	
	public final User alice;
	public final User bob;
	
	public final Account aliceCfx;
	public final Account aliceCat;
	public final Account aliceDog;
	public final Account aliceBear;
	public final Account bobCfx;
	public final Account bobCat;
	public final Account bobDog;
	public final Account bobBear;
	
	final ProductController productController;
	
	public TestDexDao() {
		this.dao = DexDao.newInMemory();

		Domain.defaultChainId = Address.NETWORK_ID_TESTNET;
		// currency
		this.cfx = new Currency("CFX", "0x8e038c1c2765c4042a20df2af61df425eb1844f2", "0x83a92e6beb4c9dd5b58b0ab92bee1e5b6cee0fd0", 18);
		this.cat = new Currency("CAT", "0x849e5d42f3cd202e422554aea991806f38c692a5", "0x84c787e5b802fd068463295eaf89920882061e85", 18);
		this.dog = new Currency("DOG", "0x8d56b7dfc511a6ab35302dd4c1575c0e0faca936", "0x8a46375a0604577950f4637e4ea94f6daf33de93", 18);
		this.bear = new Currency("BEAR", "0x83808ffca37d14eeb63940bff0bd8479cb5dc7f8", "0x86779f40a2b861641e0c1c96c2bdb8ac855ed86d", 18);
		this.dao.addCurrency(this.cfx);
		this.dao.addCurrency(this.cat);
		this.dao.addCurrency(this.dog);
		this.dao.addCurrency(this.bear);
		
		// product
		this.product = new Product();
		this.product.setName("CAT-CFX");
		this.product.setBaseCurrencyId(this.cat.getId());
		this.product.setQuoteCurrencyId(this.cfx.getId());
		this.product.setPricePrecision(6);
		this.product.setAmountPrecision(3);
		this.product.setFundsPrecision(6);
		this.product.setMinOrderAmount(BigDecimal.valueOf(0.001));
		this.product.setMaxOrderAmount(BigDecimal.valueOf(100000000));
		this.product.setMinOrderFunds(BigDecimal.valueOf(0.000001));
		this.dao.addProduct(this.product);
		
		// product2
		this.product2 = new Product();
		this.product2.setName("CFX-DOG");
		this.product2.setBaseCurrencyId(this.cfx.getId());
		this.product2.setQuoteCurrencyId(this.dog.getId());
		this.product2.setPricePrecision(3);
		this.product2.setAmountPrecision(6);
		this.product2.setFundsPrecision(3);
		this.product2.setMinOrderAmount(BigDecimal.valueOf(0.000001));
		this.product2.setMaxOrderAmount(BigDecimal.valueOf(100000000));
		this.product2.setMinOrderFunds(BigDecimal.valueOf(0.001));
		this.dao.addProduct(this.product2);
	
		// product3 instant exchange
		this.product3 = new InstantExchangeProduct();
		this.product3.setName("CAT-DOG");
		this.product3.setBaseCurrencyId(this.cat.getId());
		this.product3.setQuoteCurrencyId(this.dog.getId());
		this.product3.setPricePrecision(6);
		this.product3.setAmountPrecision(3);
		this.product3.setFundsPrecision(3);
		this.product3.setMinOrderAmount(BigDecimal.valueOf(0.001));
		this.product3.setMaxOrderAmount(BigDecimal.valueOf(100000000));
		this.product3.setMinOrderFunds(BigDecimal.valueOf(0.001));
		this.product3.setBaseProductId(this.product.getId());
		this.product3.setQuoteProductId(this.product2.getId());
		this.dao.addProduct(this.product3);
		
		this.productController = new ProductController(this.dao, null, null);
		this.productController.validateInstantExchangeProduct(this.product3);
		Assert.assertTrue(this.product3.isBaseIsBaseSide());
		Assert.assertFalse(this.product3.isQuoteIsBaseSide());

		// product4
		this.product4 = new Product();
		this.product4.setName("CFX-BEAR");
		this.product4.setBaseCurrencyId(this.cfx.getId());
		this.product4.setQuoteCurrencyId(this.bear.getId());
		this.product4.setPricePrecision(3);
		this.product4.setAmountPrecision(6);
		this.product4.setFundsPrecision(3);
		this.product4.setMinOrderAmount(BigDecimal.valueOf(0.000001));
		this.product4.setMaxOrderAmount(BigDecimal.valueOf(100000000));
		this.product4.setMinOrderFunds(BigDecimal.valueOf(0.001));
		this.dao.addProduct(this.product4);
		
		DailyLimit dailyLimit = DailyLimit.newDailyLimit(
				4,
				"09:00:00",
				"21:00:00"
		);
		this.dao.addDailyLimit(dailyLimit);
		DailyLimitRate dailyLimitRate = DailyLimitRate.newDailyLimitRate(4, 0.1, 0.1, BigDecimal.valueOf(100));
		this.dao.addDailyLimitRate(dailyLimitRate);

		// user
		this.alice = new User("Alice");
		this.bob = new User("Bob");
		this.dao.addUser(this.alice);
		this.dao.addUser(this.bob);
		
		// account
		this.aliceCfx = new Account(alice.getId(), cfx.getName(), BigDecimal.valueOf(10));
		this.aliceCat = new Account(alice.getId(), cat.getName(), BigDecimal.valueOf(10000));
		this.aliceDog = new Account(alice.getId(), dog.getName(), BigDecimal.valueOf(20000));
		this.aliceBear = new Account(alice.getId(), bear.getName(), BigDecimal.valueOf(20000));
		this.bobCfx = new Account(bob.getId(), cfx.getName(), BigDecimal.valueOf(10));
		this.bobCat = new Account(bob.getId(), cat.getName(), BigDecimal.valueOf(10000));
		this.bobDog = new Account(bob.getId(), dog.getName(), BigDecimal.valueOf(20000));
		this.bobBear = new Account(bob.getId(), bear.getName(), BigDecimal.valueOf(20000));
		this.dao.addAccount(aliceCfx);
		this.dao.addAccount(aliceCat);
		this.dao.addAccount(aliceDog);
		this.dao.addAccount(aliceBear);
		this.dao.addAccount(bobCfx);
		this.dao.addAccount(bobCat);
		this.dao.addAccount(bobDog);
		this.dao.addAccount(bobBear);
	}
	
	public DexDao get() {
		return this.dao;
	}

}
