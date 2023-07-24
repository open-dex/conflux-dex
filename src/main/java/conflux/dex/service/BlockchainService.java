package conflux.dex.service;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import conflux.dex.blockchain.EventBlockchain;
import conflux.dex.blockchain.log.DepositData;
import conflux.dex.blockchain.log.ScheduleWithdrawRequest;
import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.Currency;
import conflux.dex.service.blockchain.DepositEventHandler;
import conflux.dex.service.blockchain.WithdrawEventHandler;
import conflux.dex.ws.topic.AccountTopic;
import conflux.web3j.RpcException;

/**
 * BlockchainService monitor the deposit/withdraw events on blockchain.
 */
@Service
public class BlockchainService {
	private static final Logger logger = LoggerFactory.getLogger(BlockchainService.class);
	
	private static final String CONFIG_POLL_EPOCH = "blockchain.event.epoch";
	
	private final DexDao dao;
	private final EventBlockchain event;
	private final OrderService orderService;
	private final AccountTopic topic;
	
	@Autowired
	public BlockchainService(DexDao dao, EventBlockchain event, OrderService orderService, AccountTopic topic) {
		this.dao = dao;
		this.event = event;
		this.orderService = orderService;
		this.topic = topic;
		
		Events.NEW_CURRENCY_ADDED.addHandler(this::addCurrency);
	}
	
	@PostConstruct
	public void start() {
		logger.info("initialization started ...");
		
		List<Currency> currencies = this.dao.listCurrencies();
		for (Currency currency : currencies) {
			this.addCurrency(currency);
		}
		
		// poll from epoch that persisted in database if exists
		Optional<String> pollEpochConfig = this.dao.getConfig(CONFIG_POLL_EPOCH);
		pollEpochConfig.ifPresent(s -> this.event.setPollEpochFrom(new BigInteger(s)));
		
		logger.info("initialization completed, currencies = {}, poll epoch = {}", 
				currencies.size(), this.event.getPollEpochFrom());
	}
	
	public void addCurrency(Currency currency) {
		String contractAddress = currency.getContractAddress();
		this.event.addAddress(contractAddress);
		logger.info("succeed to add new address to poll event logs, {} {}", currency.getName(), contractAddress);
	}

	public List<conflux.web3j.types.Address> getListenedAddress() {
		return event.getAddresses();
	}
	
	/**
	 * Periodically poll event logs on blockchain.
	 */
	@Scheduled(initialDelay = 5000, fixedDelay = 5000)
	public void poll() {
		logger.trace("poll event logs started");
		
		try {
			while (this.safePoll()) {
				logger.trace("continue to poll event logs");
			}
		} catch (RpcException e) {
			if (Utils.isRpcError(e)) {
				logger.error("rpc failed to poll event logs", e);
			} else {
				logger.error("failed to poll event logs.", e);
			}
		} catch (Exception e) {
			logger.error("failed to poll event logs", e);
		}
		
		logger.trace("poll event logs ended");
	}
	
	private boolean safePoll() throws RpcException {
		List<DepositData> deposits = new LinkedList<>();
		List<ScheduleWithdrawRequest> schedule = new LinkedList<>();
		
		BigInteger pollEpochNum = this.event.getPollEpochFrom();
		boolean more = this.event.getLatestLogs(deposits, schedule);
		BigInteger nextPollEpochNum = this.event.getPollEpochFrom();
		
		if (!deposits.isEmpty()) {
			logger.info("polled deposit event logs: epochFrom = {}, epochToExclude = {}, count = {}", pollEpochNum, nextPollEpochNum, deposits.size());
		}
		
		if (!schedule.isEmpty()) {
			logger.info("polled withdraw event logs: epochFrom = {}, epochToExclude = {}, count = {}", pollEpochNum, nextPollEpochNum, schedule.size());
		}
		
		DepositEventHandler depositEventHandler = new DepositEventHandler(deposits, this.dao);
		WithdrawEventHandler withdrawEventHandler = new WithdrawEventHandler(schedule, this.dao, this.orderService);
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
				depositEventHandler.handle(dao);
				withdrawEventHandler.handle(dao);
				dao.setConfig(CONFIG_POLL_EPOCH, nextPollEpochNum.toString());
			}
			
		});
		
		depositEventHandler.publish(this.topic, this.dao);
		withdrawEventHandler.publish(this.topic, this.dao);
		
		return more;
	}
}
