package org.openelisglobal.sampleitem.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SaveSampleManagementChangesForm {

    @Valid
    @NotEmpty(message = "At least one sample update is required")
    private List<SampleUpdate> sampleUpdates = new ArrayList<>();

    public List<SampleUpdate> getSampleUpdates() {
        return sampleUpdates;
    }

    public void setSampleUpdates(List<SampleUpdate> sampleUpdates) {
        this.sampleUpdates = sampleUpdates;
    }

    public static class SampleUpdate {
        @NotBlank(message = "Sample item ID is required")
        private String sampleItemId;
        private String cugCode;
        private String quantity;
        private String unitOfMeasureId;
        private String collector;
        private String collectionDate;
        private String collectionTime;
        private Boolean removeSample;
        private Map<String, String> additionalFieldValues = new HashMap<>();

        @Valid
        private List<CurrentTestUpdate> currentTests = new ArrayList<>();

        public String getSampleItemId() {
            return sampleItemId;
        }

        public void setSampleItemId(String sampleItemId) {
            this.sampleItemId = sampleItemId;
        }

        public String getQuantity() {
            return quantity;
        }

        public void setQuantity(String quantity) {
            this.quantity = quantity;
        }

        public String getCugCode() {
            return cugCode;
        }

        public void setCugCode(String cugCode) {
            this.cugCode = cugCode;
        }

        public String getUnitOfMeasureId() {
            return unitOfMeasureId;
        }

        public void setUnitOfMeasureId(String unitOfMeasureId) {
            this.unitOfMeasureId = unitOfMeasureId;
        }

        public String getCollector() {
            return collector;
        }

        public void setCollector(String collector) {
            this.collector = collector;
        }

        public String getCollectionDate() {
            return collectionDate;
        }

        public void setCollectionDate(String collectionDate) {
            this.collectionDate = collectionDate;
        }

        public String getCollectionTime() {
            return collectionTime;
        }

        public void setCollectionTime(String collectionTime) {
            this.collectionTime = collectionTime;
        }

        public Boolean getRemoveSample() {
            return removeSample;
        }

        public void setRemoveSample(Boolean removeSample) {
            this.removeSample = removeSample;
        }

        public Map<String, String> getAdditionalFieldValues() {
            return additionalFieldValues;
        }

        public void setAdditionalFieldValues(Map<String, String> additionalFieldValues) {
            this.additionalFieldValues = additionalFieldValues;
        }

        public List<CurrentTestUpdate> getCurrentTests() {
            return currentTests;
        }

        public void setCurrentTests(List<CurrentTestUpdate> currentTests) {
            this.currentTests = currentTests;
        }
    }

    public static class CurrentTestUpdate {
        @NotBlank(message = "Analysis ID is required")
        private String analysisId;
        private Boolean canceled;

        public String getAnalysisId() {
            return analysisId;
        }

        public void setAnalysisId(String analysisId) {
            this.analysisId = analysisId;
        }

        public Boolean getCanceled() {
            return canceled;
        }

        public void setCanceled(Boolean canceled) {
            this.canceled = canceled;
        }
    }
}
