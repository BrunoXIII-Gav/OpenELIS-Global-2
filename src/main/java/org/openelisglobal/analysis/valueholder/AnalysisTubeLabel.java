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
import lombok.Getter;
import lombok.Setter;
import org.openelisglobal.common.valueholder.BaseObject;

@Getter
@Setter
@Entity
@Access(AccessType.FIELD)
@Table(name = "analysis_tube_label")
public class AnalysisTubeLabel extends BaseObject<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_tube_label_generator")
    @SequenceGenerator(name = "analysis_tube_label_generator", sequenceName = "analysis_tube_label_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "analysis_id", nullable = false)
    @NotNull
    private Analysis analysis;

    @Column(name = "tube_block_name", nullable = false, length = 150)
    @NotNull
    private String tubeBlockName;

    @Column(name = "label_code", nullable = false, length = 180)
    @NotNull
    private String labelCode;

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
