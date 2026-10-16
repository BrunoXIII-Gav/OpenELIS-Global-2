package org.openelisglobal.audittrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.openelisglobal.audittrail.persistence.AuditEventListener;
import org.openelisglobal.audittrail.form.AuditEventPage;
import org.openelisglobal.audittrail.util.AuditModule;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.springframework.test.util.ReflectionTestUtils;

public class AuditModuleClassificationTest {
    private String captureModule(String className) {
        return ReflectionTestUtils.invokeMethod(AuditEventListener.class, "moduleOf", className);
    }

    @Test
    public void capturesSampleFieldValuesUnderSamples() {
        assertEquals("sampleitem",
                captureModule("org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue"));
    }

    @Test
    public void capturesSampleFormDefinitionsAndOptionsUnderConfiguration() {
        assertEquals("sampleadditionalfield",
                captureModule("org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition"));
        assertEquals("sampleadditionalfield",
                captureModule("org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption"));
        assertEquals("sampleadditionalfield",
                captureModule("org.openelisglobal.sampleadditionalfield.valueholder.SampleFixedFieldConfig"));
    }

    @Test
    public void preservesOrdersAndOtherModuleClassification() {
        assertEquals("sample", captureModule("org.openelisglobal.sample.valueholder.Sample"));
        assertEquals("sampleitem", captureModule("org.openelisglobal.sampleitem.valueholder.SampleItem"));
        assertEquals("patient", captureModule("org.openelisglobal.patient.valueholder.Patient"));
        assertEquals("sample", captureModule("org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder"));
        assertNull(captureModule("org.openelisglobal.audittrail.valueholder.AuditEvent"));
    }

    @Test
    public void classifiesProfessionalPersonalDetailsByTheirOriginalOperation() {
        var event = new AuditEvent();
        event.setEntityName("Person");
        event.setModule("patient");
        event.setSource("POST /api/OpenELIS-Global/rest/Provider/FhirUuid");
        assertEquals("provider", AuditModule.forEvent(event));
        assertEquals("provider", AuditEventPage.Item.from(event).module());
        assertEquals("patient", event.getModule());
        event.setSource("POST /api/OpenELIS-Global/rest/PatientManagement");
        assertEquals("patient", AuditModule.forEvent(event));
        event.setSource(null);
        assertEquals("patient", AuditModule.forEvent(event));
        event.setModule("provider");
        assertEquals("provider", AuditModule.forEvent(event));
    }
}
