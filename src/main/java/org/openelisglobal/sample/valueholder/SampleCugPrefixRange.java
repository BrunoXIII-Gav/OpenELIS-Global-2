package org.openelisglobal.sample.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "sample_cug_prefix_range")
public class SampleCugPrefixRange extends BaseObject<String> {
    @Id
    @GenericGenerator(name = "sample_cug_prefix_range_assigned", strategy = "assigned")
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

    @Column(name = "start_prefix", nullable = false)
    private Long startPrefix;

    public Long getStartPrefix() {
        return startPrefix;
    }

    public void setStartPrefix(Long value) {
        startPrefix = value;
    }

    @Column(name = "end_prefix", nullable = false)
    private Long endPrefix;

    public Long getEndPrefix() {
        return endPrefix;
    }

    public void setEndPrefix(Long value) {
        endPrefix = value;
    }
}
