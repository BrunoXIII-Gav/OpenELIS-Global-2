package org.openelisglobal.testconfiguration.packageio.dao;

import java.util.*;
import org.openelisglobal.testconfiguration.packageio.valueholder.TestConfigurationRevision;

public interface TestConfigurationPackageDAO {
    record Attribute(Class<?> javaType, String targetKind, boolean association, boolean nullable) {
    }

    record Snapshot(String kind, String id, String stableKey, Map<String, Object> values, Map<String, Link> links) {
    }

    record Link(String kind, String id) {
    }

    Map<String, Attribute> attributes(String kind);

    Snapshot snapshot(String kind, String id);

    List<Snapshot> list(String kind);

    List<Snapshot> userCreatedTests();

    List<Snapshot> related(String kind, String property, Collection<String> ids);

    String insert(String kind, Map<String, Object> values, Map<String, String> references, String actorId);

    void link(String kind, String id, String property, String targetId);

    void flush();

    void lockImports();

    Optional<TestConfigurationRevision> findRevision(String fingerprint);

    Optional<TestConfigurationRevision> latestRevision(String family);

    List<TestConfigurationRevision> revisions();

    void saveRevision(TestConfigurationRevision revision);

    void saveIdentity(String kind, String entityId, String sourceKey, String revisionId);
}
