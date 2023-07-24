package conflux.dex.tool;

import conflux.web3j.Account;
import conflux.web3j.types.Address;
import conflux.web3j.types.RawTransaction;
import conflux.web3j.types.SendTransactionResult;
import org.web3j.crypto.Hash;

import java.math.BigInteger;

public class CfxTxSender {
    public static SendTransactionResult sendTx(Account account,
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

        String signedTx = account.sign(tx);
        SendTransactionResult send = account.getCfx().sendRawTransactionAndGet(signedTx);
        return send;
    }

    public static String[] buildTx(Account account,
                          Address to,
                          BigInteger nonce,
                          BigInteger epochNumber,
                          String functionData,
                          BigInteger gasPrice,
                          BigInteger gasLimit,
                          BigInteger storageLimit) throws Exception {
        RawTransaction tx = RawTransaction.call(nonce, gasLimit,
                to, storageLimit, epochNumber, functionData);
        tx.setGasPrice(gasPrice);

        String signedTx = account.sign(tx);
        String txHash = Hash.sha3(signedTx);
        return new String[]{signedTx, txHash};
    }
}
