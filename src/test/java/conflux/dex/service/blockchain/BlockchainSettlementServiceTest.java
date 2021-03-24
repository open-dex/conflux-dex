package conflux.dex.service.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.service.FeeService;
import conflux.web3j.types.Address;
import org.easymock.EasyMock;
import org.junit.Test;

import conflux.dex.blockchain.OrderBlockchain;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Trade;
import conflux.dex.service.HealthService;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.web3j.Cfx;
import conflux.web3j.Request;
import conflux.web3j.response.BigIntResponse;

public class BlockchainSettlementServiceTest {
    private ExecutorService executor;
    private Settleable data;
    private TestDexDao dao;
    private HeartBeatService heartBeatService;
    private BlockchainSettlementService service = blockchainSettlementService();

    public BlockchainSettlementServiceTest() throws Exception {
    }

    private BlockchainSettlementService blockchainSettlementService() throws Exception {
        this.executor = Executors.newCachedThreadPool();
        this.dao = new TestDexDao();
        Cfx cfx = EasyMock.createMock(Cfx.class);
        Domain.defaultChainId = Address.NETWORK_ID_TESTNET;
        BigInteger mockId = BigInteger.valueOf(Address.NETWORK_ID_TESTNET);
        EasyMock.expect(cfx.getNetworkId()).andReturn(mockId).anyTimes();
        EasyMock.expect(cfx.getIntNetworkId()).andReturn(1).anyTimes();
        Request<BigInteger, BigIntResponse> request = EasyMock.createMock(Request.class);

        Trade trade = new Trade(this.dao.product.getId(), 2, 1, BigDecimal.valueOf(1), BigDecimal.valueOf(10), OrderSide.Buy, BigDecimal.ZERO, BigDecimal.ZERO);
        trade.setCreateTime(Timestamp.from(Instant.parse("2019-12-10T09:30:00Z")));
        this.data = EasyMock.mock(Settleable.class);
        EasyMock.expect(data.getSettledTxHash()).andReturn("").anyTimes();
        Settleable.Context context = new Settleable.Context("0x8311ff0cad62f8d4810786d31af066de23b6fa3a", "data-1", BigInteger.ONE, BigInteger.ONE);
        EasyMock.createMock(Settleable.class);
        context.ignored = false;
        EasyMock.expect(data.getSettlementContext(dao.get())).andReturn(context).anyTimes();
        EasyMock.expect(data.size()).andReturn(1).once();
        EasyMock.replay(data);

        EasyMock.expect(cfx.getEpochNumber()).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getBalance(EasyMock.anyObject())).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getNonce(EasyMock.anyObject(Address.class))).andReturn(request).anyTimes();
        EasyMock.replay(cfx);
        EasyMock.expect(request.sendAndGet()).andReturn(BigInteger.ONE).anyTimes();
        EasyMock.replay(request);

        this.heartBeatService = EasyMock.createMock(HeartBeatService.class);
        EasyMock.expect(heartBeatService.getCurrentEpoch()).andReturn(BigInteger.TEN).anyTimes();
        EasyMock.replay(heartBeatService);

        String adminAddress = "0x14d45d87593eb39da895f4d78bac9c2e094d90b6";
        String adminPrivateKey = "0xc57bbb9be823ab148911ec6bcae459d0090afff7fb86d286e5947d3614a023a1";
        OrderBlockchain blockchain = new OrderBlockchain(cfx, adminAddress, adminPrivateKey);
        TransactionConfirmationMonitor monitor = new TransactionConfirmationMonitor(cfx, dao.get(), heartBeatService);
        FeeService feeService = new FeeService(null, null){
            @Override
            public void reload() {

            }
        };
        BlockchainSettlementService settlementService =
                new BlockchainSettlementService(executor, dao.get(), blockchain, feeService, monitor, new BlockchainConfig());
        return settlementService;
    }

    @Test
    public void testSetService() {
        HealthService healthService = new HealthService(dao.get());
        service.setHealthService(healthService);
        service.setHeartBeatService(heartBeatService);
        service.submit(data, true);
    }

//    @Test
//    public void testSubmit() {
//        service.submit(data);
//    }

}
