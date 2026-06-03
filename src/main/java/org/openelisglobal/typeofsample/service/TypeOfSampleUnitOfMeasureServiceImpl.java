package org.openelisglobal.typeofsample.service;

import java.util.List;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.typeofsample.dao.TypeOfSampleUnitOfMeasureDAO;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleUnitOfMeasure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TypeOfSampleUnitOfMeasureServiceImpl
        extends AuditableBaseObjectServiceImpl<TypeOfSampleUnitOfMeasure, String>
        implements TypeOfSampleUnitOfMeasureService {

    @Autowired
    private TypeOfSampleUnitOfMeasureDAO baseObjectDAO;

    public TypeOfSampleUnitOfMeasureServiceImpl() {
        super(TypeOfSampleUnitOfMeasure.class);
    }

    @Override
    protected TypeOfSampleUnitOfMeasureDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeOfSampleUnitOfMeasure> getBySampleTypeId(String sampleTypeId) {
        return baseObjectDAO.getAllMatching("typeOfSampleId", sampleTypeId);
    }
}
