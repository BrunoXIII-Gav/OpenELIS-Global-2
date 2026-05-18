package org.openelisglobal.sample.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "sample_cug_reservation")
@DynamicUpdate
public class SampleCugReservation extends BaseObject<Integer> {

    public enum ReservationStatus {
        RESERVED, CONSUMED, EXPIRED, CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sample_cug_reservation_seq")
    @SequenceGenerator(name = "sample_cug_reservation_seq", sequenceName = "sample_cug_reservation_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "reserved_value", nullable = false, length = 128)
    private String reservedValue;

    @Column(name = "reservation_token", nullable = false, length = 80)
    private String reservationToken;

    @Column(name = "reservation_context_id", length = 120)
    private String reservationContextId;

    @Column(name = "sample_id")
    private Integer sampleId;

    @Column(name = "patient_id")
    private Integer patientId;

    @Column(name = "reserved_by_user_id", nullable = false)
    private Integer reservedByUserId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Timestamp expiresAt;

    @Column(name = "sys_user_id", nullable = false)
    private Integer sysUserId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public String getReservedValue() {
        return reservedValue;
    }

    public void setReservedValue(String reservedValue) {
        this.reservedValue = reservedValue;
    }

    public String getReservationToken() {
        return reservationToken;
    }

    public void setReservationToken(String reservationToken) {
        this.reservationToken = reservationToken;
    }

    public String getReservationContextId() {
        return reservationContextId;
    }

    public void setReservationContextId(String reservationContextId) {
        this.reservationContextId = reservationContextId;
    }

    public Integer getSampleId() {
        return sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public void setPatientId(Integer patientId) {
        this.patientId = patientId;
    }

    public Integer getReservedByUserId() {
        return reservedByUserId;
    }

    public void setReservedByUserId(Integer reservedByUserId) {
        this.reservedByUserId = reservedByUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String getSysUserId() {
        return sysUserId != null ? sysUserId.toString() : null;
    }

    @Override
    public void setSysUserId(String sysUserId) {
        this.sysUserId = sysUserId != null ? Integer.valueOf(sysUserId) : null;
    }
}
