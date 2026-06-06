package org.openelisglobal.analysis.dao;

import java.util.List;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.analysis.valueholder.AnalysisTubeUsage;

public interface AnalysisTubeUsageDAO extends BaseDAO<AnalysisTubeUsage, Long> {
    List<AnalysisTubeUsage> getByAnalysisId(String analysisId) throws LIMSRuntimeException;

    List<AnalysisTubeUsage> getByParentAnalysisId(String parentAnalysisId) throws LIMSRuntimeException;

    void deleteByAnalysisId(String analysisId) throws LIMSRuntimeException;
}
