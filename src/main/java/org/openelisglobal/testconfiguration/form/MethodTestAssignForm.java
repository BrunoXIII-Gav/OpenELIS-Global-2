package org.openelisglobal.testconfiguration.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.openelisglobal.common.form.BaseForm;
import org.openelisglobal.common.validator.ValidationHelper;
import org.openelisglobal.testconfiguration.action.MethodTests;

public class MethodTestAssignForm extends BaseForm {

    private List methodList;

    private MethodTests selectedMethod = new MethodTests();

    @NotBlank
    @Pattern(regexp = ValidationHelper.ID_REGEX)
    private String methodId = "";

    private List<@Pattern(regexp = ValidationHelper.ID_REGEX) String> currentTests;

    private List<@Pattern(regexp = ValidationHelper.ID_REGEX) String> availableTests;

    public MethodTestAssignForm() {
        setFormName("methodTestAssignForm");
    }

    public List getMethodList() {
        return methodList;
    }

    public void setMethodList(List methodList) {
        this.methodList = methodList;
    }

    public MethodTests getSelectedMethod() {
        return selectedMethod;
    }

    public void setSelectedMethod(MethodTests selectedMethod) {
        this.selectedMethod = selectedMethod;
    }

    public String getMethodId() {
        return methodId;
    }

    public void setMethodId(String methodId) {
        this.methodId = methodId;
    }

    public List<String> getCurrentTests() {
        return currentTests;
    }

    public void setCurrentTests(List<String> currentTests) {
        this.currentTests = currentTests;
    }

    public List<String> getAvailableTests() {
        return availableTests;
    }

    public void setAvailableTests(List<String> availableTests) {
        this.availableTests = availableTests;
    }
}
