package org.openelisglobal.role.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.RoleFieldRestrictionService;
import org.openelisglobal.authorization.service.RoleFieldTagRestrictionService;
import org.openelisglobal.authorization.service.RoleModulePermissionService;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.role.valueholder.Role;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CustomRoleExplicitPermissionsTest {
    @Mock
    private RoleModulePermissionService roleModulePermissionService;
    @Mock
    private RoleFieldRestrictionService roleFieldRestrictionService;
    @Mock
    private RoleFieldTagRestrictionService roleFieldTagRestrictionService;
    @Mock
    private RoleService roleService;
    @InjectMocks
    private CustomRoleDefinitionServiceImpl service;

    @Before
    public void setup() {
        service = spy(service);
        doReturn(List.of("93")).when(service).getPermissionRoleIdsForCustomRole("104");
        doReturn(List.of("2")).when(service).getApplicableLabUnitIdsForCustomRole("104");
        Role legacy = new Role();
        legacy.setName(Constants.ROLE_SAMPLE_MANAGEMENT);
        when(roleService.getRoleById("93")).thenReturn(legacy);
    }

    @Test
    public void readingEmptyRoleCannotRecreateActionsFromOldMappings() {
        assertTrue(service.getModulePermissionsForCustomRole("104").isEmpty());
        verify(service, never()).getPermissionRoleIdsForCustomRole("104");
    }

    @Test
    public void readingConfiguredRoleShowsOnlyItsExplicitActions() {
        RoleModulePermission read = new RoleModulePermission();
        read.setModuleKey("sample-management");
        read.setActionKey("read");
        when(roleModulePermissionService.getByRoleId(104)).thenReturn(List.of(read));
        assertEquals(List.of("read"), service.getModulePermissionsForCustomRole("104").get(0).getActionKeys());
        verify(service, never()).getPermissionRoleIdsForCustomRole("104");
    }
}
