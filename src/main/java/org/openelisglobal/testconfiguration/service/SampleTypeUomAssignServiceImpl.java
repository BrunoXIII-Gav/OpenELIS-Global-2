package org.openelisglobal.testconfiguration.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.openelisglobal.typeofsample.service.TypeOfSampleUnitOfMeasureService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleUnitOfMeasure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleTypeUomAssignServiceImpl implements SampleTypeUomAssignService {

    @Autowired
    private TypeOfSampleUnitOfMeasureService typeOfSampleUnitOfMeasureService;

    @Override
    @Transactional
    public void replaceAssignments(String sampleTypeId, List<String> unitOfMeasureIds, String systemUserId) {
        List<TypeOfSampleUnitOfMeasure> existingMappings = typeOfSampleUnitOfMeasureService.getBySampleTypeId(sampleTypeId);
        for (TypeOfSampleUnitOfMeasure existingMapping : existingMappings) {
            typeOfSampleUnitOfMeasureService.delete(existingMapping.getId(), systemUserId);
        }

        Set<String> uniqueUnitIds = new LinkedHashSet<>();
        if (unitOfMeasureIds != null) {
            for (String unitOfMeasureId : unitOfMeasureIds) {
                if (unitOfMeasureId != null && !unitOfMeasureId.isBlank()) {
                    uniqueUnitIds.add(unitOfMeasureId);
                }
            }
        }

        for (String unitOfMeasureId : uniqueUnitIds) {
            TypeOfSampleUnitOfMeasure mapping = new TypeOfSampleUnitOfMeasure();
            mapping.setTypeOfSampleId(sampleTypeId);
            mapping.setUnitOfMeasureId(unitOfMeasureId);
            mapping.setSysUserId(systemUserId);
            mapping.setLastupdatedFields();
            typeOfSampleUnitOfMeasureService.insert(mapping);
        }
    }
}
