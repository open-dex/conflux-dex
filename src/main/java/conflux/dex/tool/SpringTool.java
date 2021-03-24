package conflux.dex.tool;

import conflux.dex.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringTool {

    private static ApplicationContext context;

    @Autowired
    public void setContext(ApplicationContext appContext) {
        context = appContext;
    }


    public static <T> T getBean(Class<T> clz) {
        if (clz == null) {
            throw BusinessException.internalError("Application Context not initialized.");
        }
        return context.getBean(clz);
    }
}
