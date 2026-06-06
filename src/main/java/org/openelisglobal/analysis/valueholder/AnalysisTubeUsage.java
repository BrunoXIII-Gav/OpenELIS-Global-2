package org.openelisglobal.analysis.valueholder;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.openelisglobal.common.valueholder.BaseObject;

@Getter
@Setter
@Entity
@Access(AccessType.FIELD)
@Table(name = "analysis_tube_usage")
public class AnalysisTubeUsage extends BaseObject<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_tube_usage_generator")
    @SequenceGenerator(name = "analysis_tube_usage_generator", sequenceName = "analysis_tube_usage_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "analysis_id", nullable = false)
    @NotNull
    private Analysis analysis;

    @ManyToOne
    @JoinColumn(name = "parent_analysis_id", nullable = false)
    @NotNull
    private Analysis parentAnalysis;

    @Column(name = "child_block_name", nullable = false, length = 150)
    @NotNull
    private String childBlockName;

    @Column(name = "parent_tube_block_name", nullable = false, length = 150)
    @NotNull
    private String parentTubeBlockName;

    @Column(name = "used_quantity", nullable = false, precision = 18, scale = 3)
    @NotNull
    private BigDecimal usedQuantity;

    @Column(name = "performed_by_user", nullable = false)
    @NotNull
    private Integer performedByUser;

    @Column(name = "sys_user_id", nullable = false, length = 40)
    @NotNull
    private String persistedSysUserId;

    @Override
    public void setSysUserId(String sysUserId) {
        super.setSysUserId(sysUserId);
        this.persistedSysUserId = sysUserId;
    }

    @Override
    public String getSysUserId() {
        return persistedSysUserId;
    }
}
