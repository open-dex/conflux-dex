package conflux.dex.dao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.codahale.metrics.Histogram;
import conflux.dex.common.Metrics;
import conflux.dex.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

interface Transactional {
	<T> T execute(TransactionCallback<T> callback);
}

public interface DexDao extends Transactional, 
		CurrencyDao, ProductDao, UserDao, AccountDao, 
		OrderDao, TradeDao, TickDao, ConfigDao, DepositDao, WithdrawDao, 
		DailyLimitDao, DailyLimitRateDao, TransferDao {
	
	static DexDao newInMemory() {
		return newProxy(new InMemoryDexDaoInvocationHandler());
	}
	
	static DexDao newProxy(InvocationHandler handler) {
		return (DexDao) Proxy.newProxyInstance(DexDao.class.getClassLoader(), new Class[] { DexDao.class }, handler);
	}
	
	static DexDao newSpringJDBC(ApplicationContext context) {
		return newProxy(new SpringJDBCDexDaoInvocationHandler(context));
	}
	
}

abstract class DexDaoInvocationHandler implements InvocationHandler, Transactional {
	private Logger log = LoggerFactory.getLogger(getClass());
	protected Map<Class<?>, Object> delegates = new HashMap<Class<?>, Object>();
	
	protected DexDaoInvocationHandler() {
		this.delegates.put(Transactional.class, this);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Class<?> type = method.getDeclaringClass();
		Object impl = this.delegates.get(type);
		if (impl == null) {
			throw new Exception(String.format("delegate for type %s not registered", type.getName()));
		}
		
		try {
			return method.invoke(impl, args);
		} catch (InvocationTargetException e) {
			Throwable targetException = e.getTargetException();
			if (!(targetException instanceof BusinessException)) {
				log.error("DAO fail, method: {}, parameters: {}, {}", method.getName(), args, targetException.toString());
			}
			throw targetException;
		}
	}
	
}

class InMemoryDexDaoInvocationHandler extends DexDaoInvocationHandler {
	public InMemoryDexDaoInvocationHandler() {
		this.delegates.put(CurrencyDao.class, new InMemoryCurrencyDao());
		this.delegates.put(ProductDao.class, new InMemoryProductDao());
		this.delegates.put(UserDao.class, new InMemoryUserDao());
		this.delegates.put(AccountDao.class, new InMemoryAccountDao());
		this.delegates.put(OrderDao.class, new InMemoryOrderDao());
		this.delegates.put(TradeDao.class, new InMemoryTradeDao());
		this.delegates.put(TickDao.class, new InMemoryTickDao());
		this.delegates.put(ConfigDao.class, new InMemoryConfigDao());
		this.delegates.put(DepositDao.class, new InMemoryDepositDao());
		this.delegates.put(DailyLimitDao.class, new InMemoryDailyLimitDao());
		this.delegates.put(DailyLimitRateDao.class, new InMemoryDailyLimitRateDao());
	}

	@Override
	public <T> T execute(TransactionCallback<T> callback) {
		return callback.doInTransaction(null);
	}
}

class SpringJDBCDexDaoInvocationHandler extends DexDaoInvocationHandler {
	private static final Logger logger = LoggerFactory.getLogger(SpringJDBCDexDaoInvocationHandler.class);
	private static final Histogram latencyStat = Metrics.histogram(SpringJDBCDexDaoInvocationHandler.class, "latency");

	private TransactionTemplate tt;
	
	@Autowired
	public SpringJDBCDexDaoInvocationHandler(ApplicationContext context) {
		PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
		this.tt = new TransactionTemplate(transactionManager);
		
		this.delegates.put(CurrencyDao.class, context.getBean(CurrencyDaoImpl.class));
		this.delegates.put(ProductDao.class, context.getBean(ProductDaoImpl.class));
		this.delegates.put(UserDao.class, context.getBean(UserDaoImpl.class));
		this.delegates.put(AccountDao.class, context.getBean(AccountDaoImpl.class));
		this.delegates.put(OrderDao.class, context.getBean(OrderDaoImpl.class));
		this.delegates.put(TradeDao.class, context.getBean(TradeDaoImpl.class));
		this.delegates.put(TickDao.class, context.getBean(TickDaoImpl.class));
		this.delegates.put(ConfigDao.class, context.getBean(ConfigDaoImpl.class));
		this.delegates.put(WithdrawDao.class, context.getBean(WithdrawDaoImpl.class));
		this.delegates.put(DepositDao.class, context.getBean(DepositDaoImpl.class));
		this.delegates.put(DailyLimitDao.class, context.getBean(DailyLimitDaoImpl.class));
		this.delegates.put(DailyLimitRateDao.class, context.getBean(DailyLimitRateDaoImpl.class));
		this.delegates.put(TransferDao.class, context.getBean(TransferDaoImpl.class));
	}

	@Override
	public <T> T execute(TransactionCallback<T> callback) {
		RuntimeException txEx = null;
		
		// retry 5 times by default for recoverable runtime exceptions
		long start = System.currentTimeMillis();
		for (int i = 0; i < 5; i++) {
			try {
				return tt.execute(callback);
			} catch (CannotCreateTransactionException e) {
				// e.g. idle connection collected by database
				logger.warn("failed to execute in transaction (CannotCreateTransactionException), retry = {}, message = {}", i, e.getMessage());
				txEx = e;
			} catch (DeadlockLoserDataAccessException e) {
				logger.warn("failed to execute in transaction (DeadlockLoserDataAccessException), retry = {}, message = {}", i, e.getMessage());
				txEx = e;
			} catch (CannotAcquireLockException e) {
				txEx = e;
				logger.warn("failed to execute in transaction (CannotAcquireLockException), retry = {}, message = {}", i, e.getMessage());
			}
		}
		latencyStat.update(System.currentTimeMillis() - start);
		throw txEx;
	}
}