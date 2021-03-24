package conflux.dex.config;

import org.springframework.context.annotation.Configuration;

import com.codahale.metrics.MetricRegistry;
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurerAdapter;

import conflux.dex.common.Metrics;

@Configuration
@EnableMetrics
public class MetricsConfig extends MetricsConfigurerAdapter {
	
	@Override
	public MetricRegistry getMetricRegistry() {
		return Metrics.DefaultRegistry;
	}

}
