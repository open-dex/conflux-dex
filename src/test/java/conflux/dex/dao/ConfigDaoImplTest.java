package conflux.dex.dao;

import org.junit.Test;

public class ConfigDaoImplTest {
    private final ConfigDaoImpl configDao = new ConfigDaoImpl();

    @Test(expected = NullPointerException.class)
    public void testSetConfig() {
        configDao.setConfig("name", "val");
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfig() {
        configDao.getConfig("name");
    }
}
