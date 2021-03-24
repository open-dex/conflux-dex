package conflux.dex.blockchain;

import conflux.dex.blockchain.log.DepositData;
import conflux.dex.blockchain.log.ScheduleWithdrawRequest;
import conflux.web3j.Cfx;
import conflux.web3j.Request;
import conflux.web3j.request.Epoch;
import conflux.web3j.response.BigIntResponse;
import conflux.web3j.response.Log;
import conflux.web3j.types.Address;
import conflux.web3j.types.AddressException;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventBlockchainTest {
    private EventBlockchain eventBlockchain() {
        Cfx cfx = EasyMock.mock(Cfx.class);
        Log log = EasyMock.mock(Log.class);

        Request<BigInteger, BigIntResponse> request = EasyMock.createMock(Request.class);
        Request<List<Log>, Log.Response> logRequest = EasyMock.createMock(Request.class);

        EasyMock.expect(cfx.getEpochNumber(Epoch.latestConfirmed())).andReturn(request).anyTimes();
        EasyMock.expect(cfx.getLogs(EasyMock.anyObject())).andReturn(logRequest).anyTimes();

        EasyMock.expect(request.sendAndGet()).andReturn(BigInteger.valueOf(40)).anyTimes();

        List<Log> logs = new ArrayList<>();
        logs.add(log);
        EasyMock.expect(logRequest.sendAndGet()).andReturn(logs).anyTimes();
        List<String> topics = new ArrayList<>();
        String str = "0x5548c837ab068cf56a2c2479df0882a4922fd203edb7517321831d95078c5f62";
        String str2 = "0x0ebe7b96b8d0566030cee68cd5153d3af3eb238d56092c9493a18e6d0b568369";
        topics.add(str);
        topics.add(str);
        topics.add(str2);

        EasyMock.expect(log.getAddress()).andReturn(new Address("cfx:type.contract:acg4k3nww68w5j0e6hy8bxuy6uvfnd27zjhe9aw1ju")).anyTimes();
        EasyMock.expect(log.getTransactionHash()).andReturn(Optional.of("dummy tx hash")).anyTimes();
        EasyMock.expect(log.getTopics()).andReturn(topics).anyTimes();
        EasyMock.expect(log.getData()).andReturn("30").anyTimes();

        EasyMock.replay(cfx, request, logRequest, log);

        return new EventBlockchain(cfx);
    }

    @Test
    public void testGetter() {
        EventBlockchain eventBlockchain = eventBlockchain();
        Assert.assertEquals(BigInteger.ONE, eventBlockchain.getPollEpochFrom());
        Assert.assertNotNull(eventBlockchain.getAddresses());

        eventBlockchain.setPollEpochFrom(BigInteger.TEN);
        Assert.assertEquals(BigInteger.TEN, eventBlockchain.getPollEpochFrom());
    }

    @Test(expected = AddressException.class)
    public void testGetLogs() {
        EventBlockchain eventBlockchain = eventBlockchain();
        eventBlockchain.addAddress("address1");
        List<DepositData> deposits = new ArrayList<>();
        List<ScheduleWithdrawRequest> schedule = new ArrayList<>();

        Assert.assertFalse(eventBlockchain.getLatestLogs(deposits, schedule));
    }
}
