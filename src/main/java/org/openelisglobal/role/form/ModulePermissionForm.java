package org.openelisglobal.role.form;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class ModulePermissionForm {

    @NotBlank
    private String moduleKey;

    private List<String> actionKeys = new ArrayList<>();

    private boolean allLabUnits;

    private List<String> labUnitIds = new ArrayList<>();

    private List<String> restrictedFieldGroupKeys = new ArrayList<>();

    private List<String> restrictedFieldTagKeys = new ArrayList<>();

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public List<String> getActionKeys() {
        return actionKeys;
    }

    public void setActionKeys(List<String> actionKeys) {
        this.actionKeys = actionKeys;
    }

    public boolean isAllLabUnits() {
        return allLabUnits;
    }

    public void setAllLabUnits(boolean allLabUnits) {
        this.allLabUnits = allLabUnits;
    }

    public List<String> getLabUnitIds() {
        return labUnitIds;
    }

    public void setLabUnitIds(List<String> labUnitIds) {
        this.labUnitIds = labUnitIds;
    }

    public List<String> getRestrictedFieldGroupKeys() {
        return restrictedFieldGroupKeys;
    }

    public void setRestrictedFieldGroupKeys(List<String> restrictedFieldGroupKeys) {
        this.restrictedFieldGroupKeys = restrictedFieldGroupKeys;
    }

    public List<String> getRestrictedFieldTagKeys() {
        return restrictedFieldTagKeys;
    }

    public void setRestrictedFieldTagKeys(List<String> restrictedFieldTagKeys) {
        this.restrictedFieldTagKeys = restrictedFieldTagKeys;
    }
}
