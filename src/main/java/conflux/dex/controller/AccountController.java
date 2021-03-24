package conflux.dex.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import conflux.dex.blockchain.TypedTransfer;
import conflux.dex.blockchain.TypedWithdraw;
import conflux.dex.blockchain.TypedWithdrawCrossChain;
import conflux.dex.blockchain.crypto.TypedData;
import conflux.dex.common.BusinessException;
import conflux.dex.common.BusinessFault;
import conflux.dex.common.Validators;
import conflux.dex.controller.request.WithdrawRequest;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.TransferEventArg;
import conflux.dex.event.WithdrawEventArg;
import conflux.dex.model.Account;
import conflux.dex.model.AccountStatus;
import conflux.dex.model.CrossChainToken;
import conflux.dex.model.Currency;
import conflux.dex.model.DepositRecord;
import conflux.dex.model.PagingResult;
import conflux.dex.model.TransferRecord;
import conflux.dex.model.User;
import conflux.dex.model.WithdrawRecord;
import conflux.dex.service.AccountService;
import conflux.dex.service.ShuttleflowService;
import conflux.web3j.types.AddressType;

/**
 * Account management
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {
	Logger logger = LoggerFactory.getLogger(getClass());
	private DexDao dao;
	private AccountService accountService;
	
	@Value("${blockchain.bitcoin.testnet}")
	private boolean isBitcoinTestnet;
	
	private ShuttleflowService shuttleflow;
	
	@Autowired
	public AccountController(DexDao dao, AccountService accountService) {
		this.dao = dao;
		this.accountService = accountService;
	}
	
	@Autowired
	public void setShuttleflow(ShuttleflowService shuttleflow) {
		this.shuttleflow = shuttleflow;
	}
	
	/**
	 * Get account
	 * Get account of specified user address and currency.
	 * @param address user address.
	 * @param currency currency name.
	 */
	@GetMapping("/{address}/{currency}")
	public Account get(
			@PathVariable String address,
			@PathVariable String currency) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validateName(currency, Currency.MAX_LEN, "currency");
		
		long userId = this.dao.getUserByName(address).mustGet().getId();
		this.dao.getCurrencyByName(currency).mustGet();
		
		return AccountService.mustGetAccount(dao, userId, currency);
	}
	
	/**
	 * Get accounts
	 * List accounts of specified user address that ordered by currency name.
	 * @param address user address.
	 * @param offset offset to fetch accounts.
	 * @param limit limit to fetch accounts ([1, 50]).
	 */
	@GetMapping("/{address}")
	public AccountPagingResult list(
			@PathVariable String address,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validatePaging(offset, limit, 50);
		
		long userId = this.dao.getUserByName(address).mustGet().getId();
		
		return new AccountPagingResult(this.accountService.listAccounts(userId, offset, limit));
	}
	
	/**
	 * Get deposit records
	 * Get user deposit records of specified currency.
	 * @param address user address.
	 * @param currency currency name.
	 * @param offset offset to fetch records.
	 * @param limit limit to fetch records. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/deposit/{address}/{currency}")
	public DepositPagingResult listDepositRecords(
			@PathVariable String address,
			@PathVariable String currency,
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "false") boolean asc) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validateName(currency, Currency.MAX_LEN, "currency");
		Validators.validatePaging(offset, limit, 500);
		
		this.dao.getUserByName(address).mustGet();
		this.dao.getCurrencyByName(currency).mustGet();
		
		return new DepositPagingResult(this.dao.listDepositRecords(address, currency, offset, limit, asc));
	}
	
	/**
	 * Get all deposit records
	 * Get user deposit records of all currencies.
	 * @param address user address.
	 * @param offset offset to fetch records.
	 * @param limit limit to fetch records. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/deposit/{address}")
	public DepositPagingResult listDepositRecords(
			@PathVariable String address,
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "false") boolean asc) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validatePaging(offset, limit, 500);
		
		this.dao.getUserByName(address).mustGet();
		
		return new DepositPagingResult(this.dao.listDepositRecords(address, offset, limit, asc));
	}
	
	/**
	 * Withdraw
	 */
	@PostMapping("/withdraw")
	public void withdraw(@RequestBody WithdrawRequest request) throws IOException {
		/*request.userAddress = AddressTool.toHex(request.userAddress);
		if (Currency.CFX.equalsIgnoreCase(request.currency)
			|| Currency.FC.equalsIgnoreCase(request.currency)){
			request.recipient = AddressTool.toHex(request.recipient);
		}*/
		request.validate();
		
		Currency currency = this.dao.getCurrencyByName(request.currency).mustGet();
		
		if (request.isCrossChain()) {
			// Currency should be a cross-chain token, e.g. BTC, ETH 
			if (!currency.isCrossChain()) {
				throw BusinessFault.CurrencyNotCrossChain.rise();
			}
			
			// Only support to cross-chain withdraw BTC, ETH and USDT.
			// For others, user could only withdraw back to cToken.
			if (!request.currency.equalsIgnoreCase(Currency.BTC)
				&& !request.currency.equalsIgnoreCase(Currency.ETH)
				&& !request.currency.equalsIgnoreCase(Currency.USDT)
			){
				throw BusinessException
						.validateFailed("Unsupported cross chain withdrawal "+request.currency);
			}
			
			Validators.validateExternalChainAddress(currency.getName(), request.recipient, isBitcoinTestnet);
			
			// Validate against token sponsor configurations from shuttleflow proxy contract.
			if (this.shuttleflow != null) {
				CrossChainToken token = this.shuttleflow.getToken(currency.getId());
				if (token != null) {
					if (request.amount.compareTo(token.getMinWithdrawAmount()) < 0) {
						throw BusinessException.validateFailed("crosschain withdraw amount is too small");
					}
					
					if (request.fee.compareTo(token.getWithdrawFee()) < 0) {
						throw BusinessException.validateFailed("crosschain withdraw fee is too small");
					}
				}
			}
		} else if (request.amount.compareTo(currency.getMinimumWithdrawAmount()) < 0) {
			throw BusinessFault.AccountWithdrawAmountTooSmall.rise();
		}
		
		User user = this.dao.getUserByName(request.userAddress).mustGet();
		Account account = AccountService.mustGetAccount(dao, user.getId(), request.currency);
		
		if (account.getStatus().equals(AccountStatus.ForceWithdrawing)) {
			throw BusinessFault.AccountForceWithdrawing.rise();
		}
		
		if (account.getAvailable().compareTo(request.amount) < 0) {
			throw BusinessFault.AccountBalanceNotEnough.rise();
		}
		
		WithdrawRecord record = request.toRecord();
		TypedData data = record.isCrossChain()
				? TypedWithdrawCrossChain.create(record, currency)
				: TypedWithdraw.create(record, currency);
		String hash = data.validate(request.userAddress, request.signature);
		record.setHash(hash);
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				AccountService.mustUpdateAccountBalance(dao, account.getId(), request.amount, request.amount.negate());
				dao.mustAddWithdrawRecord(record);
			}
			
		});
		
		Events.WITHDRAW_SUBMITTED.fire(new WithdrawEventArg(record, account));
	}
	
	/**
	 * Get withdraw records
	 * Get user withdraw records of specified currency.
	 * @param address user address.
	 * @param currency currency name.
	 * @param offset offset to fetch records.
	 * @param limit limit to fetch records. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/withdraw/{address}/{currency}")
	public WithdrawPagingResult listWithdrawRecords(
			@PathVariable String address,
			@PathVariable String currency,
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "false") boolean asc) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validateName(currency, Currency.MAX_LEN, "currency");
		Validators.validatePaging(offset, limit, 500);
		
		this.dao.getUserByName(address).mustGet();
		this.dao.getCurrencyByName(currency).mustGet();
		
		return new WithdrawPagingResult(this.dao.listWithdrawRecords(address, currency, offset, limit, asc));
	}
	
	/**
	 * Get all withdraw records
	 * Get user withdraw records of all currencies.
	 * @param address user address.
	 * @param offset offset to fetch records.
	 * @param limit limit to fetch records. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/withdraw/{address}")
	public WithdrawPagingResult listWithdrawRecords(
			@PathVariable String address,
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "false") boolean asc) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validatePaging(offset, limit, 500);
		
		this.dao.getUserByName(address).mustGet();
		
		return new WithdrawPagingResult(this.dao.listWithdrawRecords(address, offset, limit, asc));
	}
	
	/**
	 * Transfer
	 * Transfer asset to one or multiple recipients.
	 */
	@PostMapping("/transfer")
	public void transfer(@RequestBody TransferRequest request) throws Exception {
		BigDecimal totalAmount = request.validate();
		
		User user = this.dao.getUserByName(request.userAddress).mustGet();
		Currency currency = this.dao.getCurrencyByName(request.currency).mustGet();
		Account account = AccountService.mustGetAccount(dao, user.getId(), request.currency);
		
		if (account.getStatus().equals(AccountStatus.ForceWithdrawing)) {
			throw BusinessFault.AccountForceWithdrawing.rise();
		}
		
		if (account.getAvailable().compareTo(totalAmount) < 0) {
			throw BusinessFault.AccountBalanceNotEnough.rise();
		}
		
		// validate EIP712 signature
		TransferRecord record = TransferRecord.request(
				request.userAddress, 
				request.currency, 
				request.recipients,
				request.timestamp,
				request.signature);
		TypedTransfer data = TypedTransfer.create(record, currency);
		String hash = data.validate(request.userAddress, request.signature);
		record.setHash(hash);
		
		// create user/account if recipient not exists
		Map<Long, BigDecimal> transfers = new HashMap<Long, BigDecimal>();
		List<Account> recipients = new ArrayList<Account>(request.recipients.size());
		for (Map.Entry<String, BigDecimal> entry : request.recipients.entrySet()) {
			User userNew = AccountService.getOrAddUser(dao, entry.getKey());
			Account toAccount = this.accountService.getOrAddAccount(userNew.getId(), request.currency, BigDecimal.ZERO);
			transfers.put(toAccount.getId(), entry.getValue());
			recipients.add(toAccount);
		}
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				AccountService.mustUpdateAccountBalance(dao, account.getId(), BigDecimal.ZERO, totalAmount.negate());
				
				for (Map.Entry<Long, BigDecimal> entry : transfers.entrySet()) {
					AccountService.mustUpdateAccountBalance(dao, entry.getKey(), BigDecimal.ZERO, entry.getValue());
				}
				
				dao.mustAddTransferRecord(record);
			}
			
		});
		
		Events.TRANSFER.fire(new TransferEventArg(record, account, recipients));
	}
	
	/**
	 * Get transfer records
	 * Get user transfer records of specified currency.
	 * @param address user address.
	 * @param currency currency name.
	 * @param offset offset to fetch records.
	 * @param limit limit to fetch records. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/transfer/{address}/{currency}")
	public TransferPagingResult listTransferRecords(
			@PathVariable String address,
			@PathVariable String currency,
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "false") boolean asc) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validateName(currency, Currency.MAX_LEN, "currency");
		Validators.validatePaging(offset, limit, 500);
		
		this.dao.getUserByName(address).mustGet();
		this.dao.getCurrencyByName(currency).mustGet();
		
		return new TransferPagingResult(this.dao.listTransferRecords(address, currency, offset, limit, asc));
	}
	
	/**
	 * Get all transfer records
	 * Get user transfer records of all currencies.
	 * @param address user address.
	 * @param offset offset to fetch records.
	 * @param limit limit to fetch records. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/transfer/{address}")
	public TransferPagingResult listTransferRecords(
			@PathVariable String address,
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "false") boolean asc) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validatePaging(offset, limit, 500);
		
		this.dao.getUserByName(address).mustGet();
		
		return new TransferPagingResult(this.dao.listTransferRecords(address, offset, limit, asc));
	}
}

class AccountPagingResult {
	/**
	 * Total number of accounts.
	 */
	public int total;
	/**
	 * Fetched accounts.
	 */
	public List<Account> items;
	
	public AccountPagingResult(PagingResult<Account> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}

class DepositPagingResult {
	/**
	 * Total number of deposit records.
	 */
	public int total;
	/**
	 * Fetched deposit records.
	 */
	public List<DepositRecord> items;
	
	public DepositPagingResult(PagingResult<DepositRecord> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}

class WithdrawPagingResult {
	/**
	 * Total number of withdraw records.
	 */
	public int total;
	/**
	 * Fetched withdraw records.
	 */
	public List<WithdrawRecord> items;
	
	public WithdrawPagingResult(PagingResult<WithdrawRecord> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}

class TransferRequest {
	/**
	 * User address to transfer.
	 */
	public String userAddress;
	/**
	 * Currency to transfer.
	 */
	public String currency;
	/**
	 * Recipients to receive the asset (at most 10).
	 * Key is recipient address, and value is transfer amount.
	 */
	public Map<String, BigDecimal> recipients;
	/**
	 * UNIX time in milliseconds.
	 */
	public long timestamp;
	/**
	 * Request signature.
	 */
	public String signature;
	
	public BigDecimal validate() {
		Validators.validateAddress(this.userAddress, AddressType.User, "address");
		Validators.validateName(this.currency, Currency.MAX_LEN, "currency");

		if (this.recipients == null || this.recipients.isEmpty()) {
			throw BusinessException.validateFailed("recipients not specified");
		}
		
		if (this.recipients.size() > 10) {
			throw BusinessException.validateFailed("too many recipients");
		}
		
		Validators.validateTimestamp(this.timestamp);
		Validators.validateSignature(this.signature);
		
		BigDecimal total = BigDecimal.ZERO;
		
		for (Map.Entry<String, BigDecimal> entry : this.recipients.entrySet()) {
			Validators.validateAddress(entry.getKey(), AddressType.User, "recipient");
			Validators.validateAmount(entry.getValue(), Currency.MAX_DECIMALS, null, null, "transfer amount");
			total = total.add(entry.getValue());
		}
		
		return total;
	}
}

class TransferPagingResult {
	/**
	 * Total number of transfer records.
	 */
	public int total;
	/**
	 * Fetched transfer records.
	 */
	public List<TransferRecord> items;
	
	public TransferPagingResult(PagingResult<TransferRecord> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}