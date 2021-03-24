package conflux.dex.controller.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import conflux.dex.common.Metrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Rest controller for {@link MetricsServlet}
 * @ignore
 */
@RestController
@RequestMapping("/system/metrics-rest")
public class MetricsController {
    @GetMapping("/names")
    public SortedSet<String> getNames() {
        return Metrics.DefaultRegistry.getNames();
    }

    @GetMapping("/metrics")
    public Map<String, Metric> getMetrics() {
        return Metrics.DefaultRegistry.getMetrics();
    }

    @GetMapping("/counters")
    public SortedMap<String, Counter> getCounters() {
        return Metrics.DefaultRegistry.getCounters();
    }

    @SuppressWarnings("rawtypes")
	@GetMapping("/gauges")
    public SortedMap<String, Gauge> getGauges() {
        return Metrics.DefaultRegistry.getGauges();
    }

    @GetMapping("/histograms")
    public SortedMap<String, Histogram> getHistograms() {
        return Metrics.DefaultRegistry.getHistograms();
    }

    @GetMapping("/meters")
    public SortedMap<String, Meter> getMeters() {
        return Metrics.DefaultRegistry.getMeters();
    }

    @GetMapping("/timers")
    public SortedMap<String, Timer> getTimer() {
        return Metrics.DefaultRegistry.getTimers();
    }
}
