package org.openelisglobal.testconfiguration.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openelisglobal.common.form.BaseForm;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.common.validator.ValidationHelper;

public class SampleTypeUomAssignForm extends BaseForm {

    private List<IdValuePair> sampleTypeList = new ArrayList<>();
    private List<IdValuePair> uomList = new ArrayList<>();
    private Map<String, List<IdValuePair>> sampleTypeUomMap = new LinkedHashMap<>();

    @NotBlank
    @Pattern(regexp = ValidationHelper.ID_REGEX)
    private String sampleTypeId = "";

    private List<String> unitOfMeasureIds = new ArrayList<>();

    public SampleTypeUomAssignForm() {
        setFormName("sampleTypeUomAssignForm");
    }

    public List<IdValuePair> getSampleTypeList() {
        return sampleTypeList;
    }

    public void setSampleTypeList(List<IdValuePair> sampleTypeList) {
        this.sampleTypeList = sampleTypeList;
    }

    public List<IdValuePair> getUomList() {
        return uomList;
    }

    public void setUomList(List<IdValuePair> uomList) {
        this.uomList = uomList;
    }

    public Map<String, List<IdValuePair>> getSampleTypeUomMap() {
        return sampleTypeUomMap;
    }

    public void setSampleTypeUomMap(Map<String, List<IdValuePair>> sampleTypeUomMap) {
        this.sampleTypeUomMap = sampleTypeUomMap;
    }

    public String getSampleTypeId() {
        return sampleTypeId;
    }

    public void setSampleTypeId(String sampleTypeId) {
        this.sampleTypeId = sampleTypeId;
    }

    public List<String> getUnitOfMeasureIds() {
        return unitOfMeasureIds;
    }

    public void setUnitOfMeasureIds(List<String> unitOfMeasureIds) {
        this.unitOfMeasureIds = unitOfMeasureIds;
    }
}
