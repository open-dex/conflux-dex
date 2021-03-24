package conflux.dex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import conflux.dex.common.BusinessFault;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Product;

public interface ProductDao {

	void addProduct(Product product);
	void addInstantExchangeProduct(InstantExchangeProduct product);
	boolean updateProduct(Product product);

	List<Product> listProducts();

	EntityGetResult<Product> getProduct(int id);
	EntityGetResult<Product> getProductByName(String name);

	List<Integer> listProductsByCurrencyId(int id);

}

class InMemoryProductDao extends IdAllocator implements ProductDao {
	private Map<Integer, Product> items = new ConcurrentHashMap<Integer, Product>();
	private NavigableMap<String, Product> nameIndex = new ConcurrentSkipListMap<String, Product>(String.CASE_INSENSITIVE_ORDER);

	@Override
	public void addProduct(Product product) {
		product.setId(this.getNextId());
		this.items.put(product.getId(), product);
		this.nameIndex.put(product.getName(), product);
	}
	
	@Override
	public void addInstantExchangeProduct(InstantExchangeProduct product) {
		product.setId(this.getNextId());
		this.items.put(product.getId(), product);
		this.nameIndex.put(product.getName(), product);
	}
	
	@Override
	public boolean updateProduct(Product product) {
		if (!this.items.containsKey(product.getId())) {
			return false;
		}
		
		this.items.put(product.getId(), product);
		this.nameIndex.put(product.getName(), product);
		
		return true;
	}

	@Override
	public List<Product> listProducts() {
		List<Product> products = new ArrayList<>(this.nameIndex.values());
		return products;
	}

	@Override
	public EntityGetResult<Product> getProduct(int id) {
		return EntityGetResult.ofNullable(this.items.get(id), BusinessFault.ProductNotFound);
	}

	@Override
	public EntityGetResult<Product> getProductByName(String name) {
		return EntityGetResult.ofNullable(this.nameIndex.get(name), BusinessFault.ProductNotFound);
	}

	@Override
	public List<Integer> listProductsByCurrencyId(int id) {
		List<Integer> result = new LinkedList<Integer>();

		for (Map.Entry<Integer, Product> entry : this.items.entrySet()) {
			Product product = entry.getValue();
			if (product.getBaseCurrencyId() == id || product.getQuoteCurrencyId() == id) {
				result.add(entry.getKey());
			}
		}

		return result;
	}
}

@Repository
@CacheConfig(cacheNames = "dao.product")
class ProductDaoImpl extends BaseDaoImpl implements ProductDao {

	private static final RowMapper<Product> productRowMapper = new RowMapper<Product>() {
		@Override
		public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
			Product product;
			
			if (rs.getBoolean("instant_exchange")) {
				InstantExchangeProduct p = new InstantExchangeProduct();
				p.setBaseProductId(rs.getInt("base_product_id"));
				p.setQuoteProductId(rs.getInt("quote_product_id"));
				p.setBaseIsBaseSide(rs.getBoolean("base_is_base_side"));
				p.setQuoteIsBaseSide(rs.getBoolean("quote_is_base_side"));
				product = p;
			} else {  
				product = new Product();
			}
			
			product.setId(rs.getInt("id"));
			product.setName(rs.getString("name"));
			product.setBaseCurrencyId(rs.getInt("base_currency_id"));
			product.setQuoteCurrencyId(rs.getInt("quote_currency_id"));
			product.setPricePrecision(rs.getInt("price_precision"));
			product.setAmountPrecision(rs.getInt("amount_precision"));
			product.setFundsPrecision(rs.getInt("funds_precision"));
			product.setMinOrderAmount(rs.getBigDecimal("min_order_amount").stripTrailingZeros());
			product.setMaxOrderAmount(rs.getBigDecimal("max_order_amount").stripTrailingZeros());
			product.setMinOrderFunds(rs.getBigDecimal("min_order_funds").stripTrailingZeros());
			
			return product;
		}
	};

	private static final String SQL_INSERT = String.join(" ", 
			"INSERT INTO t_product",
			"(name, base_currency_id, quote_currency_id,",
			"price_precision, amount_precision, funds_precision, min_order_amount, max_order_amount, min_order_funds,",
			"instant_exchange, base_product_id, quote_product_id, base_is_base_side, quote_is_base_side)",
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
	
	private static final String SQL_UPDATE = String.join(" ",
			"UPDATE t_product SET",
			"price_precision = ?, amount_precision = ?, funds_precision = ?,",
			"min_order_amount = ?, max_order_amount = ?, min_order_funds = ?",
			"WHERE id = ?");

	@Override
	@Caching(evict = {
			@CacheEvict(key = "#product.id"),
			@CacheEvict(key = "#product.name.toLowerCase()"),
			@CacheEvict(key = "'list'"),
			@CacheEvict(key = "#product.id+'listByCurrencyId'"),
	})
	public void addProduct(Product product) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, product.getName());
				ps.setInt(2, product.getBaseCurrencyId());
				ps.setInt(3, product.getQuoteCurrencyId());
				ps.setInt(4, product.getPricePrecision());
				ps.setInt(5, product.getAmountPrecision());
				ps.setInt(6, product.getFundsPrecision());
				ps.setBigDecimal(7, product.getMinOrderAmount());
				ps.setBigDecimal(8, product.getMaxOrderAmount());
				ps.setBigDecimal(9, product.getMinOrderFunds());
				ps.setBoolean(10, false);
				ps.setNull(11, Types.INTEGER);
				ps.setNull(12, Types.INTEGER);
				ps.setNull(13,  Types.BOOLEAN);
				ps.setNull(14, Types.BOOLEAN);
				return ps;
			}
		};

		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.getJdbcTemplate().update(creator, keyHolder);
		product.setId(keyHolder.getKey().intValue());
	}
	
	@Override
	@Caching(evict = {
			@CacheEvict(key = "#product.id"),
			@CacheEvict(key = "#product.name.toLowerCase()"),
			@CacheEvict(key = "'list'"),
			@CacheEvict(key = "#product.id+'listByCurrencyId'"),
	})
	public boolean updateProduct(Product product) {
		return this.getJdbcTemplate().update(SQL_UPDATE,
				product.getPricePrecision(), product.getAmountPrecision(), product.getFundsPrecision(),
				product.getMinOrderAmount(), product.getMaxOrderAmount(), product.getMinOrderFunds(),
				product.getId()) > 0;
	}
	
	@Override
	@Caching(evict = {
			@CacheEvict(key = "#product.id"),
			@CacheEvict(key = "#product.name.toLowerCase()"),
			@CacheEvict(key = "'list'"),
			@CacheEvict(key = "#product.id+'listByCurrencyId'"),
	})
	public void addInstantExchangeProduct(InstantExchangeProduct product) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, product.getName());
				ps.setInt(2, product.getBaseCurrencyId());
				ps.setInt(3, product.getQuoteCurrencyId());
				ps.setInt(4, product.getPricePrecision());
				ps.setInt(5, product.getAmountPrecision());
				ps.setInt(6, product.getFundsPrecision());
				ps.setBigDecimal(7, product.getMinOrderAmount());
				ps.setBigDecimal(8, product.getMaxOrderAmount());
				ps.setBigDecimal(9, product.getMinOrderFunds());
				ps.setBoolean(10, true);
				ps.setInt(11, product.getBaseProductId());
				ps.setInt(12, product.getQuoteProductId());
				ps.setBoolean(13, product.isBaseIsBaseSide());
				ps.setBoolean(14, product.isQuoteIsBaseSide());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.getJdbcTemplate().update(creator, keyHolder);
		product.setId(keyHolder.getKey().intValue());
	}

	@Cacheable(key="'list'")
	@Override
	public List<Product> listProducts() {
		String sql = "SELECT * FROM t_product ORDER BY name";
		List<Product> products = this.getJdbcTemplate().query(sql, productRowMapper);
		return products;
	}

	@Override
	@Cacheable
	public EntityGetResult<Product> getProduct(int id) {
		String sql = "SELECT * FROM t_product WHERE id = ?";
		List<Product> products = this.getJdbcTemplate().query(sql, productRowMapper, id);
		return EntityGetResult.of(products, BusinessFault.ProductNotFound);
	}

	@Override
	@Cacheable
	public EntityGetResult<Product> getProductByName(String name) {
		String sql = "SELECT * FROM t_product WHERE name = ?";
		List<Product> products = this.getJdbcTemplate().query(sql, productRowMapper, name);
		return EntityGetResult.of(products, BusinessFault.ProductNotFound);
	}

	@Cacheable(key="#id + 'listByCurrencyId'")
	@Override
	public List<Integer> listProductsByCurrencyId(int id) {
		String sql = "SELECT id FROM t_product WHERE base_currency_id = ? OR quote_currency_id = ?";
		return this.getJdbcTemplate().queryForList(sql, Integer.class, id, id);
	}

}
