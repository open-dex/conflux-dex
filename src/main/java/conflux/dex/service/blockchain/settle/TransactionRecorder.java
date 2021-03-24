package conflux.dex.service.blockchain.settle;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import conflux.web3j.Cfx;
import conflux.web3j.response.Receipt;
import conflux.web3j.types.RawTransaction;

/**
 * TransactionRecorder records transactions for a single settlement.
 * 
 * When transaction discarded or unpacked for a long time, DEX will re-send
 * transaction to RPC server with same nonce and higher gas price.
 * 
 * Not sure which transaction will be packed by miner, so TransactionRecorder
 * will track all transactions for a single settlement.
 * 
 * TODO should persist in database/file in case of program crash. Because,
 * only the hash and nonce of last sent transaction are saved in database.
 */
public class TransactionRecorder {
	
	// use same nonce to re-send transactions
	private long nonce;
	
	// track all re-sent transactions
	private List<Record> records = new ArrayList<Record>();
	
	public TransactionRecorder(String txHash, RawTransaction tx) {
		this.addRecord(txHash, tx);
	}

	// in case of service restarted, and database only records
	// the tx hash and nonce.
	public TransactionRecorder(String txHash, long nonce) {
		this.nonce = nonce;
		this.records.add(new Record(txHash));
	}
	
	public long getNonce() {
		return nonce;
	}
	
	public void addRecord(String txHash, RawTransaction tx) {
		// in case of service restarted and admin nonce changed since last settlement
		long nonce = tx.getNonce().longValueExact();
		if (this.nonce < nonce) {
			this.nonce = nonce;
		}
		
		this.records.add(new Record(txHash, tx));
	}
	
	public Record getLast() {
		return this.records.get(this.records.size() - 1);
	}
	
	/**
	 * Try to get receipt for all sent transactions.
	 */
	public Optional<Receipt> getReceipt(Cfx cfx) {
		for (Record record : this.records) {
			Optional<Receipt> receipt = cfx.getTransactionReceipt(record.txHash).sendAndGet();
			if (receipt.isPresent()) {
				return receipt;
			}
		}
		
		return Optional.empty();
	}

	public List<Record> getRecords() {
		return records;
	}

	public static class Record {
		public String txHash;
		public BigInteger gasPrice;	// maybe null
		public BigInteger epoch;	// maybe null
		public Error error;
		
		public Record(String txHash) {
			this.txHash = txHash;
		}
		
		public Record(String txHash, RawTransaction tx) {
			this.txHash = txHash;
			this.gasPrice = tx.getGasPrice();
			this.epoch = tx.getEpochHeight();
		}
	}
	
	public enum Error {
		TxNotPropagated,
		TxDiscarded,
		TxLongUnpacked,
	}

}
