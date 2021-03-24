package conflux.dex.service.blockchain;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import conflux.dex.config.BlockchainPruneConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.Currency;
import conflux.dex.model.DataPruneRecord;
import conflux.dex.model.TransferRecord;
import conflux.dex.model.WithdrawRecord;
import conflux.dex.model.WithdrawType;
import conflux.dex.service.blockchain.settle.prune.PruneSettlement;
import conflux.dex.tool.contract.CrclContract;
import conflux.web3j.Cfx;

/**
 * PruneCrclService is a service to periodically prune historical withdraw and transfer requests 
 * in different CRCL contracts.
 * 
 * The withdraw and transfer request hashes are recorded in contract to avoid replay attack from
 * DEX itself, which leads to collateral for storage increase over time. So, DEX have to clean up
 * the recorded request hashes timely.
 */
@Service
public class PruneCrclService {
	private Logger log = LoggerFactory.getLogger(getClass());
	private static class Queue {
		// Delete requests in the order of request id
		public NavigableSet<DataPruneRecord> withdraws = new ConcurrentSkipListSet<DataPruneRecord>(DataPruneRecord.IdComparator);
		public NavigableSet<DataPruneRecord> transfers = new ConcurrentSkipListSet<DataPruneRecord>(DataPruneRecord.IdComparator);
	}
	
	// Each asset has a configuration to record the last pruned id.
	// When service restarted, unhandled requests will be loaded into queue for prune again.
	private static final String CONFIG_KEY_WITHDRAW_FORMAT = "prune.withdraw.%s.id";
	private static final String CONFIG_KEY_TRANSFER_FORMAT = "prune.transfer.%s.id";
	private static final int DEFAULT_PAGE_SIZE = 1000;
	
	private DexDao dao;
	private BlockchainSettlementService service;
	
	// One queue for each currency
	private Map<String, Queue> currency2Queues = new ConcurrentHashMap<String, Queue>();
	
	@Autowired
	private Cfx cfx;
	@Autowired
	private BlockchainPruneConfig config = new BlockchainPruneConfig();
	
	@Autowired
	public PruneCrclService(DexDao dao, BlockchainSettlementService service) {
		this.dao = dao;
		this.service = service;
		
		Events.WITHDRAW_COMPLETED.addHandler(data -> addWithdraw(data.record));
		Events.TRANSFER.addHandler(data -> addTransfer(data.record));
	}
	
	private void addWithdraw(WithdrawRecord record) {
		if (record.getType() != WithdrawType.OnChainRequest) {
			DataPruneRecord data = new DataPruneRecord(record.getId(), record.getTimestamp(), record.getHash());
			this.currency2Queues.computeIfAbsent(record.getCurrency(), c -> new Queue()).withdraws.add(data);
		}
	}
	
	private void addTransfer(TransferRecord record) {
		DataPruneRecord data = new DataPruneRecord(record.getId(), record.getTimestamp(), record.getHash());
		this.currency2Queues.computeIfAbsent(record.getCurrency(), c -> new Queue()).transfers.add(data);
	}
	
	@Scheduled(
			initialDelayString = "${blockchain.prune.crcl.interval.millis:600000}",
			fixedDelayString = "${blockchain.prune.crcl.interval.millis:600000}")
	public void prune() {
		long pruneTime = System.currentTimeMillis() - this.config.crclIntervalMillis;
		this.prune(pruneTime);
	}
	
	private void prune(long pruneTime) {
		for (Map.Entry<String, Queue> entry : this.currency2Queues.entrySet()) {
			this.prune(pruneTime, entry.getKey(), entry.getValue());
		}
	}
	
	private void prune(long timestamp, String currency, Queue queue) {
		// query database first in case of database out of service
		String crcl = this.dao.getCurrencyByName(currency).mustGet().getContractAddress();
		
		// remove items from queue to prune
		List<DataPruneRecord> expiredWithdraws = removeBefore(queue.withdraws, timestamp);
		List<DataPruneRecord> expiredTransfers = removeBefore(queue.transfers, timestamp);
		
		if (expiredWithdraws.isEmpty() && expiredTransfers.isEmpty()) {
			return;
		}
		
		// update timestamp
		this.service.submit(PruneSettlement.updateCrclTimestamp(crcl, this.config.updateTimestampGasLimit, timestamp));
		
		// batch delete withdraw requests
		this.prune(crcl, expiredWithdraws, String.format(CONFIG_KEY_WITHDRAW_FORMAT, currency));
		
		// batch delete transfer requests
		this.prune(crcl, expiredTransfers, String.format(CONFIG_KEY_TRANSFER_FORMAT, currency));
	}
	
	private static List<DataPruneRecord> removeBefore(NavigableSet<DataPruneRecord> records, long timestamp) {
		List<DataPruneRecord> expired = new LinkedList<DataPruneRecord>();
		
		while (!records.isEmpty() && records.first().getTimestamp() < timestamp) {
			expired.add(records.pollFirst());
		}
		
		return expired;
	}
	
	private void prune(String crcl, List<DataPruneRecord> records, String configKey) {
		for (int fromIndex = 0, size = records.size(); fromIndex < size;) {
			int toIndex = Math.min(fromIndex + this.config.deleteBatchSize, size);
			List<DataPruneRecord> batch = records.subList(fromIndex, toIndex);
			
			BigInteger gasLimit = this.config.batchDeleteCrclRequestsGasLimit(batch.size());
			this.service.submit(PruneSettlement.deleteCrclRequests(crcl, gasLimit, batch, configKey));
			
			fromIndex += batch.size();
		}
	}
	
	@PostConstruct
	public void init() {
		log.info("init start.");
		List<Currency> currencies = this.dao.listCurrencies();
		
		for (Currency currency : currencies) {
			this.initWithdraw(currency.getName());
			this.initTransfer(currency.getName());
		}
		
		this.prune(System.currentTimeMillis());
		log.info("init finished.");
	}
	
	private void initWithdraw(String currency) {
		/** {@link conflux.dex.service.blockchain.settle.prune.DeleteCrclRequestsSettlement#update(DexDao, SettlementStatus, String, long)}  */
		String confirmedKey = String.format(CONFIG_KEY_WITHDRAW_FORMAT, currency);
		String value = this.dao.getConfig(confirmedKey).orElse("0");

		long lastRecordId = Long.parseLong(value);
		List<WithdrawRecord> records;

		do {
			records = this.dao.listWithdrawRecords(currency, lastRecordId + 1, DEFAULT_PAGE_SIZE);
			if (records.isEmpty()) {
				break;
			}
			
			for (WithdrawRecord record : records) {
				this.addWithdraw(record);
				lastRecordId = record.getId();
			}
		} while (records.size() >= DEFAULT_PAGE_SIZE);

		Optional.ofNullable(this.currency2Queues.get(currency)).ifPresent(q->{
			checkOnChain(currency, confirmedKey, q.withdraws);
		});
	}

	/**
	 * When restart, there may have unconfirmed transaction. Before prune again,
	 * check whether the hash is still on chain.
	 */
	private void checkOnChain(String currency, String confirmKey, NavigableSet<DataPruneRecord> records) {
		// check on chain
		Currency currencyBean = this.dao.getCurrencyByName(currency).mustGet();
		CrclContract crcl = new CrclContract(cfx, currencyBean.getContractAddress());
		long confirmedId = 0;
		while (!records.isEmpty()) {
			DataPruneRecord record = records.first();
			BigInteger timestamp = crcl.getTimestamp(record.getHash());
			if (timestamp == null || timestamp.compareTo(BigInteger.ZERO) == 0) {
				records.pollFirst();
				confirmedId = record.getId();
				log.info("record not found on chain, currency {}, hash {}", currency, record.getHash());
			} else {
				// stop check at the first one which is still on chain.
				break;
			}
		}
		if (confirmedId > 0) {
			this.dao.setConfig(confirmKey, String.valueOf(confirmedId));
		}
	}
	
	private void initTransfer(String currency) {
		String confirmedKey = String.format(CONFIG_KEY_TRANSFER_FORMAT, currency);
		String value = this.dao.getConfig(confirmedKey).orElse("0");

		long lastRecordId = Long.parseLong(value);
		List<TransferRecord> records;
		
		do {
			records = this.dao.listTransferRecords(currency, lastRecordId + 1, DEFAULT_PAGE_SIZE);
			if (records.isEmpty()) {
				break;
			}
			
			for (TransferRecord record : records) {
				this.addTransfer(record);
				lastRecordId = record.getId();
			}
		} while (records.size() >= DEFAULT_PAGE_SIZE);

		Optional.ofNullable(this.currency2Queues.get(currency)).ifPresent(q->{
			checkOnChain(currency, confirmedKey, q.transfers);
		});
	}

}
