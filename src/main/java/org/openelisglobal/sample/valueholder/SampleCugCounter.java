package org.openelisglobal.sample.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "sample_cug_counter")
public class SampleCugCounter extends BaseObject<String> {
    @Id
    @GenericGenerator(name = "sample_cug_counter_assigned", strategy = "assigned")
    @Column(name = "id", nullable = false, length = 32)
    private String id;
    @Column(name = "sys_user_id")
    private Integer sysUserId;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String value) {
        id = value;
    }

    @Override
    public String getSysUserId() {
        return sysUserId == null ? null : sysUserId.toString();
    }

    @Override
    public void setSysUserId(String value) {
        sysUserId = value == null ? null : Integer.valueOf(value);
    }

    @Column(name = "configured_start", nullable = false)
    private Long configuredStart;

    public Long getConfiguredStart() {
        return configuredStart;
    }

    public void setConfiguredStart(Long value) {
        configuredStart = value;
    }

    @Column(name = "next_candidate", nullable = false)
    private Long nextCandidate;

    public Long getNextCandidate() {
        return nextCandidate;
    }

    public void setNextCandidate(Long value) {
        nextCandidate = value;
    }
}
