package org.openelisglobal.sample.bean;

import java.util.List;

public class SampleCugPreviewRequest {
    private String patientId;
    private String reservationToken;
    private String reservationContextId;
    private List<String> existingCugs;

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
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

    public List<String> getExistingCugs() {
        return existingCugs;
    }

    public void setExistingCugs(List<String> existingCugs) {
        this.existingCugs = existingCugs;
    }
}
