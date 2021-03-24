package conflux.dex;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import conflux.dex.blockchain.CfxBuilder;
import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.common.BusinessException;
import conflux.dex.common.SignatureValidator;
import conflux.dex.common.Validators;
import conflux.dex.common.channel.Channel;
import conflux.dex.config.ConfigRefresh;
import conflux.dex.config.ConfigService;
import conflux.dex.controller.metric.MetricsServlet;
import conflux.dex.dao.DexDao;
import conflux.dex.worker.ticker.DefaultTickGranularity;
import conflux.web3j.Cfx;
import conflux.web3j.types.RawTransaction;

@SpringBootApplication
@EnableScheduling
public class Application {

	private static String lockFilename = ".dex.lock";
	
	private static Logger logger = LoggerFactory.getLogger(Application.class);
	
	@Autowired
	private ApplicationContext context;
	
	@Value("${repository.inmemory:false}")
	private boolean inMemoryRepository;


	@Autowired
	private ConfigService configService;
	
	@Value("${system.timezone.id}")
	private String timezoneId;
	
	@Value("${user.signature.ignore:false}")
	private boolean signatureIgnored;
	
	@Value("${user.signature.disabled:false}")
	private boolean signatureDisabled;

	private static final String CFX_BEAN_NAME = "cfxBean";
	
	@Bean
	@Primary
	public DexDao getDao() {
		if (this.inMemoryRepository) {
			return DexDao.newInMemory();
		}
		
		return DexDao.newSpringJDBC(this.context);
	}
	
	@Bean
	public Channel<Object> getObjectChannel() {
		return Channel.create();
	}
	
	@Bean(destroyMethod = "shutdownNow")
	@Primary
	public ExecutorService commonThreadPool() {
		return Executors.newCachedThreadPool();
	}

	@Bean(destroyMethod = "close", name = CFX_BEAN_NAME)
	@ConfigRefresh
	public Cfx cfx() {
		String cfxUrl = configService.cfxUrl;
		int cfxRetry = configService.cfxRetry;
		long cfxIntervalMillis = configService.cfxIntervalMillis;

		logger.info("Create CFX, url = {}, retry = {}, intervalMillis = {}",
				cfxUrl, cfxRetry, cfxIntervalMillis);
		Cfx cfx = new CfxBuilder(cfxUrl)
				.withRetry(cfxRetry, cfxIntervalMillis)
				.withCallTimeout(configService.cfxCallTimeoutMillis)
				.build();
		configService.hook(CFX_BEAN_NAME, "cfxUrl", "cfxRetry", "cfxIntervalMillis");

		BigInteger chainId = cfx.getStatus().sendAndGet().getChainId();
		Domain.defaultChainId = chainId.longValueExact();
		RawTransaction.setDefaultChainId(chainId);
		
		return cfx;
	}
	
	@Bean
	public TimeZone getSystemTimeZone() {
		TimeZone timeZone = TimeZone.getTimeZone(this.timezoneId);
		if (timeZone.getID().equals("GMT")) {
			throw BusinessException.internalError("invalid timezone configured");
		}
		DefaultTickGranularity.zoneId = timeZone.toZoneId();
		return timeZone;
	}
	
	@Bean
	public SignatureValidator getSignatureValidator() {
		if (this.signatureIgnored) {
			SignatureValidator.DEFAULT.setIgnored(true);
		}
		
		if (this.signatureDisabled) {
			SignatureValidator.DEFAULT.setDisabled(true);
		}
		
		return SignatureValidator.DEFAULT;
	}
	
	// enable WebSocket
	@Bean
	public ServerEndpointExporter serverEndpointExporter() {
		return new ServerEndpointExporter();
	}
	
	@Bean
	public ServletRegistrationBean<MetricsServlet> registerMetricsServlet() {
		return new ServletRegistrationBean<MetricsServlet>(new MetricsServlet(), "/system/metrics/*");
	}
	
	@Autowired
	public void setDefaultTimeDrift(@Value("${user.signature.timestamp.drift.millis:180000}") long millis) {
		Validators.defaultTimeDriftMillis = millis;
	}
	
	public static void main(String[] args) throws IOException{
		Path path = Paths.get(lockFilename);
		FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		// Exclusive lock, will be released after JAVA VM destroyed.
		// Note that it could prevent overlapping locking but not reading/writing/deletion.
		FileLock fileLock = channel.tryLock();
		if (fileLock == null) {
			logger.error("Can not acquire lock.");
			return;
		}
		SpringApplication.run(Application.class, args);
	}
}
