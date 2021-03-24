package conflux.dex.config;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.controller.SystemController;
import conflux.dex.controller.request.SystemCommand;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * Only method that marked with @AuthRequire will trigger codes here.
 * Other (old) method should use AdminRequest.validate to check permission.
 * @see AuthFilter
 */
@Component
@Aspect
public class AuthAspect {
    private Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The header name for auth token, It's Base64 encoded in fact. it should be vague to prevent guessing.
     * @see SystemController#login(conflux.dex.controller.request.SystemCommand)
     */
    public static final String AUTH_HEADER = "HEX";

    @Value("${ui.admin.address}")
    private String adminAddress;

    @Pointcut("@annotation(conflux.dex.config.AuthRequire)")
    private void pointcut() {}

    @Before("pointcut() && @annotation(authRequire)")
    public void advice(AuthRequire authRequire) {
        if (!authRequire.enabled()) {
            return;
        }
        parseHeader();
    }
    public SystemCommand parseHeader() {
        HttpServletRequest request = AuthFilter.getRequest();
        if (request.getMethod().equals("OPTIONS")) {
            return null;
        }
        String remoteAddr = request.getRemoteAddr();
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || header.isEmpty()) {
            logger.debug("empty header, remote {}", remoteAddr);
            throw BusinessException.internalError("AuthRequire");
        }
        byte[] bytes;
        try {
            bytes = Base64Utils.decode(header.getBytes());
        } catch (Exception e) {
            logger.warn("invalid hex header, remote {}", remoteAddr, e);
            throw BusinessException.internalError("Invalid header format");
        }
        String str = new String(bytes);
        SystemCommand authInfo;
        try {
            authInfo = Utils.parseJson(str, SystemCommand.class);
        } catch (Exception e) {
            logger.warn("invalid auth str, parse json failed. {}, remote {}",
                    str, remoteAddr, e);
            throw BusinessException.internalError("Invalid header content");
        }
        authInfo.validateSign(this.adminAddress);
        // fix time expiration.
        SystemCommand command = authInfo;
        Date dt = new Date(command.timestamp);
        ZonedDateTime instant = dt.toInstant().atZone(ZoneOffset.ofHours(8));
        int hour = instant.getHour();//[0, 23]
        // expired in 3 AM.
        if (hour <= 3) {
            instant = instant.plusDays(1);
        } else {
            instant = instant.plusHours(24 - (hour - 3));
        }
        instant = instant.withHour(3).withMinute(0).withSecond(0).withNano(0);
        command.timestamp = instant.toEpochSecond()*1000;
        // compare
        if (authInfo.timestamp < System.currentTimeMillis()) {
            logger.debug("header expired, {}, remote {}", authInfo.timestamp, remoteAddr);
            throw BusinessException.validateFailed("Token expired.");
        }
        return authInfo;
    }
}
