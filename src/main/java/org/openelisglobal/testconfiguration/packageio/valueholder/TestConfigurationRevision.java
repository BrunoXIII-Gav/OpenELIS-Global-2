package org.openelisglobal.testconfiguration.packageio.valueholder;

import jakarta.persistence.*;
import java.time.Instant;
import org.openelisglobal.common.valueholder.BaseObject;

/**
 * Immutable record of exactly what was imported and where each reference went.
 */
@Entity
@Table(name = "test_configuration_revision", schema = "clinlims")
public class TestConfigurationRevision extends BaseObject<String> {
    @Id
    @Column(length = 36)
    public String id;
    @Column(name = "fingerprint", nullable = false, unique = true, length = 64)
    public String fingerprint;
    @Column(name = "family_key", nullable = false, length = 64)
    public String familyKey;
    @Column(name = "package_json", nullable = false, columnDefinition = "text")
    public String packageJson;
    @Column(name = "mapping_json", nullable = false, columnDefinition = "text")
    public String mappingJson;
    @Column(name = "actor_id", nullable = false, length = 40)
    public String actorId;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }
}
