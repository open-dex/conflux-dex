package conflux.dex.config;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * System API should be protected by some authentication.
 * Because there exist some api that using AdminRequest.validate, without @AuthRequire annotation,
 * we can't distinguish them at this point, so, just keep the request here.
 * @see AuthAspect
 */
@Component
@WebFilter()
public class AuthFilter implements Filter {
    private static ThreadLocal<HttpServletRequest> requestThreadLocal;
    public static final String LOGIN_URL = "/system/login";

    public static HttpServletRequest getRequest() {
        return requestThreadLocal.get();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        requestThreadLocal = new ThreadLocal<>();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response
            , FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        requestThreadLocal.set(req);
        try {
            chain.doFilter(request, response);
        } finally {
            requestThreadLocal.remove();
        }
    }
}
