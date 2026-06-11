package org.openelisglobal.analysis.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.dao.AnalysisTubeLabelDAO;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.AnalysisTubeLabel;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisTubeLabelServiceImpl extends AuditableBaseObjectServiceImpl<AnalysisTubeLabel, Long>
        implements AnalysisTubeLabelService {

    @Autowired
    private AnalysisTubeLabelDAO analysisTubeLabelDAO;

    public AnalysisTubeLabelServiceImpl() {
        super(AnalysisTubeLabel.class);
    }

    @Override
    protected AnalysisTubeLabelDAO getBaseObjectDAO() {
        return analysisTubeLabelDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisTubeLabel> getByAnalysisId(String analysisId) {
        return analysisTubeLabelDAO.getByAnalysisId(analysisId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, AnalysisTubeLabel> getByAnalysisIdGroupedByBlock(String analysisId) {
        Map<String, AnalysisTubeLabel> labelsByBlock = new LinkedHashMap<>();
        for (AnalysisTubeLabel label : getByAnalysisId(analysisId)) {
            if (label == null || StringUtils.isBlank(label.getTubeBlockName())) {
                continue;
            }
            labelsByBlock.put(normalizeBlockName(label.getTubeBlockName()), label);
        }
        return labelsByBlock;
    }

    @Override
    @Transactional
    public AnalysisTubeLabel saveOrUpdateLabel(Analysis analysis, String blockName, String submittedLabelCode, String sysUserId) {
        if (analysis == null || analysis.getSampleItem() == null || StringUtils.isBlank(blockName)) {
            throw new IllegalArgumentException("Analysis and tube block are required for tube labels");
        }

        SampleItem sampleItem = analysis.getSampleItem();
        String prefix = StringUtils.trimToNull(sampleItem.getCugCode());
        if (prefix == null && sampleItem.getSample() != null) {
            prefix = StringUtils.trimToNull(sampleItem.getSample().getAccessionNumber());
        }
        if (prefix == null) {
            throw new IllegalArgumentException("Tube label generation requires a sample CUG or accession number");
        }

        AnalysisTubeLabel existing = analysisTubeLabelDAO.getByAnalysisIdAndBlockName(analysis.getId(), blockName).orElse(null);
        String normalizedLabelCode = StringUtils.trimToNull(submittedLabelCode);
        if (normalizedLabelCode == null && existing != null) {
            normalizedLabelCode = existing.getLabelCode();
        }
        if (normalizedLabelCode == null) {
            normalizedLabelCode = generateNextLabelCode(analysis.getId(), prefix);
        }

        validateLabelCode(normalizedLabelCode, prefix, existing == null ? null : existing.getId());

        AnalysisTubeLabel label = existing == null ? new AnalysisTubeLabel() : existing;
        label.setAnalysis(analysis);
        label.setTubeBlockName(StringUtils.trim(blockName));
        label.setLabelCode(normalizedLabelCode);
        label.setSysUserId(sysUserId);
        if (label.getId() == null) {
            Long id = insert(label);
            return get(id);
        }
        update(label);
        return get(label.getId());
    }

    private String generateNextLabelCode(String analysisId, String prefix) {
        List<AnalysisTubeLabel> existingLabels = getByAnalysisId(analysisId);
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
        int nextSuffix = 1;
        for (AnalysisTubeLabel label : existingLabels) {
            if (label == null || StringUtils.isBlank(label.getLabelCode())) {
                continue;
            }
            Matcher matcher = pattern.matcher(label.getLabelCode().trim());
            if (!matcher.matches()) {
                continue;
            }
            try {
                int currentSuffix = Integer.parseInt(matcher.group(1));
                nextSuffix = Math.max(nextSuffix, currentSuffix + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        return prefix + "." + nextSuffix;
    }

    private void validateLabelCode(String labelCode, String prefix, Long excludeId) {
        String trimmedCode = StringUtils.trimToNull(labelCode);
        if (trimmedCode == null) {
            throw new IllegalArgumentException("Tube label code is required");
        }
        if (!trimmedCode.matches("^" + Pattern.quote(prefix) + "\\.\\d+$")) {
            throw new IllegalArgumentException("Tube label must match the format " + prefix + ".N");
        }
        if (analysisTubeLabelDAO.existsByLabelCode(trimmedCode, excludeId)) {
            throw new IllegalArgumentException("Tube label already exists");
        }
    }

    private String normalizeBlockName(String blockName) {
        return StringUtils.trimToNull(blockName);
    }
}
