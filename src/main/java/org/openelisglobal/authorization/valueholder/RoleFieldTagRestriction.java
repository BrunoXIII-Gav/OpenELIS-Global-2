package org.openelisglobal.authorization.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "role_field_tag_restriction")
public class RoleFieldTagRestriction extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "module_key", nullable = false, length = 80)
    private String moduleKey;

    @Column(name = "field_tag_key", nullable = false, length = 120)
    private String fieldTagKey;

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

    public String getFieldTagKey() {
        return fieldTagKey;
    }

    public void setFieldTagKey(String fieldTagKey) {
        this.fieldTagKey = fieldTagKey;
    }
}
