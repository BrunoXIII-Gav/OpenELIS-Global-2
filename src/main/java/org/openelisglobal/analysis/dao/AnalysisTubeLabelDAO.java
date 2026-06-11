package org.openelisglobal.analysis.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.analysis.valueholder.AnalysisTubeLabel;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.common.exception.LIMSRuntimeException;

public interface AnalysisTubeLabelDAO extends BaseDAO<AnalysisTubeLabel, Long> {

    List<AnalysisTubeLabel> getByAnalysisId(String analysisId) throws LIMSRuntimeException;

    Optional<AnalysisTubeLabel> getByAnalysisIdAndBlockName(String analysisId, String blockName) throws LIMSRuntimeException;

    boolean existsByLabelCode(String labelCode, Long excludeId) throws LIMSRuntimeException;
}
