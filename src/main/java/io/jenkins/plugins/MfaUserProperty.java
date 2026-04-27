package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

public class MfaUserProperty extends UserProperty {
    private static final Logger LOGGER = Logger.getLogger(MfaUserProperty.class.getName());

    private final boolean userConfiguredMfa;
    private final Secret secretKey;
    private Secret backupCodesData;

    @DataBoundConstructor
    public MfaUserProperty(boolean mfaEnabled, String secretKey, String totpCode) throws FormException {
        if (mfaEnabled) {
            if (secretKey == null || secretKey.isEmpty()) {
                // Re-save with MFA already configured: preserve existing secret
                User currentUser = User.current();
                MfaUserProperty existing = currentUser != null
                        ? currentUser.getProperty(MfaUserProperty.class) : null;
                if (existing != null && existing.secretKey != null) {
                    this.userConfiguredMfa = true;
                    this.secretKey = existing.secretKey;
                    this.backupCodesData = existing.backupCodesData;
                    return;
                }
                throw new FormException(Messages.MfaUserProperty_secretKey_missing(), "secretKey");
            }
            if (totpCode == null || totpCode.isEmpty()) {
                throw new FormException(Messages.MfaUserProperty_totpCode_missing(), "totpCode");
            }
            if (!TOTPUtil.verifyCode(secretKey, totpCode)) {
                throw new FormException(Messages.MfaUserProperty_totpCode_invalid(), "totpCode");
            }
        }

        this.userConfiguredMfa = mfaEnabled;
        this.secretKey = (secretKey != null && !secretKey.isEmpty()) ? Secret.fromString(secretKey) : null;

        // Load backup codes from session if they were just generated
        if (mfaEnabled) {
            StaplerRequest currentReq = Stapler.getCurrentRequest();
            if (currentReq != null) {
                String pending = (String) currentReq.getSession()
                        .getAttribute(MfaConstants.MFA_PENDING_BACKUP_CODES_ATTR);
                if (pending != null && !pending.isEmpty()) {
                    this.backupCodesData = Secret.fromString(pending);
                    currentReq.getSession().removeAttribute(MfaConstants.MFA_PENDING_BACKUP_CODES_ATTR);
                }
            }
        }
    }

    public boolean isMfaEnabled() {
        MfaGlobalConfig globalConfig = MfaGlobalConfig.get();
        boolean globalEnforce = globalConfig != null && globalConfig.isEnforceMfaForAllUsers();
        return globalEnforce || (userConfiguredMfa && secretKey != null);
    }

    public Secret getSecretKey() {
        return secretKey;
    }

    public boolean hasBackupCodes() {
        return backupCodesData != null && !backupCodesData.getPlainText().isEmpty();
    }

    /**
     * Checks the submitted code against stored backup code hashes and consumes it if valid.
     * Caller must invoke {@code user.save()} afterwards to persist the change.
     */
    public boolean consumeBackupCode(String code) throws IOException {
        if (backupCodesData == null) return false;
        List<String> hashes = BackupCodeUtil.fromStorageString(backupCodesData.getPlainText());
        if (!BackupCodeUtil.consume(code, hashes)) return false;
        backupCodesData = hashes.isEmpty() ? null : Secret.fromString(BackupCodeUtil.toStorageString(hashes));
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public DescriptorImpl() {
            super(MfaUserProperty.class);
        }

        @Override
        public UserProperty newInstance(User user) {
            try {
                return new MfaUserProperty(false, null, null);
            } catch (FormException e) {
                LOGGER.log(Level.SEVERE, "Failed to create MfaUserProperty instance", e);
                return null;
            }
        }

        @Override
        public String getDisplayName() {
            return Messages.MfaUserProperty_displayName();
        }

        public FormValidation doCheckTotpCode(@QueryParameter String value, @QueryParameter String secretKey) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }
            if (secretKey == null || secretKey.isEmpty()) {
                return FormValidation.warning(Messages.MfaUserProperty_totpCode_warning());
            }
            boolean valid = TOTPUtil.verifyCode(secretKey, value);
            return valid
                    ? FormValidation.ok(Messages.MfaUserProperty_totpCode_valid())
                    : FormValidation.error(Messages.MfaUserProperty_totpCode_invalid());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
