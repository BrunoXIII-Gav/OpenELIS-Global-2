package org.openelisglobal.role.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "custom_role_lab_unit_scope")
public class CustomRoleLabUnitScope extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "custom_role_id", nullable = false)
    private Integer customRoleId;

    @Column(name = "lab_unit_id", nullable = false)
    private String labUnitId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCustomRoleId() {
        return customRoleId;
    }

    public void setCustomRoleId(Integer customRoleId) {
        this.customRoleId = customRoleId;
    }

    public String getLabUnitId() {
        return labUnitId;
    }

    public void setLabUnitId(String labUnitId) {
        this.labUnitId = labUnitId;
    }
}
