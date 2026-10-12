package org.openelisglobal.testconfiguration.packageio.valueholder;

import jakarta.persistence.*;
import org.openelisglobal.common.valueholder.BaseObject;

/** Serializes the final conflict check and publication of imports. */
@Entity
@Table(name = "test_configuration_import_lock", schema = "clinlims")
public class TestConfigurationImportLock extends BaseObject<Integer> {
    @Id
    public Integer id;
    @Column(name = "origin_key", nullable = false, length = 36)
    public String originKey;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }
}
