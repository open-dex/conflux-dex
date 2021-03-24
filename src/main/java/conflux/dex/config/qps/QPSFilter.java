package conflux.dex.config.qps;

import com.google.common.util.concurrent.RateLimiter;
import conflux.dex.common.Utils;
import conflux.dex.controller.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@WebFilter(urlPatterns = "/*")
public class QPSFilter implements Filter {
    Logger log = LoggerFactory.getLogger(getClass());
    private ConcurrentHashMap<String, RateLimiter> limiterMap = new ConcurrentHashMap<>();

    private QPSConfigService qpsConfigService;

    @Autowired
    public void setQpsConfigService(QPSConfigService svc) {
        this.qpsConfigService = svc;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String msg = check(req);
        if (msg.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        res.addHeader("Content-Type", "application/json");
        res.getWriter().write(Utils.toJson(Response.failure(msg, null)));
    }

    private String check(HttpServletRequest req) {
        String url = req.getRequestURI();
        if (url.startsWith("/system/")) {
            // do not restrict system request from admin.
            return "";
        }
        String ip;
        ip = req.getHeader("X-Real-IP");
        String msg = check(ip, url);
        if (msg.isEmpty()) {
            ip = req.getRemoteAddr();
            msg = check(ip, url);
        }
        return msg;
    }
    private String check(String ip, String url){
        if (ip == null) {
            return "";
        }
        String key = ip + "#" + url;
        String msg;
        LimitedResource confByIpUrl = qpsConfigService.getConf(key);
        LimitedResource confByIp = qpsConfigService.getConf(ip);
        if (qpsConfigService.isWhitelistMode()
            && confByIpUrl == null && confByIp == null) {
            return "Access denied.";
        }
        msg = checkConf(confByIpUrl);
        if (msg.isEmpty()) {
            msg = checkConf(confByIp);
        }
        return msg;
    }

    private String checkConf(LimitedResource conf) {
        if (conf == null) {
            return "";
        }
        if (conf.rate == 0) {
            // un-limit, whitelist
            return "";
        }
        if (conf.rate < 0) {
            // blacklist
            if (conf.banTimes < 100) {
                // limit log frequency.
                log.warn("hit blacklist, key {} banTimes {}", conf.key, conf.banTimes);
            }
            conf.banTimes += 1;
            return"Bad Request";
        }
        RateLimiter rateLimiter = limiterMap.computeIfAbsent(conf.key,
                key -> RateLimiter.create(conf.rate));
        if (conf.rate != rateLimiter.getRate()) {
            rateLimiter.setRate(conf.rate);
        }
        boolean got = rateLimiter.tryAcquire();
        if (!got) {
            if (conf.overRateTimes < 100) {
                log.warn("QPS over limit, key {} overRateTimes {}", conf.key, conf.overRateTimes);
            }
            conf.overRateTimes += 1;
            return "QPS over limit";
        }
        return "";
    }

}
