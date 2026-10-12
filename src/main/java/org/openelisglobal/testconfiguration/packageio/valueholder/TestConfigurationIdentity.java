package org.openelisglobal.testconfiguration.packageio.valueholder;

import jakarta.persistence.*;
import org.openelisglobal.common.valueholder.BaseObject;

/**
 * Portable node identity, independent of local row IDs and test
 * interoperability GUIDs.
 */
@Entity
@Table(name = "test_configuration_identity", schema = "clinlims")
public class TestConfigurationIdentity extends BaseObject<String> {
    @Id
    @Column(name = "entity_key", nullable = false, length = 140)
    public String id;
    @Column(name = "source_key", nullable = false, length = 100)
    public String sourceKey;
    @Column(name = "revision_id", nullable = false, length = 36)
    public String revisionId;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }
}
