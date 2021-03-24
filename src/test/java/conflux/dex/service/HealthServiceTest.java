package conflux.dex.service;

import org.junit.Assert;
import org.junit.Test;

import conflux.dex.dao.TestDexDao;
import conflux.dex.service.HealthService.PauseSource;

public class HealthServiceTest {
    private HealthService healthService;

    @Test
    public void testSetPause() {
        healthService = new HealthService(new TestDexDao().get());
        healthService.pause(PauseSource.Manual, "dummy error");
        Assert.assertTrue(healthService.getPauseSource().get() == PauseSource.Manual);
    }
}
