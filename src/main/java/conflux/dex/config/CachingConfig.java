package conflux.dex.config;

import java.lang.reflect.Method;

import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CachingConfig extends CachingConfigurerSupport {
	
	private static class NameKeyGenerator extends SimpleKeyGenerator {
		
		@Override
		public Object generate(Object target, Method method, Object... params) {
			// case-insensitive for single string parameter, e.g. entity name, address
			if (params.length == 1 && params[0] instanceof String) {
				return ((String) params[0]).toLowerCase();
			}
			
			return super.generate(target, method, params);
		}
		
	}
	
	@Override
	public KeyGenerator keyGenerator() {
		return new NameKeyGenerator();
	}

}
