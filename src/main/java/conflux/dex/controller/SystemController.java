package conflux.dex.controller;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import conflux.dex.service.blockchain.PruneOrderService;
import conflux.dex.tool.ScanTool;
import conflux.dex.tool.contract.CrclContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;

import conflux.dex.blockchain.EventBlockchain;
import conflux.dex.blockchain.OrderBlockchain;
import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.blockchain.crypto.legacy.NodejsWrapper;
import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.common.Validators;
import conflux.dex.config.AuthAspect;
import conflux.dex.config.AuthRequire;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.config.ConfigService;
import conflux.dex.config.PruneConfig;
import conflux.dex.config.qps.LimitedResource;
import conflux.dex.config.qps.QPSConfigService;
import conflux.dex.controller.request.AdminCancelOrdersRequest;
import conflux.dex.controller.request.ConfigRequest;
import conflux.dex.controller.request.SystemCommand;
import conflux.dex.controller.request.UpdateAdminAccountRequest;
import conflux.dex.dao.AccountDao;
import conflux.dex.dao.MiscDAO;
import conflux.dex.dao.TradeDao;
import conflux.dex.dao.UserDao;
import conflux.dex.model.BinLog;
import conflux.dex.model.Config;
import conflux.dex.model.CrossChainToken;
import conflux.dex.model.PagingResult;
import conflux.dex.model.Trade;
import conflux.dex.model.User;
import conflux.dex.service.HealthService;
import conflux.dex.service.HealthService.PauseSource;
import conflux.dex.service.OrderService;
import conflux.dex.service.ShuttleflowService;
import conflux.dex.service.blockchain.BlockchainSettlementService;
import conflux.dex.service.blockchain.TransactionConfirmationMonitor;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.dex.tool.SpringTool;
import conflux.dex.tool.contract.BoomflowContract;
import conflux.web3j.Cfx;
import conflux.web3j.RpcException;
import conflux.web3j.contract.diagnostics.Recall;

/**
 * System management
 * System maintenance APIs for administrator only.
 */
@RestController
@RequestMapping("/system")
public class SystemController {

	private static final Logger logger = LoggerFactory.getLogger(SystemController.class);

	@Value("${ui.admin.address}")
	private String adminAddress;
	@Value("${user.admin.address}")
	private String tradeAdminAddress;

	private HealthService healthService;
	private OrderService orderService;
	private PruneOrderService pruneOrderService;
	private OrderBlockchain blockchain;
	private ShuttleflowService shuttleflow;

	@Autowired
	ConfigService configService;

	private BlockchainConfig blockchainConfig = new BlockchainConfig();

	@Autowired
	public SystemController(HealthService healthService
			, OrderService orderService
			, PruneOrderService pruneOrderService
			, OrderBlockchain blockchain) {
		this.healthService = healthService;
		this.orderService = orderService;
		this.blockchain = blockchain;
		this.pruneOrderService = pruneOrderService;
	}

	@Autowired
	public void setBlockchainConfig(BlockchainConfig blockchainConfig) {
		this.blockchainConfig = blockchainConfig;
	}

	@Autowired
	public void setShuttleflow(ShuttleflowService shuttleflow) {
		this.shuttleflow = shuttleflow;
	}

	/**
	 * @ignore
	 */
	@GetMapping("/tx-items")
	@AuthRequire
	public Collection<Settleable> getTxItems() {
		return SpringTool.getBean(TransactionConfirmationMonitor.class).getItems().values();
	}

	/**
	 * @ignore
	 * @return
	 */
	@AuthRequire
	@GetMapping("/admin-addresses")
	public ImmutableMap<String, String> getTradeAdminAddress() {
		return ImmutableMap.of("uiAdminAddress", adminAddress,
				"tradeAdminAddress", tradeAdminAddress);
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@GetMapping("/tx/check")
	public TransactionConfirmationMonitor.CheckConfirmationResult checkTx(Long txNonce) throws InterruptedException {
		try {
			return SpringTool.getBean(TransactionConfirmationMonitor.class).checkConfirmation(txNonce);
		} catch (RpcException e) {
			throw BusinessException.internalError(e.getMessage());
		}
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/tx/remove")
	public Settleable removeTx(@RequestBody SystemCommand command) {
        Validators.nonEmpty("Please set tx nonce at comment field", command.comment);
		Long txNonce = Long.valueOf(command.comment);
		Settleable settleable = SpringTool.getBean(TransactionConfirmationMonitor.class).removeTx(txNonce);
		logger.info("remove tx from monitor {} : {}"
				, txNonce, settleable);
		return settleable;
    }
	/**
	 * @ignore
	 */
	@GetMapping("/event/confirmed-epoch")
	public BigInteger getConfirmedEpoch() {
		return SpringTool.getBean(EventBlockchain.class).getConfirmedEpoch();
	}

	/**
	 * @ignore
	 */
	@GetMapping("/event/next-fetch-log-epoch")
	public BigInteger getNextFetchLogEpoch() {
		return SpringTool.getBean(EventBlockchain.class).getPollEpochFrom();
	}

	/**
	 * list off chain tx in queue
	 * @ignore
	 * @return
	 */
	@GetMapping("/tx/off-chain")
	@AuthRequire
	public Settleable[] listOffChainTx() {
		return SpringTool.getBean(BlockchainSettlementService.class).getTasks();
	}

	@GetMapping("/tx/handling")
	@AuthRequire
	public Settleable getHandlingTx() {
		BlockchainSettlementService bean = SpringTool.getBean(BlockchainSettlementService.class);
		return bean.getHandlingData();
	}

	/**
	 * @ignore
	 * @param request
	 * @return
	 */
	@PostMapping("/set-config")
	@AuthRequire
	public HashMap<String, Object> setConfig(@RequestBody ConfigRequest request) {
		HashMap<String, Object> msg = configService.setConfig(request.name.trim(), request.value.trim());
		return msg;
	}

	/**
	 * @ignore
	 * @param command
	 * @return
	 */
	@PostMapping("/list-config")
	@AuthRequire
	public List<Config> listConfig(@RequestBody SystemCommand command) {
		return this.configService.listAllConfig();
	}

	/**
	 * @ignore
	 */
	@GetMapping("/get-block-chain-config")
	@AuthRequire
	public  BlockchainConfig getBlockchainConfig() {
		BlockchainConfig copy = new BlockchainConfig();
		BlockchainConfig bean = SpringTool.getBean(BlockchainConfig.class);
		BeanUtils.copyProperties(bean, copy);
		return copy;
	}

	/**
	 * Suspend
	 * Suspend system to refuse signature related operations for maintenance.
	 * @ignore
	 */
	@PostMapping("/suspend")
	@AuthRequire
	public void suspend(@RequestBody SystemCommand command) {

		if (!SystemCommand.CMD_SUSPEND.equals(command.command)) {
			throw BusinessException.validateFailed("invalid command");
		}

		if (this.healthService.getPauseSource().isPresent()) {
			if (!"force".equalsIgnoreCase(command.comment) || this.healthService.getPauseSource().get() == PauseSource.Manual) {
				throw BusinessException.validateFailed("system already paused");
			}
		}

		this.healthService.pause(PauseSource.Manual, "paused in manual, comment = " + command.comment);

		logger.info("system paused in manual, comment = {}", command.comment);
	}

	/**
	 * @ignore
	 * @return
	 */
	@AuthRequire
	@PostMapping("/list-qps-limit")
	public Collection<LimitedResource> listQPSLimit() {
		return SpringTool.getBean(QPSConfigService.class)
				.list();
	}

	/**
	 * @ignore
	 */
	@GetMapping("/echo-ip")
	public String echoIP(HttpServletRequest req) {
		return req.getRemoteAddr();
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/set-qps-filter-mode")
	public void setQPSFilterMode(String mode) {
		SpringTool.getBean(QPSConfigService.class)
				.setFilterMode(mode);
	}

	/**
	 * @ignore
	 * @return
	 */
	@AuthRequire
	@PostMapping("/get-order-on-chain")
	public BoomflowContract.Order getOrderOnChain(String hash) {
		String boomflowAddress = Domain.boomflow().verifyingContract;
		BoomflowContract bfc = new BoomflowContract(SpringTool.getBean(Cfx.class), boomflowAddress);
		return bfc.getOrder(hash);
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-trade-by-nonce")
	public List<Trade> listUserTrades(String nonce) {
		return SpringTool.getBean(MiscDAO.class)
				.listTradeByTxNonce(nonce);
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-user-orders")
	public PagingResult<?> listUserTrades(long userId, int productId, int skip, int size) {
		return SpringTool.getBean(MiscDAO.class)
				.listOrders(userId, productId, skip, size);
	}
	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-user-trades")
	public PagingResult<?> listUserOrders(long userId, int productId, int skip, int size) {
		return SpringTool.getBean(MiscDAO.class)
				.listTrades(userId, productId, skip, size);
	}

	/**
	 * @ignore
	 * @return
	 */
	@AuthRequire
	@PostMapping("/count-status")
	public ImmutableMap<Object, Object> countStatus() {
		MiscDAO dao = SpringTool.getBean(MiscDAO.class);
		return ImmutableMap.builder()
				.put("countOrderByStatus", dao.countOrderByStatus())
				.put("countOrderCancelByStatus", dao.countOrderCancelByStatus())
				.put("countOrderPruneByStatus", dao.countOrderPruneByStatus())
				.put("countTradeByStatus", dao.countTradeByStatus())
				.put("countTransferByStatus", dao.countTransferByStatus())
				.put("countWithdrawByStatus", dao.countWithdrawByStatus())
				.build();
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-withdraw")
	public PagingResult<?> listWithdraw(String currency, int offset, int limit) {
		MiscDAO dao = SpringTool.getBean(MiscDAO.class);
		return dao.listWithdraw(currency, offset, limit);
	}


	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-deposit")
	public PagingResult<?> listDeposit(String currency, int offset, int limit) {
		MiscDAO dao = SpringTool.getBean(MiscDAO.class);
		return dao.listDeposit(currency, offset, limit);
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/show-bin-logs")
	public List<BinLog> showBinaryLogs() {
		return SpringTool.getBean(MiscDAO.class)
				.showBinaryLogs();
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/purge-bin-logs")
	public Object purgeBinaryLogs(String to) {
		List<Object> purge = SpringTool.getBean(MiscDAO.class)
				.purge(to);
		return purge;
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/get-qps-filter-mode")
	public String getQPSFilterMode() {
		return SpringTool.getBean(QPSConfigService.class)
				.getFilterMode();
	}
	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/del-qps-limit")
	public void delQPSLimit(@RequestBody LimitedResource resource) {
		SpringTool.getBean(QPSConfigService.class)
				.delete(resource);
	}
	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/set-qps-limit")
	public void setQPSLimit(@RequestBody LimitedResource resource) {
		SpringTool.getBean(QPSConfigService.class)
				.setRate(resource.key, resource.rate);
	}

	/**
	 * This method should not marked with @AuthRequire, other wise the parsing header process will runs two times.
	 * @ignore
	 */
	@PostMapping("/get-login-info")
	public Object getLoginInfo() {
		return SpringTool.getBean(AuthAspect.class).parseHeader();
	}
	/**
	 * @ignore
	 */
	@PostMapping("/login")
	public String login(@RequestBody SystemCommand command) {
		command.validate(this.adminAddress);
		return Base64Utils.encodeToString(Utils.toJson(command).getBytes());
	}
	/**
	 * Login method still need it.
	 * @ignore
	 */
	@PostMapping ("/encode")
	public String encodeSystemCommand(@RequestBody SystemCommand command) {
		return command.encodeHex();
	}
	/**
	 * Check system suspended
	 */
	@GetMapping("/suspend")
	public boolean isSuspended() {
		return this.healthService.getPauseSource().isPresent();
	}

    /**
     * Check system suspended
	 * @ignore
     */
    @GetMapping("/get-pause-source")
    public String getPauseSource() {
        return this.healthService.getPauseSourceString();
    }

	/**
	 * Get recent errors
	 * @ignore
	 */
	@GetMapping("/get-recent-errors")
	@AuthRequire
	public List<String> getRecentErrors() {
		return this.healthService.getRecentErrors();
	}


	/**
	 * List users
	 * @param offset offset to fetch users
	 * @param limit limit to fetch users. (0, 50)
	 * @return
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-user") // would be /users
	public UserPagingResult list(
			@RequestBody SystemCommand command,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "10") int limit
	) {
		Validators.validatePaging(offset, limit, 50);
		return new UserPagingResult(SpringTool.getBean(UserDao.class).listUser(offset, limit));
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/get-account-by-id")
	public conflux.dex.model.Account getAccountById(@RequestBody conflux.dex.model.Account account) {
		return SpringTool.getBean(AccountDao.class).getAccountById(account.getId()).orElse(null);
	}

	/**
	 * @ignore
	 * @return
	 */
	@AuthRequire
	@PostMapping("/get-user-by-id")
	public User getUserById(long id) {
		return SpringTool.getBean(UserDao.class).getUser(id).get().orElse(null);
	}
	
	/**
	 * Resume
	 * Resume system to accept signature related operations after maintenance.
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/resume")
	public void resume(@RequestBody SystemCommand command) {

		if (!SystemCommand.CMD_RESUME.equals(command.command)) {
			throw BusinessException.validateFailed("invalid command");
		}
		
		if (!this.healthService.getPauseSource().isPresent()) {
			throw BusinessException.validateFailed("system not paused yet");
		}
		
		this.healthService.resume();
		
		logger.info("system unpaused in manual, comment = {}", command.comment);
	}

	/**
	 * Cancel orders
	 * Cancel opening orders for maintenance.
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/orders/cancel")
	public void cancelOrders(@RequestBody AdminCancelOrdersRequest request) {
		if (!this.isSuspended()) {
			throw BusinessException.validateFailed("system not paused yet");
		}
		
		if (StringUtils.isEmpty(request.userAddress) && StringUtils.isEmpty(request.product)) {
			this.orderService.cancelAllOrders();
		} else if (StringUtils.isEmpty(request.userAddress)) {
			this.orderService.cancelOrdersByProduct(request.product);
		} else if (StringUtils.isEmpty(request.product)) {
			this.orderService.cancelOrdersByUser(request.userAddress);
		} else {
			this.orderService.cancelOrdersByUserProduct(request.userAddress, request.product);
		}
		
		logger.info("cancel all orders by admin for maintanence: {}", request);
	}

	/**
	 * Update administrator account
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/admin")
	public void updateAdminAccount(@RequestBody UpdateAdminAccountRequest request) {
		if (request.nonce != null) {
			this.blockchain.getAdmin().setNonce(request.nonce);
		}
		
		if (request.gasPrice != null) {
			this.blockchainConfig.txGasPrice = request.gasPrice;
		}
		
		if (request.storageLimit != null) {
			this.blockchainConfig.txStorageLimit = request.storageLimit;
		}
		
		logger.info("update account for maintanence: " + request);
	}

	/**
	 * recordOrders
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/recordOrders")
	public String recordOrders(@RequestBody SystemCommand command) throws Exception {
		Validators.nonEmpty(command.comment, "order hash(in comment)");
		List<Long> ids = Arrays.stream(command.comment.split(",")).map(str -> str.trim())
				.filter(str -> str.length() > 0)
				.map(Long::parseLong)
				.collect(Collectors.toList());
		try {
			this.pruneOrderService.recordOrdersByIds(ids);
			return "Submitted";
		} catch (Exception e) {
			logger.error("record orders fail", e);
			throw BusinessException.internalError("Record orders fail, "+e.toString());
		}
	}
	/**
	 * deployToken
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/deploy-currency")
	public String deployCurrency(@RequestBody SystemCommand command) {
		Validators.nonEmpty(command.comment, "token name(in comment field)");
		// will deploy crcl and token.
		return NodejsWrapper.deployToken(command.comment);
	}
	/**
	 * retrieve deploy status
	 * @ignore
	 * @return
	 */
	@GetMapping("/deploy-status")
	public Map<Object, Object> deployToken(String tempFilePath) {
		Validators.nonEmpty(tempFilePath, "tempFilePath");
		return NodejsWrapper.processStatus(tempFilePath);
	}
	/**
	 * diagnoseFailure
	 * @ignore
	 */
	@GetMapping("/diagnose-failure")
	public String diagnoseFailure(String hash) {
		Recall recall = new Recall(SpringTool.getBean(Cfx.class));
		try {
			String s = recall.diagnoseFailure(hash);
			return s == null ? "" : s;
		} catch (RpcException e) {
			throw BusinessException.internalError(e.getMessage());
		}
	}

	@AuthRequire
	@GetMapping("/cfx-url")
	public String getCfxUrl() {
		return this.configService.cfxUrl;
	}
	/**
	 * Refresh corss-chain tokens
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/crosschain/tokens/refresh")
	public boolean refreshCrossChainTokens(@RequestBody SystemCommand command) {
		if (!SystemCommand.CMD_REFRESH_CTOKEN.equals(command.command)) {
			throw BusinessException.validateFailed("invalid command");
		}
		
		return this.shuttleflow.reload();
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/list-contract-abi-files")
	public List<String> listContractAbiFiles() {
		return new ScanTool().listJsonFiles();
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/register-contract-abi")
	public String registerContractAbi(String addr, String abiType, String name) {
		try {
			return new ScanTool().regContractWrap(addr, abiType, name);
		} catch (Exception e) {
			throw BusinessException.system(e.getMessage());
		}
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/get-crcl-token")
	public String getCrclTokenAddress(String crcl) {
		CrclContract crclContract = new CrclContract(SpringTool.getBean(Cfx.class), crcl);
		return crclContract.getTokenAddress();
	}


	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/get-trade-by-id")
	public Trade getTradeById(long id) {
		return SpringTool.getBean(TradeDao.class).getById(id);
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/operate-prune-config")
	public PruneConfig operatePruneConfig(String action, String address) {
		PruneConfig config = SpringTool.getBean(PruneConfig.class);
		if ("add".equals(action)) {
			if (!config.getMarketmaker().stream().anyMatch(addr -> Objects.equals(addr, address))) {
				List<String> newList = new ArrayList<>(config.getMarketmaker().size() + 1);
				newList.addAll(config.getMarketmaker());
				newList.add(address);
				config.setMarketmaker(newList);
			}
		} else if ("remove".equals(action)) {
			List<String> filteredList = config.getMarketmaker().stream()
					.filter(addr -> !addr.equals(address))
					.collect(Collectors.toList());
			config.setMarketmaker(filteredList);
		}
		return config;
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/query-token-from-custodian")
	public CrossChainToken queryTokenFromCustodian(@RequestBody CrossChainToken token) {
		return this.shuttleflow.query(token.getAddress()).orElse(null);
	}

}
