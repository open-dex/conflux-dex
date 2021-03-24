package conflux.dex.service.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import conflux.dex.blockchain.log.ScheduleWithdrawRequest;
import conflux.dex.common.channel.Channel;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.Currency;
import conflux.dex.service.OrderService;
import conflux.dex.ws.topic.AccountTopic;

public class WithdrawEventHandlerTest {
    private ScheduleWithdrawRequest newScheduleData(Currency currency, String txHash, String sender, BigInteger time) {
        return new ScheduleWithdrawRequest(currency.getContractAddress(), txHash, sender, time);
    }

    @Test
    public void testItem() {
        TestDexDao dao = new TestDexDao();
        Channel<Object> channel = Channel.create();
        AccountTopic accountTopic = new AccountTopic((dao.get()));
        OrderService orderService = new OrderService(dao.get(), channel, accountTopic);
        // user "alice" withdraw 123 tokens
        List<ScheduleWithdrawRequest> schedule = Arrays.asList(
                newScheduleData(dao.cat, "tx1", dao.alice.getName(), BigInteger.ONE),
                newScheduleData(dao.cat, "tx2", dao.alice.getName(), BigInteger.ZERO)
        );
        WithdrawEventHandler handler = new WithdrawEventHandler(schedule, dao.get(), orderService);
        handler.publish(accountTopic, dao.get());
//        handler.handle(dao.get());
    }

    @Test
    public void testNoCurrency() {
        TestDexDao dao = new TestDexDao();
        Channel<Object> channel = Channel.create();
        OrderService orderService = new OrderService(dao.get(), channel, new AccountTopic(dao.get()));
        Currency pig = new Currency("pig", "0x80a62515e736c8823f0b47345855f5be80e893b6", "0x872f24f5fbabea1d652e4c5e2e86349120d0a4dc", 2);
        List<ScheduleWithdrawRequest> schedule = Arrays.asList(
                newScheduleData(pig, "tx2", dao.alice.getName(), BigInteger.ZERO)
        );
        new WithdrawEventHandler(schedule, dao.get(), orderService);
    }
}
