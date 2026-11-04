package org.openelisglobal.sample.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.openelisglobal.common.valueholder.BaseObject;

/** Maps the legacy environmental sample link for explicit clinical cleanup. */
@Entity
@Immutable
@Table(name = "sample_environmental")
public class SampleEnvironmental extends BaseObject<Long> {
    @Id
    private Long id;
    @Column(name = "samp_id", nullable = false)
    private Long sampleId;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long value) {
        id = value;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long value) {
        sampleId = value;
    }
}
