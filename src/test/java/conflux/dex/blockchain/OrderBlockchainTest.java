package conflux.dex.blockchain;

import java.math.BigInteger;

import conflux.dex.blockchain.crypto.Domain;
import conflux.web3j.types.Address;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import conflux.dex.service.blockchain.HeartBeatService;
import conflux.web3j.Cfx;
import conflux.web3j.Request;
import conflux.web3j.response.BigIntResponse;
import conflux.web3j.types.SendTransactionResult;

public class OrderBlockchainTest {
    private HeartBeatService heartBeatService;

    private OrderBlockchain init() throws Exception{
        Cfx cfx = EasyMock.createMock(Cfx.class);
        Domain.defaultChainId = Address.NETWORK_ID_TESTNET;
        BigInteger mockId = BigInteger.valueOf(Address.NETWORK_ID_TESTNET);
        Request<BigInteger, BigIntResponse> request = EasyMock.createMock(Request.class);
        SendTransactionResult res = new SendTransactionResult("tx");

        EasyMock.expect(cfx.getEpochNumber()).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getNetworkId()).andReturn(mockId).anyTimes();
        EasyMock.expect(cfx.getIntNetworkId()).andReturn(1).anyTimes();
        EasyMock.expect(cfx.getBalance(EasyMock.anyObject())).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getNonce(EasyMock.anyObject(Address.class))).andReturn(request).anyTimes();
        EasyMock.expect(cfx.sendRawTransactionAndGet(EasyMock.anyString())).andReturn(res);

        EasyMock.expect(request.sendAndGet()).andReturn(BigInteger.ONE).anyTimes();
        
        EasyMock.replay(cfx, request);

        this.heartBeatService = EasyMock.createMock(HeartBeatService.class);
        EasyMock.expect(heartBeatService.getCurrentEpoch()).andReturn(BigInteger.TEN).anyTimes();
        EasyMock.replay(heartBeatService);

        String adminAddress = "0x14d45d87593eb39da895f4d78bac9c2e094d90b6";
        String adminPrivateKey = "0xc57bbb9be823ab148911ec6bcae459d0090afff7fb86d286e5947d3614a023a1";
        return new OrderBlockchain(cfx, adminAddress, adminPrivateKey);
    }

    @Test
    public void testGetAdmin() throws Exception{
        OrderBlockchain orderBlockchain = init();
        Assert.assertNotNull(orderBlockchain.getAdmin());
    }

}
