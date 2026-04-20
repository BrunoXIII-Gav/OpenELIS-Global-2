package org.openelisglobal.orderadditionalfield.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "order_additional_field_option")
@DynamicUpdate
public class OrderAdditionalFieldOption extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_additional_field_option_seq")
    @SequenceGenerator(name = "order_additional_field_option_seq", sequenceName = "order_additional_field_option_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "field_def_id", nullable = false)
    private Integer fieldDefinitionId;

    @Column(name = "option_key", nullable = false, length = 80)
    private String optionKey;

    @Column(name = "option_label", nullable = false, length = 150)
    private String optionLabel;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "active", nullable = false)
    private Boolean active;

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

    public Integer getFieldDefinitionId() {
        return fieldDefinitionId;
    }

    public void setFieldDefinitionId(Integer fieldDefinitionId) {
        this.fieldDefinitionId = fieldDefinitionId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public void setOptionKey(String optionKey) {
        this.optionKey = optionKey;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public void setOptionLabel(String optionLabel) {
        this.optionLabel = optionLabel;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public String getSysUserId() {
        return sysUserId != null ? sysUserId.toString() : null;
    }

    @Override
    public void setSysUserId(String sysUserId) {
        this.sysUserId = sysUserId != null ? Integer.valueOf(sysUserId) : null;
    }
}
