package org.openelisglobal.role.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import org.openelisglobal.common.validator.ValidationHelper;

public class CustomRoleDefinitionForm {

    @Pattern(regexp = ValidationHelper.ID_REGEX)
    private String id;

    @NotBlank
    private String name;

    private String description;

    private List<@Pattern(regexp = ValidationHelper.ID_REGEX) String> permissionRoleIds = new ArrayList<>();

    private List<String> applicableLabUnitIds = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPermissionRoleIds() {
        return permissionRoleIds;
    }

    public void setPermissionRoleIds(List<String> permissionRoleIds) {
        this.permissionRoleIds = permissionRoleIds;
    }

    public List<String> getApplicableLabUnitIds() {
        return applicableLabUnitIds;
    }

    public void setApplicableLabUnitIds(List<String> applicableLabUnitIds) {
        this.applicableLabUnitIds = applicableLabUnitIds;
    }
}
