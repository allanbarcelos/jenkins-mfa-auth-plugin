package io.jenkins.plugins;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
public class MfaGlobalConfig extends GlobalConfiguration {

    private boolean enforceMfaForAllUsers;
    private boolean excludeApiTokens;
    /**
     * Nullable: null means "never set by user", treated as the default (480 min = 8 h).
     * Using Integer so XStream can leave it null when the field was absent in old XML,
     * distinguishing that from an explicit user choice of 0 (no expiry).
     */
    private Integer mfaSessionTimeoutMinutes;

    public static MfaGlobalConfig get() {
        return GlobalConfiguration.all().get(MfaGlobalConfig.class);
    }

    public MfaGlobalConfig() {
        load();
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public boolean isEnforceMfaForAllUsers() {
        return enforceMfaForAllUsers;
    }

    @DataBoundSetter
    public void setEnforceMfaForAllUsers(boolean enforceMfaForAllUsers) {
        this.enforceMfaForAllUsers = enforceMfaForAllUsers;
        save();
    }

    public boolean isExcludeApiTokens() {
        return excludeApiTokens;
    }

    @DataBoundSetter
    public void setExcludeApiTokens(boolean excludeApiTokens) {
        this.excludeApiTokens = excludeApiTokens;
        save();
    }

    /** Returns the configured session timeout in minutes, defaulting to 480 (8 hours). */
    public int getMfaSessionTimeoutMinutes() {
        return mfaSessionTimeoutMinutes != null ? mfaSessionTimeoutMinutes : 480;
    }

    @DataBoundSetter
    public void setMfaSessionTimeoutMinutes(int mfaSessionTimeoutMinutes) {
        this.mfaSessionTimeoutMinutes = Math.max(0, mfaSessionTimeoutMinutes);
        save();
    }
}
