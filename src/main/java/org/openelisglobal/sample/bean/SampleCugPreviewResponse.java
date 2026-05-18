package org.openelisglobal.sample.bean;

import java.sql.Timestamp;

public class SampleCugPreviewResponse {
    private String cugCode;
    private String reservationToken;
    private Timestamp expiresAt;

    public SampleCugPreviewResponse() {
    }

    public SampleCugPreviewResponse(String cugCode) {
        this.cugCode = cugCode;
    }

    public SampleCugPreviewResponse(String cugCode, String reservationToken, Timestamp expiresAt) {
        this.cugCode = cugCode;
        this.reservationToken = reservationToken;
        this.expiresAt = expiresAt;
    }

    public String getCugCode() {
        return cugCode;
    }

    public void setCugCode(String cugCode) {
        this.cugCode = cugCode;
    }

    public String getReservationToken() {
        return reservationToken;
    }

    public void setReservationToken(String reservationToken) {
        this.reservationToken = reservationToken;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }
}
