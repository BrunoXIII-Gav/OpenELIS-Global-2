package org.openelisglobal.typeofsample.service;

import java.util.List;
import org.openelisglobal.common.service.BaseObjectService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleUnitOfMeasure;

public interface TypeOfSampleUnitOfMeasureService extends BaseObjectService<TypeOfSampleUnitOfMeasure, String> {
    List<TypeOfSampleUnitOfMeasure> getBySampleTypeId(String sampleTypeId);
}
