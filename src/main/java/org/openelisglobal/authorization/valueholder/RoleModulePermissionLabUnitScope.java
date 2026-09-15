package org.openelisglobal.authorization.valueholder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "role_module_permission_lab_unit_scope")
public class RoleModulePermissionLabUnitScope extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_module_permission_id", nullable = false)
    @JsonIgnore
    private RoleModulePermission roleModulePermission;

    @Column(name = "lab_unit_id", nullable = false, length = 64)
    private String labUnitId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public RoleModulePermission getRoleModulePermission() {
        return roleModulePermission;
    }

    public void setRoleModulePermission(RoleModulePermission roleModulePermission) {
        this.roleModulePermission = roleModulePermission;
    }

    public String getLabUnitId() {
        return labUnitId;
    }

    public void setLabUnitId(String labUnitId) {
        this.labUnitId = labUnitId;
    }
}
