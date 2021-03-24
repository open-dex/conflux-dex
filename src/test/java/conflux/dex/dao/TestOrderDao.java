package conflux.dex.dao;

import java.math.BigDecimal;

import org.junit.Assert;

import conflux.dex.controller.ProductController;
import conflux.dex.model.Account;
import conflux.dex.model.CancelOrderReason;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.Currency;
import conflux.dex.model.DailyLimit;
import conflux.dex.model.DailyLimitRate;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderType;
import conflux.dex.model.Product;
import conflux.dex.model.User;

public class TestOrderDao {

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

    public final Order o1;

    public final CancelOrderRequest r1;

    final ProductController productController;

    public TestOrderDao() {
        this.dao = DexDao.newInMemory();

        // currency
        this.cfx = new Currency("CFX", "0x11", "0x12", 18);
        this.cat = new Currency("CAT", "0x21", "0x22", 18);
        this.dog = new Currency("DOG", "0x31", "0x32", 18);
        this.bear = new Currency("BEAR", "0x41", "0x42", 18);
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

        // partially filled order
		this.o1 = new Order();
		this.o1.setClientOrderId("1");
		this.o1.setUserId(1L);
		this.o1.setProductId(1);
		this.o1.setPrice(BigDecimal.valueOf(100));
		this.o1.setAmount(BigDecimal.valueOf(10));
		this.o1.setSide(OrderSide.Buy);
		this.o1.setType(OrderType.Limit);
		this.o1.setFilledAmount(BigDecimal.valueOf(5));
		this.o1.setFilledFunds(BigDecimal.valueOf(500));
		this.dao.mustAddOrder(this.o1);

		// cancel order request
		this.r1 = new CancelOrderRequest();
		this.r1.setOrderId(1L);
		this.r1.setReason(CancelOrderReason.CustomerRequested);
		this.dao.mustAddCancelOrderRequest(this.r1);
    }

    public DexDao get() {
        return this.dao;
    }

}
