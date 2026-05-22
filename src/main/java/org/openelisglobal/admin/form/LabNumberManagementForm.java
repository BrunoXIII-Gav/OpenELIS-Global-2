package org.openelisglobal.admin.form;

import org.hibernate.validator.constraints.Length;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.validation.annotations.SafeHtml;

public class LabNumberManagementForm {

    private AccessionFormat labNumberType;

    private Boolean usePrefix;

    @Length(max = 5, min = 0)
    @SafeHtml
    private String alphanumPrefix;

    @Length(max = 20, min = 0)
    @SafeHtml
    private String sitePrefix;

    public AccessionFormat getLabNumberType() {
        return labNumberType;
    }

    public void setLabNumberType(AccessionFormat labNumberType) {
        this.labNumberType = labNumberType;
    }

    public Boolean getUsePrefix() {
        return usePrefix;
    }

    public void setUsePrefix(Boolean usePrefix) {
        this.usePrefix = usePrefix;
    }

    public String getAlphanumPrefix() {
        return alphanumPrefix;
    }

    public void setAlphanumPrefix(String alphanumPrefix) {
        this.alphanumPrefix = alphanumPrefix;
    }

    public String getSitePrefix() {
        return sitePrefix;
    }

    public void setSitePrefix(String sitePrefix) {
        this.sitePrefix = sitePrefix;
    }
}
