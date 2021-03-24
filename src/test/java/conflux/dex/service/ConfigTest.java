package conflux.dex.service;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectFactory;

import conflux.dex.config.BlockchainConfig;
import conflux.dex.config.ConfigService;
import conflux.dex.config.RefreshScope;
import conflux.dex.dao.DexDao;

public class ConfigTest {
    private ObjectFactory<?> objectFactory;
    private ConfigService configService;
    private RefreshScope refreshScope;
    private String beanName = "cfxBeanTest";
    private BlockchainConfig blockchainConfig;

    class FakeCfx{
        String cfxUrl;
        int cfxRetry;
        long cfxIntervalMillis;

        FakeCfx(String cfxUrl, Integer cfxRetry, long cfxIntervalMillis) {
            this.cfxUrl = cfxUrl;
            this.cfxRetry = cfxRetry;
            this.cfxIntervalMillis = cfxIntervalMillis;
        }
    }
    @Before
    public void setUp(){
        configService = new ConfigService();
        configService.cfxUrl = "url1";
        configService.cfxRetry = 3;
        configService.cfxIntervalMillis = 1000;

        refreshScope = new RefreshScope();
        objectFactory = (ObjectFactory<Object>) ()->{
            return new FakeCfx(configService.cfxUrl, configService.cfxRetry, configService.cfxIntervalMillis);
        };
        getBean();

        configService.setRefreshScope(refreshScope);
        configService.setConfigDao(DexDao.newInMemory());
        configService.init();
        configService.hook(beanName,"cfxUrl", "cfxRetry", "cfxIntervalMillis");

        blockchainConfig = new BlockchainConfig();
        blockchainConfig.setConfigService(configService);
        blockchainConfig.bind2configService();
    }

    @Test
    public void testBlockChainConfig() {
        Integer bd = 123;
        configService.setConfig("blockchain.settlement.batch.size", bd.toString());
        configService.reload();
        assertEquals(bd.intValue(), blockchainConfig.settlementBatchSize);
    }
    @Test
    public void testBlockChainConfig2() {
        BigInteger bd = new BigInteger("456");
        configService.setConfig("blockchain.settlement.nonce.check.interval", bd.toString());
        configService.reload();
        assertEquals(bd, blockchainConfig.settlementNonceCheckInterval);
    }

    private FakeCfx getBean() {
        return (FakeCfx) refreshScope.get(RefreshScope.BEAN_NAME_PREFIX + beanName, objectFactory);
    }

    @Test
    public void changeInterval() {
        changeInterval(999);
        changeInterval(9998);
    }
    public void changeInterval(long ms) {
        configService.setConfig("blockchain.cfx.intervalMillis", String.valueOf(ms));
        configService.reload();
        assertEquals(ms, configService.cfxIntervalMillis);
        assertEquals(ms, getBean().cfxIntervalMillis);
    }

    @Test
    public void changeRetry() {
        changeRetry(3);
        changeRetry(5);
    }
    public void changeRetry(int retry) {
        configService.setConfig("blockchain.cfx.retry", String.valueOf(retry));
        configService.reload();
        assertEquals(retry, configService.cfxRetry);
        assertEquals(retry, getBean().cfxRetry);
    }

    @Test
    public void changeUrl() {
        String url3 = "url3";
        changeUrl(url3);
        //
        changeUrl("url4");

    }

    private void changeUrl(String url) {
        configService.setConfig("blockchain.cfx.url", url);
        configService.reload();
        assertEquals(url, configService.cfxUrl);
        assertEquals(url, getBean().cfxUrl);
    }
}
