package conflux.dex.service.blockchain;

import conflux.dex.blockchain.crypto.Domain;
import conflux.web3j.Cfx;
import conflux.web3j.Request;
import conflux.web3j.response.BigIntResponse;
import conflux.web3j.types.Address;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class HeartBeatServiceTest {
    private HeartBeatService heartBeatService = heartBeatService();

    private HeartBeatService heartBeatService() {
        Cfx cfx = EasyMock.createMock(Cfx.class);
        Domain.defaultChainId = Address.NETWORK_ID_TESTNET;
        Request<BigInteger, BigIntResponse> request = EasyMock.createMock(Request.class);
        EasyMock.expect(cfx.getEpochNumber()).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getBalance(EasyMock.anyObject())).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getNonce(EasyMock.anyObject(Address.class))).andReturn(request).anyTimes();
        EasyMock.replay(cfx);
        EasyMock.expect(request.sendAndGet(0, 0L)).andReturn(BigInteger.valueOf(2)).anyTimes();
        EasyMock.expect(request.sendAndGet()).andReturn(BigInteger.ONE).anyTimes();
        EasyMock.replay(request);
        return new HeartBeatService(cfx);
    }

    @Test
    public void test() {
//        Assert.assertFalse(heartBeatService.getValue());
        Assert.assertEquals(BigInteger.ONE, heartBeatService.getCurrentEpoch());
        heartBeatService.pollEpoch();
    }
}
