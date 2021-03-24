package conflux.dex.config;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Scope(ConfigRefresh.SCOPE_NAME)
@Documented
public @interface ConfigRefresh {
    String SCOPE_NAME = "RefreshScope";
    ScopedProxyMode proxyMode() default ScopedProxyMode.TARGET_CLASS;
}
