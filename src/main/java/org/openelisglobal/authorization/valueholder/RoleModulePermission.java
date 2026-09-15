package org.openelisglobal.authorization.valueholder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "role_module_permission")
public class RoleModulePermission extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "module_key", nullable = false, length = 80)
    private String moduleKey;

    @Column(name = "action_key", nullable = false, length = 80)
    private String actionKey;

    @Column(name = "all_lab_units", nullable = false)
    private boolean allLabUnits = true;

    @OneToMany(mappedBy = "roleModulePermission", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RoleModulePermissionLabUnitScope> labUnitScopes = new LinkedHashSet<>();

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getActionKey() {
        return actionKey;
    }

    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
    }

    public boolean isAllLabUnits() {
        return allLabUnits;
    }

    public void setAllLabUnits(boolean allLabUnits) {
        this.allLabUnits = allLabUnits;
    }

    public Set<RoleModulePermissionLabUnitScope> getLabUnitScopes() {
        return labUnitScopes;
    }

    public void setLabUnitScopes(Set<RoleModulePermissionLabUnitScope> labUnitScopes) {
        this.labUnitScopes.clear();
        if (labUnitScopes != null) {
            labUnitScopes.forEach(this::addLabUnitScope);
        }
    }

    public void addLabUnitScope(RoleModulePermissionLabUnitScope labUnitScope) {
        labUnitScope.setRoleModulePermission(this);
        labUnitScopes.add(labUnitScope);
    }
}
