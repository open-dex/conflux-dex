package conflux.dex.dao;

import org.junit.Test;

public class ProductDaoTest {

    @Test(expected = NullPointerException.class)
    public void testAddProduct() {
        ProductDaoImpl productDao = new ProductDaoImpl();
        TestDexDao dao = new TestDexDao();
        productDao.addProduct(dao.product);
    }

    @Test(expected = NullPointerException.class)
    public void testAddInstantExchangeProduct() {
        ProductDaoImpl productDao = new ProductDaoImpl();
        TestDexDao dao = new TestDexDao();
        productDao.addInstantExchangeProduct(dao.product3);
    }

    @Test(expected = NullPointerException.class)
    public void testListProducts() {
        ProductDaoImpl productDao = new ProductDaoImpl();
        productDao.listProducts();
    }

    @Test(expected = NullPointerException.class)
    public void testGetProduct() {
        ProductDaoImpl productDao = new ProductDaoImpl();
        productDao.getProduct(1);
    }

    @Test(expected = NullPointerException.class)
    public void testGetProductByName() {
        ProductDaoImpl productDao = new ProductDaoImpl();
        productDao.getProductByName("p");
    }

    @Test(expected = NullPointerException.class)
    public void testListProductsByCurrencyId() {
        ProductDaoImpl productDao = new ProductDaoImpl();
        productDao.listProductsByCurrencyId(1);
    }
}
