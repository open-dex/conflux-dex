package conflux.dex.service;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import conflux.dex.event.Events;
import org.junit.After;
import org.junit.Before;

import conflux.dex.common.channel.Channel;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.worker.DepthPriceLevel;

public abstract class EngineTester {
	protected TestDexDao dao;
	protected Channel<Object> channel;
	protected ExecutorService executor;
	protected ScheduledExecutorService scheduledExecutor;
	private EngineService service;
	private DailyLimitService dailyLimitService;

	@Before
	public void setUp() {
		this.dao = new TestDexDao();
		FeeService feeService = new FeeService(this.dao.get(), new AccountService(this.dao.get()));
		this.channel = Channel.create();
		this.executor = Executors.newCachedThreadPool();
		this.service = new EngineService(this.dao.get(), this.channel, this.executor);
		this.service.setFeeService(feeService);
		this.service.start();
		this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
		this.dailyLimitService = new DailyLimitService(this.dao.get(), channel);
		this.dailyLimitService.setup();
		Events.WORKER_ERROR.addHandler(str->{
			System.out.println("worker error:"+str);
			System.exit(500);
		});
	}

	@After
	public void tearDown() throws Exception {
		this.executor.shutdownNow();
		this.executor.awaitTermination(3, TimeUnit.SECONDS);
		this.dailyLimitService.close();
	}

	public List<Product> getPreloadedProducts() {
		return this.service.getPreloadedProducts();
	}

	public EnumMap<OrderSide, List<DepthPriceLevel>> getDepth(int productId, int step, int depth) {
		return this.service.getDepth(productId, step, depth);
	}

	public Tick getLast24HoursTick(int productId) {
		return this.service.getLast24HoursTick(productId);
	}
}
