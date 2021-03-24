package conflux.dex.config;

import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class AutoConfigBase {
    protected ConfigService configService;

    @Autowired
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }
    @PostConstruct
    public void bind2configService() {
        configService.resolveConfig(this);
    }
}
