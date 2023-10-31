package conflux.dex.service.blockchain;

import conflux.web3j.Cfx;
import conflux.web3j.request.Call;
import conflux.web3j.response.UsedGasAndCollateral;
import conflux.web3j.types.Address;
import conflux.web3j.types.RawTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GasTool {
    private static final Logger logger = LoggerFactory.getLogger(GasTool.class);
    public static UsedGasAndCollateral estimate(RawTransaction tx, Address from, Cfx cfx) {
        Call request = new Call();
        request.setNonce(tx.getNonce());
        request.setValue(tx.getValue());
        request.setGasPrice(tx.getGasPrice());
        request.setData(tx.getData());
        request.setFrom(from);
        request.setTo(tx.getTo());
        try {
            UsedGasAndCollateral est = cfx.estimateGasAndCollateral(request).sendAndGet();
            logger.debug("used {} limit {}", est.getGasUsed(), est.getGasLimit());
            return est;
        } catch (Exception e) {
            logger.warn("estimate gas failed", e);
        }
        return null;
    }
}
