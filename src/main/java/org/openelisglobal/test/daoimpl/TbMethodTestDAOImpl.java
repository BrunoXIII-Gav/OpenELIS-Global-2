package org.openelisglobal.test.daoimpl;

import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.test.dao.TbMethodTestDAO;
import org.openelisglobal.test.valueholder.TbMethodTest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TbMethodTestDAOImpl extends BaseDAOImpl<TbMethodTest, String> implements TbMethodTestDAO {

    TbMethodTestDAOImpl() {
        super(TbMethodTest.class);
    }
}
