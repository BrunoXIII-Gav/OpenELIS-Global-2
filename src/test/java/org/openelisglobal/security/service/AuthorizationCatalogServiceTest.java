package org.openelisglobal.security.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.common.service.ProfessionalProfilePermissionService.PermissionFlow;
import org.openelisglobal.security.service.AuthorizationCatalogService.AuthorizationCatalog;
import org.openelisglobal.security.service.AuthorizationCatalogService.AuthorizationModule;

public class AuthorizationCatalogServiceTest {

    private final AuthorizationCatalogService authorizationCatalogService = new AuthorizationCatalogService();

    @Test
    public void getCatalog_includesThePrimaryAuthorizationModules() {
        AuthorizationCatalog catalog = authorizationCatalogService.getCatalog();

        assertEquals(List.of("sample-management", "orders", "patients", "results", "validation", "storage",
                "administration"), catalog.modules().stream().map(AuthorizationModule::key).toList());
    }

    @Test
    public void getCatalog_definesSampleManagementActionsAndFieldGroups() {
        AuthorizationModule sampleManagement = authorizationCatalogService.getCatalog().modules().stream()
                .filter(module -> "sample-management".equals(module.key())).findFirst().orElseThrow();

        assertEquals(List.of("read", "receive", "update", "aliquot", "print", "export"),
                sampleManagement.actions().stream().map(action -> action.key()).toList());
        assertEquals(
                List.of("patient-identity", "patient-demographics", "reception", "collection", "clinical-data",
                        "tests-and-results"),
                sampleManagement.fieldGroups().stream().map(fieldGroup -> fieldGroup.key()).toList());
    }

    @Test
    public void getCatalog_definesValidationAsOneFinalOperation() {
        AuthorizationModule validation = authorizationCatalogService.getCatalog().modules().stream()
                .filter(module -> "validation".equals(module.key())).findFirst().orElseThrow();

        assertEquals(List.of("read", "validate", "revoke"),
                validation.actions().stream().map(action -> action.key()).toList());
        assertFalse(authorizationCatalogService.isKnownPermission("validation", "sign"));
    }

    @Test
    public void getCatalog_listsExistingProfessionalProfileFlowsSeparately() {
        AuthorizationCatalog catalog = authorizationCatalogService.getCatalog();

        assertEquals(PermissionFlow.values().length, catalog.professionalProfileFlows().size());
        assertTrue(catalog.professionalProfileFlows().stream()
                .anyMatch(flow -> PermissionFlow.VALIDATION_INTERPRETER.name().equals(flow.key())));
    }
}
