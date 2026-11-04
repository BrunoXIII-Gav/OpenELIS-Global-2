package org.openelisglobal.authorization.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.openelisglobal.common.valueholder.BaseObject;

/** A search criterion that a role may not use or receive in patient matches. */
@Entity
@Table(name = "role_patient_search_restriction")
public class RolePatientSearchRestriction extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "criterion_key", nullable = false, length = 80)
    private String criterionKey;

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

    public String getCriterionKey() {
        return criterionKey;
    }

    public void setCriterionKey(String criterionKey) {
        this.criterionKey = criterionKey;
    }
}
