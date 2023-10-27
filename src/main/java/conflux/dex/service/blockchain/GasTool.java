package conflux.dex.service.blockchain;

import conflux.web3j.Cfx;
import conflux.web3j.request.Call;
import conflux.web3j.response.UsedGasAndCollateral;
import conflux.web3j.types.Address;
import conflux.web3j.types.RawTransaction;

public class GasTool {
    public static void estimate(RawTransaction tx, Address from, Cfx cfx, String txHash) {
        Call request = new Call();
        request.setNonce(tx.getNonce());
        request.setValue(tx.getValue());
        request.setGasPrice(tx.getGasPrice());
        request.setData(tx.getData());
        request.setFrom(from);
        request.setTo(tx.getTo());
        try {
            UsedGasAndCollateral usedGasAndCollateral = cfx.estimateGasAndCollateral(request).sendAndGet();
            System.out.println("tx "+ txHash +" estimate gas "+usedGasAndCollateral.getGasUsed());
        } catch (Exception e) {
            System.out.println("estimate fail ");
            e.printStackTrace(System.out);
        }
    }
}
