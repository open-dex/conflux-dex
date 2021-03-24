package conflux.dex.service.blockchain.settle;

import java.math.BigDecimal;
import java.math.BigInteger;

import conflux.dex.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.web3j.abi.datatypes.DynamicBytes;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.TypedWithdraw;
import conflux.dex.blockchain.TypedWithdrawCrossChain;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.WithdrawEventArg;
import conflux.dex.model.Account;
import conflux.dex.model.Currency;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.User;
import conflux.dex.model.WithdrawRecord;
import conflux.dex.service.AccountService;

class WithdrawSettlement extends Settleable {
	
	private static Logger logger = LoggerFactory.getLogger(WithdrawSettlement.class);
	
	private WithdrawRecord record;
	
	public WithdrawSettlement(WithdrawRecord record) {
		super(record.getId(), record.getTxHash(), record.getTxNonce());
		
		this.record = record;
	}

	@Override
	public Settleable.Context getSettlementContext(DexDao dao) throws Exception {
		Currency currency = dao.getCurrencyByName(this.record.getCurrency()).mustGet();		
		DynamicBytes sig = EncodeUtils.hex2Bytes(this.record.getSignature());
		String data = this.record.isCrossChain()
				? EncodeUtils.encode("withdrawCrossChain", TypedWithdrawCrossChain.create(this.record, currency), sig)
				: EncodeUtils.encode("withdraw", TypedWithdraw.create(this.record, currency), sig);
		BigInteger gasLimit = BlockchainConfig.instance.txGasLimitWithdraw;
		BigInteger storageLimit = BlockchainConfig.instance.txStorageLimit;
		
		return new Settleable.Context(currency.getContractAddress(), data, gasLimit, storageLimit);
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		User user = dao.getUserByName(this.record.getUserAddress()).mustGet();
		Account account = AccountService.mustGetAccount(dao, user.getId(), this.record.getCurrency());

		switch (status) {
		case OnChainConfirmed:
			dao.execute(new TransactionCallbackWithoutResult() {
				
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus ts) {
					dao.updateWithdrawSettlement(record.getId(), status, txHash, txNonce);
					dao.updateAccountBalance(account.getId(), record.getAmount().negate(), BigDecimal.ZERO);
				}
				
			});
			
			Events.WITHDRAW_COMPLETED.fire(new WithdrawEventArg(this.record, account));
			
			break;
		case OnChainFailed:
		case OnChainReceiptValidationFailed:
			dao.execute(new TransactionCallbackWithoutResult() {
				
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus ts) {
					dao.updateWithdrawSettlement(record.getId(), status, txHash, txNonce);
					dao.updateAccountBalance(account.getId(), record.getAmount().negate(), record.getAmount());
				}
				
			});
			
			Events.WITHDRAW_FAILED.fire(new WithdrawEventArg(this.record, account));
			
			break;
		case OnChainSettled:
		case OffChainSettled:
			dao.updateWithdrawSettlement(record.getId(), status, txHash, txNonce);
			break;
		default:
			String msg = "Unhandled withdraw status " + status +
				" id "+ this.record.getId();
			logger.error(msg);
			throw BusinessException.system(msg);
		}
	}
	
	@Override
	public boolean suppressError(String errorData) {
		logger.info("withdraw failed due to error: {}", errorData);
		return true;
	}
	
}
