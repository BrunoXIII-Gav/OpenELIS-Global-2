package org.openelisglobal.sample.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderAuthorizationServiceImpl implements OrderAuthorizationService {

    private static final String ORDERS_MODULE = "orders";

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Autowired
    private TestService testService;

    @Override
    public boolean hasPermission(String userId, String actionKey) {
        return moduleAuthorizationService.hasPermission(userId, ORDERS_MODULE, actionKey);
    }

    @Override
    public boolean canAccessAllTests(String userId, List<String> testIds, String actionKey) {
        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, ORDERS_MODULE, actionKey);
        if (!decision.allowed()) {
            return false;
        }
        if (decision.source() != AuthorizationSource.MODULE_PERMISSION || decision.allLabUnits()) {
            return true;
        }

        List<String> normalizedTestIds = testIds == null ? List.of()
                : testIds.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim).distinct().toList();
        if (normalizedTestIds.isEmpty()) {
            return false;
        }
        return permittedTestIds(decision.labUnitIds()).containsAll(normalizedTestIds);
    }

    @Override
    public boolean canAccessSampleXml(String userId, String sampleXml, String actionKey) {
        try {
            return canAccessAllTests(userId, extractTestIds(sampleXml), actionKey);
        } catch (DocumentException e) {
            return false;
        }
    }

    private Set<String> permittedTestIds(Collection<String> labUnitIds) {
        List<Integer> testSectionIds = labUnitIds.stream().filter(StringUtils::isNumeric).map(Integer::valueOf).toList();
        if (testSectionIds.isEmpty()) {
            return Set.of();
        }
        return testService.getTestsByTestSectionIds(testSectionIds).stream().map(Test::getId).filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<String> extractTestIds(String sampleXml) throws DocumentException {
        if (StringUtils.isBlank(sampleXml)) {
            return List.of();
        }
        List<String> testIds = new ArrayList<>();
        collectTestIds(DocumentHelper.parseText(sampleXml).getRootElement(), testIds);
        return testIds;
    }

    @SuppressWarnings("unchecked")
    private void collectTestIds(Element element, List<String> testIds) {
        String tests = element.attributeValue("tests");
        if (StringUtils.isNotBlank(tests)) {
            for (String testId : tests.split(",")) {
                if (StringUtils.isNotBlank(testId)) {
                    testIds.add(testId.trim());
                }
            }
        }
        for (Element child : (List<Element>) element.elements()) {
            collectTestIds(child, testIds);
        }
    }
}
