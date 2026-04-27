package io.jenkins.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaFilterTest {

    private MfaFilter filter;

    @Mock
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        filter = new MfaFilter();
    }

    // --- isExcludedPath ---

    @Test
    void isExcludedPath_staticResource() {
        assertTrue(filter.isExcludedPath("/jenkins/static/abc.js", "/jenkins"));
    }

    @Test
    void isExcludedPath_adjunct() {
        assertTrue(filter.isExcludedPath("/jenkins/adjuncts/xyz.js", "/jenkins"));
    }

    @Test
    void isExcludedPath_mfaVerify() {
        assertTrue(filter.isExcludedPath("/jenkins/mfa-verify", "/jenkins"));
        assertTrue(filter.isExcludedPath("/jenkins/mfa-verify/verify", "/jenkins"));
    }

    @Test
    void isExcludedPath_loginPage() {
        assertTrue(filter.isExcludedPath("/jenkins/login", "/jenkins"));
    }

    @Test
    void isExcludedPath_signupPage() {
        assertTrue(filter.isExcludedPath("/jenkins/signup", "/jenkins"));
    }

    @Test
    void isExcludedPath_favicon() {
        assertTrue(filter.isExcludedPath("/jenkins/favicon.ico", "/jenkins"));
    }

    @Test
    void isExcludedPath_regularPage_notExcluded() {
        assertFalse(filter.isExcludedPath("/jenkins/job/my-job", "/jenkins"));
    }

    @Test
    void isExcludedPath_dashboard_notExcluded() {
        assertFalse(filter.isExcludedPath("/jenkins/", "/jenkins"));
    }

    @Test
    void isExcludedPath_emptyContextPath() {
        assertTrue(filter.isExcludedPath("/static/abc.js", ""));
        assertFalse(filter.isExcludedPath("/job/my-job", ""));
    }

    // --- isApiTokenRequest ---

    @Test
    void isApiTokenRequest_apiPathWithAuth_returnsTrue() {
        when(req.getHeader("Authorization")).thenReturn("Basic dXNlcjp0b2tlbg==");
        when(req.getRequestURI()).thenReturn("/jenkins/api/json");
        when(req.getContextPath()).thenReturn("/jenkins");
        assertTrue(filter.isApiTokenRequest(req));
    }

    @Test
    void isApiTokenRequest_apiPathWithBearerToken_returnsTrue() {
        when(req.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJSUzI1NiJ9...");
        when(req.getRequestURI()).thenReturn("/jenkins/api/json");
        when(req.getContextPath()).thenReturn("/jenkins");
        assertTrue(filter.isApiTokenRequest(req));
    }

    @Test
    void isApiTokenRequest_nonApiPathWithAuth_returnsFalse() {
        when(req.getHeader("Authorization")).thenReturn("Basic dXNlcjp0b2tlbg==");
        when(req.getRequestURI()).thenReturn("/jenkins/job/my-job");
        when(req.getContextPath()).thenReturn("/jenkins");
        assertFalse(filter.isApiTokenRequest(req));
    }

    @Test
    void isApiTokenRequest_noAuthHeader_returnsFalse() {
        when(req.getHeader("Authorization")).thenReturn(null);
        assertFalse(filter.isApiTokenRequest(req));
    }

    @Test
    void isApiTokenRequest_bearerOnNonApiPath_returnsFalse() {
        when(req.getHeader("Authorization")).thenReturn("Bearer sometoken");
        when(req.getRequestURI()).thenReturn("/jenkins/job/build");
        when(req.getContextPath()).thenReturn("/jenkins");
        assertFalse(filter.isApiTokenRequest(req));
    }
}
