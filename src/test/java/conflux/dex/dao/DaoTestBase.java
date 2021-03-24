package conflux.dex.dao;

import org.junit.Before;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

public class DaoTestBase<DAO> {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    DAO dao;

    @Before
    public void init() {
        // Use mocked jdbc template.
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);
    }
    @Autowired
    CacheManager cacheManager;
    @Autowired
    JdbcTemplate jdbcTemplate;
    protected void mockInsert(int autoGenId) {
        Mockito.when(jdbcTemplate.update(Mockito.any(PreparedStatementCreator.class),
                Mockito.any(GeneratedKeyHolder.class))
        ).then((Answer<Integer>) invocation -> {
            // Fill auto generated key
            KeyHolder argument = invocation.getArgument(autoGenId, GeneratedKeyHolder.class);
            Map<String, Object> map = new HashMap<>(autoGenId);
            map.put("", autoGenId);
            argument.getKeyList().add(map);
            // return modified/inserted row count
            return autoGenId;
        });
    }
}
