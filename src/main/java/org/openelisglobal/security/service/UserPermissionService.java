package org.openelisglobal.security.service;

import java.util.List;
import java.util.Set;
import org.openelisglobal.common.constants.SystemPermission;

public interface UserPermissionService {

    boolean hasPermission(String userId, SystemPermission permission);

    List<String> getUserIdsForPermission(SystemPermission permission);

    List<String> getGrantedLegacyRoleNames(String userId, SystemPermission permission);

    Set<String> getEffectiveRoleNames(String userId);
}
