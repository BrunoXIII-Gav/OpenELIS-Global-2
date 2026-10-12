package org.openelisglobal.testconfiguration.packageio.dao;

import java.util.*;
import org.openelisglobal.analyte.valueholder.Analyte;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.dictionarycategory.valueholder.DictionaryCategory;
import org.openelisglobal.label.valueholder.Label;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.method.valueholder.Method;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.resultlimits.valueholder.ResultLimit;
import org.openelisglobal.scriptlet.valueholder.Scriptlet;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.testadditionalfield.valueholder.*;
import org.openelisglobal.testanalyte.valueholder.TestAnalyte;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.openelisglobal.testreflex.valueholder.TestReflex;
import org.openelisglobal.testresult.valueholder.TestResult;
import org.openelisglobal.testtrailer.valueholder.TestTrailer;
import org.openelisglobal.typeofsample.valueholder.*;
import org.openelisglobal.typeoftestresult.valueholder.TypeOfTestResult;
import org.openelisglobal.unitofmeasure.valueholder.UnitOfMeasure;

/**
 * Closed entity allowlist: uploaded class names and arbitrary Hibernate
 * entities are never accepted.
 */
public final class TestPackageRegistry {
    public static final Set<String> OWNED = Set.of("Test", "TestResult", "ResultLimit", "Localization",
            "TestAdditionalFieldDefinition", "TestAdditionalFieldOption", "TestParentChildDependency", "TestAnalyte",
            "TestReflex", "TypeOfSampleTest", "PanelItem");
    public static final Map<String, Class<?>> TYPES;
    private static final Map<String, String> ID_REFERENCES = Map.ofEntries(Map.entry("ResultLimit.testId", "Test"),
            Map.entry("ResultLimit.resultTypeId", "TypeOfTestResult"),
            Map.entry("ResultLimit.dictionaryNormalId", "Dictionary"), Map.entry("TypeOfSampleTest.testId", "Test"),
            Map.entry("TypeOfSampleTest.typeOfSampleId", "TypeOfSample"),
            Map.entry("TestAdditionalFieldDefinition.testId", "Test"),
            Map.entry("TestAdditionalFieldOption.fieldDefinitionId", "TestAdditionalFieldDefinition"),
            Map.entry("TestReflex.siblingReflexId", "TestReflex"));
    public static final Set<String> NO_CREATE = Set.of("Scriptlet", "Organization", "Label", "TestTrailer",
            "TypeOfTestResult");
    static {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        for (Class<?> type : List.of(Test.class, TestResult.class, ResultLimit.class, Localization.class,
                TestAdditionalFieldDefinition.class, TestAdditionalFieldOption.class, TestParentChildDependency.class,
                TestAnalyte.class, TestReflex.class, TypeOfSample.class, TypeOfSampleTest.class, PanelItem.class,
                TestSection.class, UnitOfMeasure.class, Method.class, Panel.class, Dictionary.class,
                DictionaryCategory.class, Analyte.class, TypeOfTestResult.class, Label.class, TestTrailer.class,
                Scriptlet.class, Organization.class)) {
            types.put(type.getSimpleName(), type);
        }
        TYPES = Collections.unmodifiableMap(types);
    }

    public static Class<?> type(String kind) {
        Class<?> type = TYPES.get(kind);
        if (type == null)
            throw new IllegalArgumentException("testPackage.issue.kind");
        return type;
    }

    public static String idReference(String kind, String property, Map<String, ?> values) {
        if ("TestResult.value".equals(kind + "." + property)
                && Set.of("D", "M", "C").contains(String.valueOf(values.get("testResultType"))))
            return "Dictionary";
        return ID_REFERENCES.get(kind + "." + property);
    }

    public static boolean omitted(String kind, String property) {
        return Set
                .of("lastupdated", "sysUserId", "guid", "fhirUuid", "fhir_uuid", "normalizedDescription", "userCreated",
                        "creationOriginVerified", "creationOriginVerifiedBy", "creationOriginVerifiedAt")
                .contains(property) || ("Scriptlet".equals(kind) && !"scriptletName".equals(property))
        // The legacy unique panel alias is local identity; membership points to the new
        // Test.
                || ("PanelItem".equals(kind) && "testName".equals(property));
    }

    private TestPackageRegistry() {
    }
}
