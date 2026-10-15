package org.openelisglobal.audittrail;

import static org.junit.Assert.*;

import org.junit.Test;
import org.openelisglobal.audittrail.util.AuditFieldPolicy;
import org.openelisglobal.audittrail.util.AuditModule;

public class AuditFieldPolicyTest {
    @Test
    public void excludesInternalValuesAndIncludesVisibleIdentifiersAndActions() {
        for (String field : java.util.List.of("fhirUuid", "sysUserId", "revision", "analysisId", "resultCalculated"))
            assertFalse(field, AuditFieldPolicy.includes("Analysis", field));
        assertFalse(AuditFieldPolicy.includes("SampleCugReservation", "status"));
        assertFalse(AuditFieldPolicy.includes("SampleItemAdditionalFieldValue", "fieldDefinitionId"));
        assertTrue(AuditFieldPolicy.includes("SampleItemAdditionalFieldValue", "fieldValue"));
        assertTrue(AuditFieldPolicy.includes("SampleItem", "cugCode"));
        assertTrue(AuditFieldPolicy.includes("Sample", "accessionNumber"));
        assertTrue(AuditFieldPolicy.includes("Analysis", "statusId"));
        assertTrue(AuditFieldPolicy.includes("AnalysisValidationApproval", "approvedAt"));
        assertTrue(AuditFieldPolicy.includes("TestAdditionalFieldOption", "sortOrder"));
    }

    @Test
    public void groupsClinicalValuesSeparatelyFromFieldConfiguration() {
        assertEquals("sample", AuditModule.forEntity("SampleOrderAdditionalFieldValue", "orderadditionalfield"));
        assertEquals("sampleitem", AuditModule.forEntity("SampleItemAdditionalFieldValue", "sample"));
        assertEquals("patient", AuditModule.forEntity("PatientAdditionalFieldValue", "patientadditionalfield"));
        assertEquals("result", AuditModule.forEntity("AnalysisAdditionalFieldValue", "testadditionalfield"));
        assertEquals("siteinformation",
                AuditModule.forEntity("OrderAdditionalFieldDefinition", "orderadditionalfield"));
        assertEquals("testconfiguration",
                AuditModule.forEntity("TestAdditionalFieldDefinition", "testadditionalfield"));
        assertEquals("systemuser", AuditModule.forEntity("RoleModulePermission", "authorization"));
        assertEquals("provider", AuditModule.forEntity("ProfessionalProfile", "professionalprofile"));
        assertEquals("siteinformation", AuditModule.canonical("typeofsample"));
        assertEquals("testconfiguration", AuditModule.canonical("testdependency"));
    }

    @Test
    public void distinguishesCurrentCatalogDescriptionsFromOriginalSnapshots() {
        var event = new org.openelisglobal.audittrail.valueholder.AuditEvent();
        event.setEntityName("SampleOrderAdditionalFieldValue");
        event.setModule("orderadditionalfield");
        event.setField("fieldValue");
        var recovered = org.openelisglobal.audittrail.form.AuditEventPage.Item.from(event, null, "Current name", null);
        assertTrue(recovered.newDescriptionFromCurrentCatalog());
        event.setNewDisplayValue("Original name");
        var snapshot = org.openelisglobal.audittrail.form.AuditEventPage.Item.from(event);
        assertFalse(snapshot.newDescriptionFromCurrentCatalog());
        assertEquals("Original name", snapshot.newDisplayValue());
    }
}
