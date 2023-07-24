package conflux.dex.tool;

import conflux.web3j.Account;
import conflux.web3j.types.Address;
import conflux.web3j.types.RawTransaction;
import conflux.web3j.types.SendTransactionResult;

import java.math.BigInteger;

public class CfxTxSender {
    public static String sendTx(Account account,
                                Address to,
                                String functionData,
                                BigInteger gasPrice,
                                BigInteger gasLimit,
                                BigInteger storageLimit) throws Exception {
        BigInteger nonce = account.getPoolNonce();
        BigInteger epochNumber = account.getCfx().getEpochNumber().sendAndGet();
        RawTransaction tx = RawTransaction.call(nonce, gasLimit,
                to, storageLimit, epochNumber, functionData);
        tx.setGasPrice(gasPrice);

        SendTransactionResult send = account.send(tx);
        return send.getTxHash();
    }
}
