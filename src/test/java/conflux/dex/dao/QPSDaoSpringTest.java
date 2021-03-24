package conflux.dex.dao;

import conflux.dex.config.qps.LimitedResource;
import conflux.dex.config.qps.QPSConfigService;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {QPSDaoSpringTest.class,QPSConfigService.class})
@Transactional
@Rollback
// It connects to real database, ignore in case  one doesn't have one.
// You can still run it manually.
//@Ignore
@SpringBootApplication
public class QPSDaoSpringTest {
    @Autowired
    QPSConfigService qpsConfigService;
    @Autowired
    QPSConfDao qpsConfDao;

    @Test
    public void crud() {
        LimitedResource conf = LimitedResource.buildResource("1.2.3.4");
        conf.rate = 1;
        conf = qpsConfigService.setRate(conf.key, conf.rate);
        Assert.assertNotNull("should set id", conf.id);
        conf = qpsConfigService.setRate(conf.key, 2);
        Assert.assertEquals("should change rate", 2, conf.rate, 0.0);
        //
        Collection<LimitedResource> list = qpsConfigService.list();
        Assert.assertTrue("should have at least one", list.size() > 0 );
        LimitedResource finalConf = conf;
        Assert.assertEquals("should have the one",
                list.stream().filter(s->s.key.equals(finalConf.key)).findFirst().get().rate, conf.rate, 0.0);
        int deleted = qpsConfigService.delete(conf);
        Assert.assertEquals("should delete one", deleted, 1);
    }
}
