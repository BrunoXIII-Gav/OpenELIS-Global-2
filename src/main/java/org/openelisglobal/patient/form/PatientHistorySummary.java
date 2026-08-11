package org.openelisglobal.patient.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;

public class PatientHistorySummary {

    private String patientId;
    private Metrics metrics = new Metrics();
    private List<OrderRecord> orders = new ArrayList<>();
    private List<SampleRecord> samples = new ArrayList<>();
    private List<ResultRecord> results = new ArrayList<>();

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public List<OrderRecord> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderRecord> orders) {
        this.orders = orders;
    }

    public List<SampleRecord> getSamples() {
        return samples;
    }

    public void setSamples(List<SampleRecord> samples) {
        this.samples = samples;
    }

    public List<ResultRecord> getResults() {
        return results;
    }

    public void setResults(List<ResultRecord> results) {
        this.results = results;
    }

    public static class Metrics {
        private int totalOrders;
        private int totalSamples;
        private int storedSamples;
        private int totalTests;
        private int completedTests;
        private int pendingTests;

        public int getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(int totalOrders) {
            this.totalOrders = totalOrders;
        }

        public int getTotalSamples() {
            return totalSamples;
        }

        public void setTotalSamples(int totalSamples) {
            this.totalSamples = totalSamples;
        }

        public int getStoredSamples() {
            return storedSamples;
        }

        public void setStoredSamples(int storedSamples) {
            this.storedSamples = storedSamples;
        }

        public int getTotalTests() {
            return totalTests;
        }

        public void setTotalTests(int totalTests) {
            this.totalTests = totalTests;
        }

        public int getCompletedTests() {
            return completedTests;
        }

        public void setCompletedTests(int completedTests) {
            this.completedTests = completedTests;
        }

        public int getPendingTests() {
            return pendingTests;
        }

        public void setPendingTests(int pendingTests) {
            this.pendingTests = pendingTests;
        }
    }

    public static class OrderRecord {
        private String id;
        private String accessionNumber;
        private String clinicalOrderId;
        private String clientReference;
        private String priority;
        private String requestDate;
        private String receivedDate;
        private String status;
        private String referringSiteName;
        private String requesterName;
        private List<FieldRecord> orderFields = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getAccessionNumber() {
            return accessionNumber;
        }

        public void setAccessionNumber(String accessionNumber) {
            this.accessionNumber = accessionNumber;
        }

        public String getClinicalOrderId() {
            return clinicalOrderId;
        }

        public void setClinicalOrderId(String clinicalOrderId) {
            this.clinicalOrderId = clinicalOrderId;
        }

        public String getClientReference() {
            return clientReference;
        }

        public void setClientReference(String clientReference) {
            this.clientReference = clientReference;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getRequestDate() {
            return requestDate;
        }

        public void setRequestDate(String requestDate) {
            this.requestDate = requestDate;
        }

        public String getReceivedDate() {
            return receivedDate;
        }

        public void setReceivedDate(String receivedDate) {
            this.receivedDate = receivedDate;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReferringSiteName() {
            return referringSiteName;
        }

        public void setReferringSiteName(String referringSiteName) {
            this.referringSiteName = referringSiteName;
        }

        public String getRequesterName() {
            return requesterName;
        }

        public void setRequesterName(String requesterName) {
            this.requesterName = requesterName;
        }

        public List<FieldRecord> getOrderFields() {
            return orderFields;
        }

        public void setOrderFields(List<FieldRecord> orderFields) {
            this.orderFields = orderFields == null ? new ArrayList<>() : orderFields;
        }
    }

    public static class SampleRecord {
        private String id;
        private String accessionNumber;
        private String clinicalOrderId;
        private String sampleItemExternalId;
        private String sampleType;
        private String collectionDate;
        private String status;
        private String parentSampleItemExternalId;
        private String storageLocation;
        private String storageAssignedDate;
        private String storagePositionCoordinate;
        private String storageNotes;
        private int totalTests;
        private int completedTests;
        private int pendingTests;
        private boolean stored;
        private List<FieldRecord> collectionFields = new ArrayList<>();
        private List<FieldRecord> receptionFields = new ArrayList<>();
        private List<FieldRecord> fixedFields = new ArrayList<>();
        private List<FieldRecord> orderFields = new ArrayList<>();
        private List<FieldRecord> additionalFields = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getAccessionNumber() {
            return accessionNumber;
        }

        public void setAccessionNumber(String accessionNumber) {
            this.accessionNumber = accessionNumber;
        }

        public String getClinicalOrderId() {
            return clinicalOrderId;
        }

        public void setClinicalOrderId(String clinicalOrderId) {
            this.clinicalOrderId = clinicalOrderId;
        }

        public String getSampleItemExternalId() {
            return sampleItemExternalId;
        }

        public void setSampleItemExternalId(String sampleItemExternalId) {
            this.sampleItemExternalId = sampleItemExternalId;
        }

        public String getSampleType() {
            return sampleType;
        }

        public void setSampleType(String sampleType) {
            this.sampleType = sampleType;
        }

        public String getCollectionDate() {
            return collectionDate;
        }

        public void setCollectionDate(String collectionDate) {
            this.collectionDate = collectionDate;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getParentSampleItemExternalId() {
            return parentSampleItemExternalId;
        }

        public void setParentSampleItemExternalId(String parentSampleItemExternalId) {
            this.parentSampleItemExternalId = parentSampleItemExternalId;
        }

        public String getStorageLocation() {
            return storageLocation;
        }

        public void setStorageLocation(String storageLocation) {
            this.storageLocation = storageLocation;
        }

        public String getStorageAssignedDate() {
            return storageAssignedDate;
        }

        public void setStorageAssignedDate(String storageAssignedDate) {
            this.storageAssignedDate = storageAssignedDate;
        }

        public String getStoragePositionCoordinate() {
            return storagePositionCoordinate;
        }

        public void setStoragePositionCoordinate(String storagePositionCoordinate) {
            this.storagePositionCoordinate = storagePositionCoordinate;
        }

        public String getStorageNotes() {
            return storageNotes;
        }

        public void setStorageNotes(String storageNotes) {
            this.storageNotes = storageNotes;
        }

        public int getTotalTests() {
            return totalTests;
        }

        public void setTotalTests(int totalTests) {
            this.totalTests = totalTests;
        }

        public int getCompletedTests() {
            return completedTests;
        }

        public void setCompletedTests(int completedTests) {
            this.completedTests = completedTests;
        }

        public int getPendingTests() {
            return pendingTests;
        }

        public void setPendingTests(int pendingTests) {
            this.pendingTests = pendingTests;
        }

        public boolean isStored() {
            return stored;
        }

        public void setStored(boolean stored) {
            this.stored = stored;
        }

        public List<FieldRecord> getCollectionFields() {
            return collectionFields;
        }

        public void setCollectionFields(List<FieldRecord> collectionFields) {
            this.collectionFields = collectionFields == null ? new ArrayList<>() : collectionFields;
        }

        public List<FieldRecord> getReceptionFields() {
            return receptionFields;
        }

        public void setReceptionFields(List<FieldRecord> receptionFields) {
            this.receptionFields = receptionFields == null ? new ArrayList<>() : receptionFields;
        }

        public List<FieldRecord> getFixedFields() {
            return fixedFields;
        }

        public void setFixedFields(List<FieldRecord> fixedFields) {
            this.fixedFields = fixedFields == null ? new ArrayList<>() : fixedFields;
        }

        public List<FieldRecord> getOrderFields() {
            return orderFields;
        }

        public void setOrderFields(List<FieldRecord> orderFields) {
            this.orderFields = orderFields == null ? new ArrayList<>() : orderFields;
        }

        public List<FieldRecord> getAdditionalFields() {
            return additionalFields;
        }

        public void setAdditionalFields(List<FieldRecord> additionalFields) {
            this.additionalFields = additionalFields == null ? new ArrayList<>() : additionalFields;
        }
    }

    public static class ResultRecord {
        private String id;
        private String accessionNumber;
        private String clinicalOrderId;
        private String sampleType;
        private String collectionDate;
        private String sampleStatus;
        private String testName;
        private String testStatus;
        private String resultDate;
        private String resultName;
        private String resultValue;
        private String resultType;
        private String resultDisplayConfigJson;
        private List<FieldRecord> fixedFields = new ArrayList<>();
        private List<FieldRecord> sampleOrderFields = new ArrayList<>();
        private List<FieldRecord> sampleAdditionalFields = new ArrayList<>();
        private List<FieldRecord> resultValues = new ArrayList<>();
        private List<TestAdditionalFieldPayload> additionalFieldDefinitions = new ArrayList<>();
        private Map<String, String> additionalFieldValues = new HashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getAccessionNumber() {
            return accessionNumber;
        }

        public void setAccessionNumber(String accessionNumber) {
            this.accessionNumber = accessionNumber;
        }

        public String getClinicalOrderId() {
            return clinicalOrderId;
        }

        public void setClinicalOrderId(String clinicalOrderId) {
            this.clinicalOrderId = clinicalOrderId;
        }

        public String getSampleType() {
            return sampleType;
        }

        public void setSampleType(String sampleType) {
            this.sampleType = sampleType;
        }

        public String getCollectionDate() {
            return collectionDate;
        }

        public void setCollectionDate(String collectionDate) {
            this.collectionDate = collectionDate;
        }

        public String getSampleStatus() {
            return sampleStatus;
        }

        public void setSampleStatus(String sampleStatus) {
            this.sampleStatus = sampleStatus;
        }

        public String getTestName() {
            return testName;
        }

        public void setTestName(String testName) {
            this.testName = testName;
        }

        public String getTestStatus() {
            return testStatus;
        }

        public void setTestStatus(String testStatus) {
            this.testStatus = testStatus;
        }

        public String getResultDate() {
            return resultDate;
        }

        public void setResultDate(String resultDate) {
            this.resultDate = resultDate;
        }

        public String getResultName() {
            return resultName;
        }

        public void setResultName(String resultName) {
            this.resultName = resultName;
        }

        public String getResultValue() {
            return resultValue;
        }

        public void setResultValue(String resultValue) {
            this.resultValue = resultValue;
        }

        public String getResultType() {
            return resultType;
        }

        public void setResultType(String resultType) {
            this.resultType = resultType;
        }

        public String getResultDisplayConfigJson() {
            return resultDisplayConfigJson;
        }

        public void setResultDisplayConfigJson(String resultDisplayConfigJson) {
            this.resultDisplayConfigJson = resultDisplayConfigJson;
        }

        public List<FieldRecord> getFixedFields() {
            return fixedFields;
        }

        public void setFixedFields(List<FieldRecord> fixedFields) {
            this.fixedFields = fixedFields == null ? new ArrayList<>() : fixedFields;
        }

        public List<FieldRecord> getSampleOrderFields() {
            return sampleOrderFields;
        }

        public void setSampleOrderFields(List<FieldRecord> sampleOrderFields) {
            this.sampleOrderFields = sampleOrderFields == null ? new ArrayList<>() : sampleOrderFields;
        }

        public List<FieldRecord> getSampleAdditionalFields() {
            return sampleAdditionalFields;
        }

        public void setSampleAdditionalFields(List<FieldRecord> sampleAdditionalFields) {
            this.sampleAdditionalFields = sampleAdditionalFields == null ? new ArrayList<>() : sampleAdditionalFields;
        }

        public List<FieldRecord> getResultValues() {
            return resultValues;
        }

        public void setResultValues(List<FieldRecord> resultValues) {
            this.resultValues = resultValues == null ? new ArrayList<>() : resultValues;
        }

        public List<TestAdditionalFieldPayload> getAdditionalFieldDefinitions() {
            return additionalFieldDefinitions;
        }

        public void setAdditionalFieldDefinitions(List<TestAdditionalFieldPayload> additionalFieldDefinitions) {
            this.additionalFieldDefinitions = additionalFieldDefinitions == null ? new ArrayList<>()
                    : additionalFieldDefinitions;
        }

        public Map<String, String> getAdditionalFieldValues() {
            return additionalFieldValues;
        }

        public void setAdditionalFieldValues(Map<String, String> additionalFieldValues) {
            this.additionalFieldValues = additionalFieldValues == null ? new HashMap<>() : additionalFieldValues;
        }
    }

    public static class FieldRecord {
        private String key;
        private String label;
        private String value;
        private String fieldType;
        private String source;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
