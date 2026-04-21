package org.openelisglobal.test.service;

import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.test.dao.TbMethodTestDAO;
import org.openelisglobal.test.valueholder.TbMethodTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TbMethodTestServiceImpl extends AuditableBaseObjectServiceImpl<TbMethodTest, String>
        implements TbMethodTestService {

    @Autowired
    protected TbMethodTestDAO baseObjectDAO;

    TbMethodTestServiceImpl() {
        super(TbMethodTest.class);
    }

    @Override
    protected TbMethodTestDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }
}
