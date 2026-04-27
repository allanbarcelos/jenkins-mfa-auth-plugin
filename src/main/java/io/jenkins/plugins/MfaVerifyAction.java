package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.User;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public class MfaVerifyAction implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(MfaVerifyAction.class.getName());
    private static final Logger AUDIT = Logger.getLogger("io.jenkins.plugins.mfa.audit");

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "MFA Verify";
    }

    @Override
    public String getUrlName() {
        return "mfa-verify";
    }

    public void doVerify(StaplerRequest req, StaplerResponse rsp) throws IOException {
        User u = User.current();
        if (u == null) {
            rsp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        if (MfaRateLimiter.isLocked(u.getId())) {
            AUDIT.log(Level.WARNING, "MFA_LOCKED user={0} ip={1}",
                    new Object[]{u.getId(), req.getRemoteAddr()});
            rsp.sendRedirect(req.getContextPath() + "/mfa-verify?error=locked");
            return;
        }

        MfaUserProperty mfa = u.getProperty(MfaUserProperty.class);
        if (mfa != null && mfa.isMfaEnabled()) {
            String code = req.getParameter("totpCode");
            boolean verified = false;

            if (BackupCodeUtil.isBackupCode(code)) {
                try {
                    verified = mfa.consumeBackupCode(code);
                    if (verified) {
                        u.save();
                        AUDIT.log(Level.INFO, "MFA_BACKUP_CODE_USED user={0} ip={1}",
                                new Object[]{u.getId(), req.getRemoteAddr()});
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to save user after backup code consumption", e);
                }
            } else {
                verified = TOTPUtil.verifyCode(mfa.getSecretKey(), code);
            }

            if (verified) {
                MfaRateLimiter.reset(u.getId());
                req.getSession().setAttribute(MfaConstants.MFA_VERIFIED_ATTR, true);
                req.getSession().setAttribute(MfaConstants.MFA_VERIFIED_AT_ATTR, System.currentTimeMillis());
                AUDIT.log(Level.INFO, "MFA_SUCCESS user={0} ip={1}",
                        new Object[]{u.getId(), req.getRemoteAddr()});
                rsp.sendRedirect(req.getContextPath() + "/");
            } else {
                MfaRateLimiter.recordFailure(u.getId());
                AUDIT.log(Level.WARNING, "MFA_FAILURE user={0} ip={1} attempts={2}",
                        new Object[]{u.getId(), req.getRemoteAddr(), MfaRateLimiter.getAttemptCount(u.getId())});
                rsp.sendRedirect(req.getContextPath() + "/mfa-verify?error=1");
            }
            return;
        }

        rsp.sendRedirect(req.getContextPath() + "/");
    }
}
