package conflux.dex.service.blockchain;

import conflux.dex.config.BlockchainConfig;
import conflux.dex.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;

@Service
public class EvmTxService {
    Logger log = LoggerFactory.getLogger(getClass());
    private Web3j web3j;
    private RawTransactionManager rawTxManager;
    @Value("${user.admin.privateKey}")
    public String adminPrivateKey;
    @Autowired
    ConfigService configService;
    @Autowired
    BlockchainConfig config;

    @PostConstruct
    public void init() {
        log.info("cfx url [{}]", configService.cfxUrl);
        this.web3j = Web3j.build(new HttpService(configService.cfxUrl));
        this.rawTxManager = new RawTransactionManager(this.web3j, Credentials.create(adminPrivateKey));
    }

    public String[] buildTx(String to,
                            BigInteger nonce,
                            String functionData,
                            BigInteger gasPrice,
                            BigInteger gasLimit) {
        BigInteger ethValue = BigInteger.ZERO;
        RawTransaction tx =
                RawTransaction.createTransaction(nonce, gasPrice, gasLimit,
                        to, ethValue, functionData);

        String signedTx = rawTxManager.sign(tx);
        String txHash = Hash.sha3(signedTx);

        return new String[]{signedTx, txHash};
    }
}
