package org.openelisglobal.provider.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.AttributeOverride;
import org.hibernate.annotations.DynamicUpdate;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "provider_profile_field_value")
@AttributeOverride(name = "lastupdated", column = @Column(name = "lastupdated"))
@DynamicUpdate
public class ProviderProfileFieldValue extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "provider_profile_field_value_seq")
    @SequenceGenerator(name = "provider_profile_field_value_seq", sequenceName = "provider_profile_field_value_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "provider_id", nullable = false)
    private Integer providerId;

    @Column(name = "professional_profile_code", nullable = false, length = 64)
    private String professionalProfileCode;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "field_value", nullable = false, columnDefinition = "text")
    private String fieldValue;

    @Column(name = "sys_user_id", nullable = false)
    private Integer sysUserId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProviderId() {
        return providerId;
    }

    public void setProviderId(Integer providerId) {
        this.providerId = providerId;
    }

    public String getProfessionalProfileCode() {
        return professionalProfileCode;
    }

    public void setProfessionalProfileCode(String professionalProfileCode) {
        this.professionalProfileCode = professionalProfileCode;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    @Override
    public String getSysUserId() {
        return sysUserId != null ? sysUserId.toString() : null;
    }

    @Override
    public void setSysUserId(String sysUserId) {
        this.sysUserId = sysUserId == null ? null : Integer.valueOf(sysUserId);
    }
}
