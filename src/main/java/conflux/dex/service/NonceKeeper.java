package conflux.dex.service;

import conflux.dex.common.BusinessException;
import conflux.dex.dao.ConfigDao;
import conflux.dex.dao.DexDao;
import conflux.web3j.Account;
import conflux.web3j.Cfx;
import conflux.web3j.response.Transaction;
import org.slf4j.Logger;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import java.math.BigInteger;
import java.util.Optional;

import static conflux.dex.dao.ConfigDao.ADMIN_NONCE_KEY;

public class NonceKeeper {
    public static void checkNonce(Logger logger, Account admin, DexDao dao) {
        Cfx cfx = admin.getCfx();
        // using nonce saved in database could prevent most bad cases.
        // unless transaction sent out of this system.
        Optional<String> config = dao.getConfig(ADMIN_NONCE_KEY);
        if (!config.isPresent()) {
            return;
        }
        String str = config.get();
        BigInteger nonceInDB = new BigInteger(str);
        int compare = nonceInDB.compareTo(admin.getNonce());
        if(compare < 0){
            /**
             * After sending transaction, the next nonce is saved to DB, {@link #reserve}
             * But in case the saving action may be lost, or transactions are sent out of this system,
             * then nonce on chain will be greater than off chain.
             */
            logger.warn("nonce in database is less than on chain, {} < {}", nonceInDB, admin.getNonce());
            return;
        } else if (compare == 0) {
            // Things go well.
            return;
        }
        /** Here, the off chain nonce is greater than on chain, it indicates that transactions are pending in
         * transaction pool on chain, the nonce on chain haven't catchup.
         * How ever, we still can not use the nonce in DB, because the same problem may occur as mentioned above,
         * that is, tx is sent but saving nonce is lost.
         * We need check the last prepared/reserved tx by hash, to determine whether it have been received by the chain.
         * */
        //use `orElse` to avoid optional checking and null checking
        logger.warn("nonce in DB is greater than on chain. {} > {}", nonceInDB, admin.getNonce());
        String reservedHash = dao.getConfig(ConfigDao.KEY_LAST_TX_RESERVED_HASH).orElse("");
        if (reservedHash.isEmpty()) {
            logger.warn("last reserved hash does not exist.");
            return;
        }
        // last hash and nonce are saved together, so if hash exists, nonce must exist.
        String reservedNonceStr = dao.getConfig(ConfigDao.KEY_LAST_TX_RESERVED_NONCE)
                .orElseThrow(() -> BusinessException.system("Reserved nonce not found."));
        BigInteger lastNonce = new BigInteger(reservedNonceStr);
        Optional<Transaction> transaction = cfx.getTransactionByHash(reservedHash).sendAndGet();
        if (transaction.isPresent()) {
            // last transaction is on chain, compute next nonce.
            BigInteger next = lastNonce.add(BigInteger.ONE);
            admin.setNonce(next);
            logger.warn("Last transaction found on chain, use next nonce. {} {}", reservedHash, next);
            return;
        }
        // last transaction is not on chain, reuse last one.
        logger.info("Last transaction NOT found {}, reuse last [reserved] nonce from database {}"
                , reservedHash, lastNonce);
        admin.setNonce(lastNonce);
    }
    /**
     * Save the last prepared/reserved tx hash and nonce,
     * note that the real/final `send transaction` may fail in various way,
     * for example, the network may be cutoff, or the JavaVM may be killed,
     * we can only check the transaction by hash when startup.
     * @param resendOnError
     * @param dao
     * @param txHash
     * @param nonce
     */
    public static void reserve(boolean resendOnError, DexDao dao, String txHash, BigInteger nonce) {
        if (resendOnError) {
            return;
        }
        dao.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                dao.setConfig(ConfigDao.KEY_LAST_TX_RESERVED_HASH, txHash);
                dao.setConfig(ConfigDao.KEY_LAST_TX_RESERVED_NONCE, nonce.toString());
            }
        });
    }

    public static void saveNext(boolean resendOnError, DexDao dao, String nonce) {
        if (resendOnError) {
            return;
        }
        // persist nonce, prevent nonce too stale after restart.
        dao.setConfig(ADMIN_NONCE_KEY, nonce);
    }
}
