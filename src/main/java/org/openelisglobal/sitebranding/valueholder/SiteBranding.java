package org.openelisglobal.sitebranding.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.openelisglobal.common.valueholder.BaseObject;

/**
 * SiteBranding entity - Represents the organization's branding configuration
 * Single record per OpenELIS deployment
 *
 * Task Reference: T011
 */
@Entity
@Table(name = "site_branding")
public class SiteBranding extends BaseObject<Integer> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "site_branding_generator")
    @SequenceGenerator(name = "site_branding_generator", sequenceName = "site_branding_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "header_logo_path", length = 500)
    private String headerLogoPath;

    @Column(name = "login_logo_path", length = 500)
    private String loginLogoPath;

    @Column(name = "use_header_logo_for_login", nullable = false)
    private Boolean useHeaderLogoForLogin = false;

    @Column(name = "favicon_path", length = 500)
    private String faviconPath;

    /** Header bar background color - defaults to OpenELIS brand color */
    @Column(name = "header_color", length = 50, nullable = false)
    private String headerColor = "#295785";

    /**
     * Primary interactive color for buttons, links, focus states - defaults to
     * Carbon interactive-01
     */
    @Column(name = "primary_color", length = 50, nullable = false)
    private String primaryColor = "#0f62fe";

    /** Secondary color for secondary buttons - defaults to Carbon interactive-02 */
    @Column(name = "secondary_color", length = 50, nullable = false)
    private String secondaryColor = "#393939";

    @Column(name = "color_mode", length = 10, nullable = false)
    private String colorMode = "light";

    /** Show legal/security notice on login page */
    @Column(name = "show_login_notice", nullable = false)
    private Boolean showLoginNotice = true;

    /** Show facility name banner text in header */
    @Column(name = "show_header_banner_text", nullable = false)
    private Boolean showHeaderBannerText = true;

    /** Show release/version text in header */
    @Column(name = "show_header_version", nullable = false)
    private Boolean showHeaderVersion = true;

    /** Show search icon/action in header */
    @Column(name = "show_header_search_icon", nullable = false)
    private Boolean showHeaderSearchIcon = true;

    /** Show notifications icon/action in header */
    @Column(name = "show_header_notification_icon", nullable = false)
    private Boolean showHeaderNotificationIcon = true;

    /** Show help icon/action in header */
    @Column(name = "show_header_help_icon", nullable = false)
    private Boolean showHeaderHelpIcon = true;

    // Override BaseObject's @Transient sysUserId to map to actual database column
    @Column(name = "sys_user_id", length = 255, nullable = false)
    private String sysUserId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public String getHeaderLogoPath() {
        return headerLogoPath;
    }

    public void setHeaderLogoPath(String headerLogoPath) {
        this.headerLogoPath = headerLogoPath;
    }

    public String getLoginLogoPath() {
        return loginLogoPath;
    }

    public void setLoginLogoPath(String loginLogoPath) {
        this.loginLogoPath = loginLogoPath;
    }

    public Boolean getUseHeaderLogoForLogin() {
        return useHeaderLogoForLogin;
    }

    public void setUseHeaderLogoForLogin(Boolean useHeaderLogoForLogin) {
        this.useHeaderLogoForLogin = useHeaderLogoForLogin;
    }

    public String getFaviconPath() {
        return faviconPath;
    }

    public void setFaviconPath(String faviconPath) {
        this.faviconPath = faviconPath;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getHeaderColor() {
        return headerColor;
    }

    public void setHeaderColor(String headerColor) {
        this.headerColor = headerColor;
    }

    public String getColorMode() {
        return colorMode;
    }

    public void setColorMode(String colorMode) {
        this.colorMode = colorMode;
    }

    public Boolean getShowLoginNotice() {
        return showLoginNotice;
    }

    public void setShowLoginNotice(Boolean showLoginNotice) {
        this.showLoginNotice = showLoginNotice;
    }

    public Boolean getShowHeaderBannerText() {
        return showHeaderBannerText;
    }

    public void setShowHeaderBannerText(Boolean showHeaderBannerText) {
        this.showHeaderBannerText = showHeaderBannerText;
    }

    public Boolean getShowHeaderVersion() {
        return showHeaderVersion;
    }

    public void setShowHeaderVersion(Boolean showHeaderVersion) {
        this.showHeaderVersion = showHeaderVersion;
    }

    public Boolean getShowHeaderSearchIcon() {
        return showHeaderSearchIcon;
    }

    public void setShowHeaderSearchIcon(Boolean showHeaderSearchIcon) {
        this.showHeaderSearchIcon = showHeaderSearchIcon;
    }

    public Boolean getShowHeaderNotificationIcon() {
        return showHeaderNotificationIcon;
    }

    public void setShowHeaderNotificationIcon(Boolean showHeaderNotificationIcon) {
        this.showHeaderNotificationIcon = showHeaderNotificationIcon;
    }

    public Boolean getShowHeaderHelpIcon() {
        return showHeaderHelpIcon;
    }

    public void setShowHeaderHelpIcon(Boolean showHeaderHelpIcon) {
        this.showHeaderHelpIcon = showHeaderHelpIcon;
    }

    // Override BaseObject's sysUserId methods to use the mapped field
    @Override
    public String getSysUserId() {
        return sysUserId;
    }

    @Override
    public void setSysUserId(String sysUserId) {
        this.sysUserId = sysUserId;
    }
}
