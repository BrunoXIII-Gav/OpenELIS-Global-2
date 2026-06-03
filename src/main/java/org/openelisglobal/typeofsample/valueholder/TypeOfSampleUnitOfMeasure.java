package org.openelisglobal.typeofsample.valueholder;

import org.openelisglobal.common.valueholder.BaseObject;

public class TypeOfSampleUnitOfMeasure extends BaseObject<String> {
    private static final long serialVersionUID = 1L;

    private String id;
    private String typeOfSampleId;
    private String unitOfMeasureId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTypeOfSampleId() {
        return typeOfSampleId;
    }

    public void setTypeOfSampleId(String typeOfSampleId) {
        this.typeOfSampleId = typeOfSampleId;
    }

    public String getUnitOfMeasureId() {
        return unitOfMeasureId;
    }

    public void setUnitOfMeasureId(String unitOfMeasureId) {
        this.unitOfMeasureId = unitOfMeasureId;
    }
}
