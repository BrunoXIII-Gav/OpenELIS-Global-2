package org.openelisglobal.test.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.rolemodule.service.RoleModuleService;
import org.openelisglobal.systemmodule.service.SystemModuleService;
import org.openelisglobal.systemmodule.valueholder.SystemModule;
import org.openelisglobal.systemusermodule.valueholder.RoleModule;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.testconfiguration.service.TestSectionCreateServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;

public class TestSectionCreationSourceTest {
    @Test
    public void creationFromManagementRecordsUserOrigin() {
        var service = new TestSectionCreateServiceImpl();
        var sections = mock(TestSectionService.class);
        ReflectionTestUtils.setField(service, "testSectionService", sections);
        ReflectionTestUtils.setField(service, "localizationService", mock(LocalizationService.class));
        ReflectionTestUtils.setField(service, "roleModuleService", mock(RoleModuleService.class));
        ReflectionTestUtils.setField(service, "systemModuleService", mock(SystemModuleService.class));
        var section = new TestSection();
        section.setSysUserId("117");
        service.insertTestSection(new Localization(), section, new SystemModule(), new SystemModule(),
                new SystemModule(), new RoleModule(), new RoleModule(), new RoleModule());
        assertEquals(TestSection.ORIGIN_USER, section.getCreationOrigin());
        assertEquals("117", section.getCreationOriginBy());
        assertNotNull(section.getCreationOriginAt());
        verify(sections).insert(section);
    }

    @Test
    public void csvInitializationCreatesSystemUnitsAndDoesNotReclassifyUserOwnedUnits() {
        var handler = new TestSectionConfigurationHandler();
        var sections = mock(TestSectionService.class);
        ReflectionTestUtils.setField(handler, "testSectionService", sections);
        ReflectionTestUtils.setField(handler, "localizationService", mock(LocalizationService.class));
        String[] values = { "Unit", "Description", "Y", "1", "N", "Unit", "Unité" };
        when(sections.insert(any(TestSection.class))).thenReturn("27");
        TestSection created = ReflectionTestUtils.invokeMethod(handler, "processCsvLine", values, 0, 1, 2, 3, 4, 5, 6,
                2, "initial.csv", 1);
        assertEquals(TestSection.ORIGIN_SYSTEM, created.getCreationOrigin());

        for (String origin : new String[] { TestSection.ORIGIN_UNKNOWN, TestSection.ORIGIN_USER }) {
            var existing = new TestSection();
            existing.setCreationOrigin(origin);
            when(sections.getTestSectionByName("Unit")).thenReturn(existing);
            ReflectionTestUtils.invokeMethod(handler, "processCsvLine", values, 0, 1, 2, 3, 4, 5, 6, 2, "initial.csv",
                    1);
            assertEquals(TestSection.ORIGIN_UNKNOWN.equals(origin) ? TestSection.ORIGIN_SYSTEM : origin,
                    existing.getCreationOrigin());
        }
    }
}
