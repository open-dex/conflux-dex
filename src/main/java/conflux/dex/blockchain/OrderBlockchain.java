package conflux.dex.blockchain;

import java.math.BigInteger;

import conflux.dex.controller.AddressTool;
import conflux.dex.service.NonceKeeper;
import conflux.web3j.AMNAccount;
import conflux.web3j.types.Address;
import org.influxdb.dto.Point.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.InfluxDBReportable;
import conflux.dex.common.Metrics.LongGauge;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.event.Events;
import conflux.web3j.Account;
import conflux.web3j.Cfx;
import conflux.web3j.CfxUnit;
import conflux.web3j.types.RawTransaction;
import org.web3j.crypto.Credentials;

@Component
public class OrderBlockchain implements InfluxDBReportable {
	
	private static final Logger logger = LoggerFactory.getLogger(OrderBlockchain.class);
	
	private static LongGauge balanceGauge = Metrics.longGauge(OrderBlockchain.class, "admin", "balance", "cfx");
	
	private AMNAccount admin;
	private Address adminAddress;
	
	private BlockchainConfig config = new BlockchainConfig();

	@Autowired
	public OrderBlockchain(Cfx cfx,
			@Value("${user.admin.address}") String adminAddress,
			@Value("${user.admin.privateKey}") String adminPrivateKey) {
		logger.info("Force init cfx, network id is {}", cfx.getNetworkId());
		this.adminAddress = AddressTool.address(adminAddress);
		Credentials credentials = Credentials.create(adminPrivateKey);
		Address address = new Address(credentials.getAddress(), cfx.getIntNetworkId());
		this.admin = new AMNAccount(cfx, address, credentials.getEcKeyPair());
		logger.info("adminAddress from PK is [{}]", this.admin.getAddress());
		if (!this.adminAddress.getHexAddress().equals(this.admin.getHexAddress())) {
			logger.info("configured admin address is {}", this.adminAddress);
			throw new IllegalArgumentException("admin address mismatch");
		}
		BigInteger balance = cfx.getBalance(this.adminAddress).sendAndGet();
		if (balance.compareTo(BigInteger.ZERO) == 0) {
			throw BusinessException.internalError("Balance of DEX admin " + this.adminAddress.getAddress() + " "
					+ this.adminAddress.getHexAddress() + " is too small: "
					+ balance );
		}
		
		balanceGauge.setValue(CfxUnit.drip2Cfx(balance).longValue());


		logger.info("DEX admin initialized: address = {}, nonce = {}, balance = {}",
				adminAddress, this.admin.getNonce(), CfxUnit.drip2Cfx(balance).toPlainString());
		
		Metrics.dumpReportable(this);
	}
	
	@Autowired
	public void setConfig(BlockchainConfig config) {
		this.config = config;
		RawTransaction.setDefaultGasPrice(config.txGasPrice);
	}

	public AMNAccount getAdmin() {
		return this.admin;
	}
	
	@Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
	public void checkHealth() {
		try {
			this.checkHealthUnsafe();
		} catch (Exception e) {
			logger.error("failed to check balance and nonce for DEX admin", e);
		}
	}
	
	private void checkHealthUnsafe() throws Exception {
		// check balance for administrator
		BigInteger balance = this.admin.getCfx().getBalance(this.adminAddress).sendAndGet();
		long balanceCfx = CfxUnit.drip2Cfx(balance).longValue();
		if (balanceCfx < this.config.adminMinBalanceCfx) {
			String error = String.format("too few balance for admin account, balance = %s cfx, min = %s cfx", balanceCfx, this.config.adminMinBalanceCfx);
			logger.error(error);
			Events.BLOCKCHAIN_ERROR.fire(error);
			return;
		}
		
		balanceGauge.setValue(balanceCfx);
		
		// check nonce for administrator
		BigInteger onChainNonce;
		BigInteger offChainNonce;
		int retryTimes = 3;
		int sleepSeconds = 10;
		do {
			onChainNonce = this.admin.getCfx().getNonce(this.admin.getAddress()).sendAndGet();
			offChainNonce = this.admin.getNonce();
			BigInteger nonceCache = NonceKeeper.nonceCache.get();
			if (nonceCache.compareTo(offChainNonce) > 0) {
				offChainNonce = nonceCache;
			}
			// admin nonce may be changed outside of DEX
			if (offChainNonce.compareTo(onChainNonce) >= 0) {
				break;
			} else if (retryTimes > 0) {
				logger.warn("off chain nonce {} < {} on chain, retry at {}", offChainNonce, onChainNonce, retryTimes);
				Thread.sleep(sleepSeconds * 1000);
			} else /*if (offChainNonce.compareTo(onChainNonce) < 0)*/ {
				String error = String.format("off chain nonce is less than on chain nonce, off = %s, on = %s", offChainNonce, onChainNonce);
				logger.error(error);
				Events.BLOCKCHAIN_ERROR.fire(error);
				return;
			}
			retryTimes --;
		} while (retryTimes >= 0);

//		if (onChainNonce.add(this.config.adminMaxNonceFuture).compareTo(offChainNonce) < 0) {
//			String error = String.format("too many txs pending in txpool, onChainNonce = %s, offChainNonce = %s, maxTooFuture = %s",
//					onChainNonce, offChainNonce, this.config.adminMaxNonceFuture);
//			logger.error(error);
//			this.healthService.pause(PauseSource.Blockchain, error);
//		}
	}

	@Override
	public Builder buildInfluxDBPoint(Builder builder) {
		return builder.addField("nonce", this.admin.getNonce());
	}
}
