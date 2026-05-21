package org.openelisglobal.sampleitem.dto;

public class SaveSampleManagementChangesResponse {

    private int updatedSamplesCount;
    private int cancelledTestsCount;
    private String message;

    public SaveSampleManagementChangesResponse() {
    }

    public SaveSampleManagementChangesResponse(int updatedSamplesCount, int cancelledTestsCount, String message) {
        this.updatedSamplesCount = updatedSamplesCount;
        this.cancelledTestsCount = cancelledTestsCount;
        this.message = message;
    }

    public int getUpdatedSamplesCount() {
        return updatedSamplesCount;
    }

    public void setUpdatedSamplesCount(int updatedSamplesCount) {
        this.updatedSamplesCount = updatedSamplesCount;
    }

    public int getCancelledTestsCount() {
        return cancelledTestsCount;
    }

    public void setCancelledTestsCount(int cancelledTestsCount) {
        this.cancelledTestsCount = cancelledTestsCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
