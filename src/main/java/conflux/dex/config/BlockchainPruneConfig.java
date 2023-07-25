package conflux.dex.config;

import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlockchainPruneConfig {
	
	// Configurations in common
	@Value("${blockchain.prune.common.gas.intrinsic:30000}")
	public BigInteger intrinsicGasLimit = BigInteger.valueOf(30000);

	@Value("${blockchain.prune.common.delete.batch:100}")
	public int deleteBatchSize = 100;

	@Value("${blockchain.prune.common.update.gas:300000}")
	public BigInteger updateTimestampGasLimit = BigInteger.valueOf(300000);
	
	// Configurations for order prune
	@Value("${blockchain.prune.order.upload.batch:100}")
	public int uploadOrderBatchSize = 100;
	
	@Value("${blockchain.prune.order.upload.gas.exec:30000}")
	public int uploadOrderGasLimit = 30_000;
	
	@Value("${blockchain.prune.order.delete.gas.exec:40000}")
	public int deleteOrderGasLimit = 40000;
	
	// Configurations for CRCL prune
	@Value("${blockchain.prune.crcl.interval.millis:600000}")
	public long crclIntervalMillis = 600_000;
	
	@Value("${blockchain.prune.crcl.delete.gas.exec:10000}")
	public int deleteCrclRequestGasLimit = 10000;
	
	public BigInteger batchUploadOrdersGasLimit(int count) {
		return BigInteger.valueOf(this.uploadOrderGasLimit * count).add(this.intrinsicGasLimit);
	}
	
	public BigInteger batchDeleteOrdersGasLimit(int count) {
		return BigInteger.valueOf(this.deleteOrderGasLimit * count).add(this.intrinsicGasLimit);
	}
	
	public BigInteger batchDeleteCrclRequestsGasLimit(int count) {
		return BigInteger.valueOf(this.deleteCrclRequestGasLimit * count).add(this.intrinsicGasLimit);
	}

}
