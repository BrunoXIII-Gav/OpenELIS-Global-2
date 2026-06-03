package org.openelisglobal.typeofsample.daoimpl;

import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.typeofsample.dao.TypeOfSampleUnitOfMeasureDAO;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleUnitOfMeasure;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TypeOfSampleUnitOfMeasureDAOImpl extends BaseDAOImpl<TypeOfSampleUnitOfMeasure, String>
        implements TypeOfSampleUnitOfMeasureDAO {

    public TypeOfSampleUnitOfMeasureDAOImpl() {
        super(TypeOfSampleUnitOfMeasure.class);
    }
}
