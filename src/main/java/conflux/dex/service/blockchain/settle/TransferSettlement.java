package conflux.dex.service.blockchain.settle;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.TypedTransfer;
import conflux.dex.blockchain.log.TransferData;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.TransferRecord;
import conflux.web3j.response.Log;
import conflux.web3j.response.Receipt;

class TransferSettlement extends Settleable {
	
	private static final Logger logger = LoggerFactory.getLogger(TransferSettlement.class);
	
	private TransferRecord record;
	
	public TransferSettlement(TransferRecord record) {
		super(record.getId(), record.getTxHash(), record.getTxNonce());
		
		this.record = record;
	}

	@Override
	public Settleable.Context getSettlementContext(DexDao dao) throws Exception {
		Currency currency = dao.getCurrencyByName(this.record.getCurrency()).mustGet();
		TypedTransfer request = TypedTransfer.create(this.record, currency);
		String data = EncodeUtils.encode("transferFor", request, EncodeUtils.hex2Bytes(this.record.getSignature()));
		BigInteger gasLimit = BlockchainConfig.instance.batchTransferGasLimit(request.recipients.size());
		BigInteger storageLimit = BlockchainConfig.instance.batchStorageLimit(request.recipients.size());
		return new Settleable.Context(currency.getContractAddress(), data, gasLimit, storageLimit);
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		dao.updateTransferSettlement(this.record.getId(), status, txHash, txNonce);
	}
	
	@Override
	public boolean matches(DexDao dao, Receipt receipt) {
		if (!this.matchesEventLogLength(receipt, this.record.getRecipients().size(), logger)) {
			return false;
		}
		
		String crcl = dao.getCurrencyByName(this.record.getCurrency()).mustGet().getContractAddress();
		TreeMap<String, BigDecimal> recipients = new TreeMap<String, BigDecimal>(String.CASE_INSENSITIVE_ORDER);
		recipients.putAll(this.record.getRecipients());
		
		boolean matched = true;
		int index = 0;
		
		for (Log log : receipt.getLogs()) {
			index++;
			
			String error = this.validate(log, crcl, recipients);
			if (!StringUtils.isEmpty(error)) {
				logger.error("failed to validate event logs of transfer settlement: index = {}, error = {}", index, error);
				matched = false;
			}
		}
		
		return matched;
	}
	
	private String validate(Log log, String crcl, Map<String, BigDecimal> recipients) {
		Optional<TransferData> data = TransferData.tryParse(log);
		if (!data.isPresent()) {
			return "failed to parse Transfer event";
		}
		
		String recipient = data.get().recipient;
		BigDecimal amount = recipients.get(recipient);
		if (amount == null) {
			return String.format("recipient address mismatch, onChain = %s", recipient);
		}
		
		return data.get().validate(crcl, this.record.getUserAddress(), recipient, amount);
	}
	
}
