package org.openelisglobal.role.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "role_permission_mapping")
public class RolePermissionMapping extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "custom_role_id", nullable = false)
    private Integer customRoleId;

    @Column(name = "permission_role_id", nullable = false)
    private Integer permissionRoleId;

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

    public Integer getPermissionRoleId() {
        return permissionRoleId;
    }

    public void setPermissionRoleId(Integer permissionRoleId) {
        this.permissionRoleId = permissionRoleId;
    }
}
