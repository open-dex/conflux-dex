package conflux.dex.dao;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@TestConfiguration
@EnableCaching
public class SetupCacheStub {
    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    @Bean
    public JdbcTemplate jdbcTemplate() {
        return Mockito.mock(JdbcTemplate.class);
    }

    @Bean
    public DataSource dataSource() {
        return Mockito.mock(DataSource.class);
    }

    @Bean
    public AccountDao accountDao() {
        return new AccountDaoImpl();
    }

    @Bean
    public CurrencyDao currencyDao() {
        return new CurrencyDaoImpl();
    }

    @Bean
    public ProductDao productDao() {
        return new ProductDaoImpl();
    }
    @Bean
    public DailyLimitDao dailyLimitDaoImpl() {
        return new DailyLimitDaoImpl();
    }
    @Bean
    public DailyLimitRateDao dailyLimitRateDaoImpl() {
        return new DailyLimitRateDaoImpl();
    }


    @Bean("KeyGeneratorId2UserIdAndCurrency")
    public KeyGeneratorId2UserIdAndCurrency keyGenId2UserIdAndCurrency() {
        return new KeyGeneratorId2UserIdAndCurrency();
    }


    @Bean("KeyGeneratorId2UserId")
    public KeyGeneratorId2UserId keyGenId2UserId() {
        return new KeyGeneratorId2UserId();
    }
}
