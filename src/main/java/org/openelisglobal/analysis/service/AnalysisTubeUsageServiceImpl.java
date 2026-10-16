package org.openelisglobal.analysis.service;

import java.math.BigDecimal;
import java.util.List;
import org.openelisglobal.analysis.dao.AnalysisTubeUsageDAO;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.AnalysisTubeUsage;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisTubeUsageServiceImpl extends AuditableBaseObjectServiceImpl<AnalysisTubeUsage, Long>
        implements AnalysisTubeUsageService {

    @Autowired
    private AnalysisTubeUsageDAO analysisTubeUsageDAO;

    public AnalysisTubeUsageServiceImpl() {
        super(AnalysisTubeUsage.class);
    }

    @Override
    protected AnalysisTubeUsageDAO getBaseObjectDAO() {
        return analysisTubeUsageDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisTubeUsage> getByAnalysisId(String analysisId) {
        return analysisTubeUsageDAO.getByAnalysisId(analysisId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisTubeUsage> getByParentAnalysisId(String parentAnalysisId) {
        return analysisTubeUsageDAO.getByParentAnalysisId(parentAnalysisId);
    }

    @Override
    @Transactional
    public void deleteByAnalysisId(String analysisId) {
        analysisTubeUsageDAO.deleteByAnalysisId(analysisId);
    }

    @Override
    @Transactional
    public void replaceUsages(Analysis analysis, Analysis parentAnalysis, List<UsageInput> inputs, String actorId) {
        java.util.Map<String, AnalysisTubeUsage> previous = getByAnalysisId(analysis.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(AnalysisTubeUsage::getChildBlockName, usage -> usage));
        java.util.Set<String> blocks = new java.util.HashSet<>();
        for (UsageInput input : inputs) {
            if (!blocks.add(input.childBlockName()))
                throw new IllegalArgumentException("Duplicate tube block");
            AnalysisTubeUsage existing = previous.remove(input.childBlockName());
            if (existing == null) {
                createUsage(analysis, parentAnalysis, input.childBlockName(), input.parentTubeBlockName(),
                        input.usedQuantity(), actorId);
            } else if (!java.util.Objects.equals(existing.getParentAnalysis().getId(), parentAnalysis.getId())
                    || !java.util.Objects.equals(existing.getParentTubeBlockName(), input.parentTubeBlockName())
                    || existing.getUsedQuantity().compareTo(input.usedQuantity()) != 0) {
                existing.setParentAnalysis(parentAnalysis);
                existing.setParentTubeBlockName(input.parentTubeBlockName());
                existing.setUsedQuantity(input.usedQuantity());
                existing.setPerformedByUser(Integer.valueOf(actorId));
                existing.setSysUserId(actorId);
                update(existing);
            }
        }
        previous.values().forEach(analysisTubeUsageDAO::delete);
    }

    @Override
    @Transactional
    public AnalysisTubeUsage createUsage(Analysis analysis, Analysis parentAnalysis, String childBlockName,
            String parentTubeBlockName, BigDecimal usedQuantity, String sysUserId) {
        AnalysisTubeUsage usage = new AnalysisTubeUsage();
        usage.setAnalysis(analysis);
        usage.setParentAnalysis(parentAnalysis);
        usage.setChildBlockName(childBlockName);
        usage.setParentTubeBlockName(parentTubeBlockName);
        usage.setUsedQuantity(usedQuantity);
        usage.setPerformedByUser(Integer.valueOf(sysUserId));
        usage.setSysUserId(sysUserId);
        Long id = insert(usage);
        return get(id);
    }
}
