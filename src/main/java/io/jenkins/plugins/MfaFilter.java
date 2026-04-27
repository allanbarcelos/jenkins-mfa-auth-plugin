package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.User;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

@Extension
public class MfaFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(MfaFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;

        rsp.setHeader("X-Frame-Options", "DENY");
        rsp.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
        rsp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");

        String path = req.getRequestURI();
        String contextPath = req.getContextPath();

        if (Jenkins.getInstanceOrNull() == null || isExcludedPath(path, contextPath)) {
            chain.doFilter(request, response);
            return;
        }

        User user = User.current();
        MfaGlobalConfig globalConfig = GlobalConfiguration.all().get(MfaGlobalConfig.class);

        if (user == null || globalConfig == null) {
            chain.doFilter(request, response);
            return;
        }

        if (globalConfig.isExcludeApiTokens() && isApiTokenRequest(req)) {
            chain.doFilter(request, response);
            return;
        }

        MfaUserProperty mfa = user.getProperty(MfaUserProperty.class);
        boolean mfaRequired = (mfa != null && mfa.isMfaEnabled()) || globalConfig.isEnforceMfaForAllUsers();

        if (mfaRequired) {
            HttpSession session = req.getSession(false);
            boolean verified = session != null
                    && Boolean.TRUE.equals(session.getAttribute(MfaConstants.MFA_VERIFIED_ATTR));

            if (verified) {
                verified = !isSessionExpired(session, globalConfig);
            }

            if (!verified) {
                LOGGER.log(Level.INFO, "MFA required for user {0}", user.getId());
                rsp.sendRedirect(contextPath + "/mfa-verify");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isSessionExpired(HttpSession session, MfaGlobalConfig globalConfig) {
        int timeoutMinutes = globalConfig.getMfaSessionTimeoutMinutes();
        if (timeoutMinutes <= 0) return false;
        Long verifiedAt = (Long) session.getAttribute(MfaConstants.MFA_VERIFIED_AT_ATTR);
        if (verifiedAt == null) return false;
        long elapsedMs = System.currentTimeMillis() - verifiedAt;
        if (elapsedMs > (long) timeoutMinutes * 60_000L) {
            session.removeAttribute(MfaConstants.MFA_VERIFIED_ATTR);
            session.removeAttribute(MfaConstants.MFA_VERIFIED_AT_ATTR);
            return true;
        }
        return false;
    }

    // Package-private for testing
    boolean isApiTokenRequest(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null) return false;
        // Exclude programmatic API calls authenticated via any scheme on /api/ paths.
        // Jenkins API tokens use HTTP Basic auth; some integrations use Bearer tokens.
        return req.getRequestURI().startsWith(req.getContextPath() + "/api/");
    }

    // Package-private for testing
    boolean isExcludedPath(String path, String contextPath) {
        return path.startsWith(contextPath + "/static/")
                || path.startsWith(contextPath + "/adjuncts/")
                || path.startsWith(contextPath + "/mfa-verify")
                || path.startsWith(contextPath + "/login")
                || path.startsWith(contextPath + "/signup")
                || path.startsWith(contextPath + "/error")
                || path.startsWith(contextPath + "/securityRealm")
                || path.startsWith(contextPath + "/favicon.ico");
    }
}
