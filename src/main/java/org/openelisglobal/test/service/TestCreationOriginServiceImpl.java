package org.openelisglobal.test.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.openelisglobal.test.dao.TestCreationOriginDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TestCreationOriginServiceImpl implements TestCreationOriginService {
    @Autowired
    private TestCreationOriginDAO dao;

    @Autowired
    private SystemTestCatalog systemTestCatalog;

    @Override
    public List<Map<String, String>> unclassifiedTests() {
        return dao.unclassifiedTests().stream().filter(t -> !systemTestCatalog.contains(t.getGuid())).map(t -> Map
                .of("id", t.getId(), "name", t.getDescription(), "code", Objects.toString(t.getLocalCode(), "")))
                .toList();
    }

    @Override
    @Transactional
    public int classify(List<String> ids, boolean userCreated, String actorId) {
        if (ids == null || ids.isEmpty() || ids.size() > 200 || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(id -> id == null || !id.matches("[0-9]{1,22}")) || actorId == null
                || actorId.isBlank())
            throw new IllegalArgumentException("testPackage.issue.selection");
        int count = dao.classify(ids, userCreated, actorId);
        if (count != ids.size())
            throw new IllegalArgumentException("testPackage.issue.conflicts");
        return count;
    }

    @Override
    @Transactional
    public void recognizeConfigurationTest(String id) {
        dao.classify(List.of(id), false, null);
    }
}
