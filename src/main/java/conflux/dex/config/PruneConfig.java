package conflux.dex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@ConfigurationProperties("prune.address")
public class PruneConfig {
    private List<String> marketmaker;
    private List<String> KLineRobots;

    public List<String> getMarketmaker() {
        return marketmaker;
    }

    public void setMarketmaker(List<String> marketmaker) {
        this.marketmaker = marketmaker;
    }

    public List<String> getKLineRobots() {
        return KLineRobots == null ? Collections.emptyList() : KLineRobots;
    }

    public void setKLineRobots(List<String> KLineRobots) {
        this.KLineRobots = KLineRobots;
    }
}
