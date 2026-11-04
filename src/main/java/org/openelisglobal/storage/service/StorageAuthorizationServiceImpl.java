package org.openelisglobal.storage.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.util.ControllerUtills;
import org.openelisglobal.login.dao.UserModuleService;
import org.openelisglobal.storage.dao.SampleStorageAssignmentDAO;
import org.openelisglobal.storage.dao.StorageSampleScopeDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Locations are shared; access to specimens follows each action's laboratory
 * unit scope.
 */
@Service
@Transactional(readOnly = true)
public class StorageAuthorizationServiceImpl implements StorageAuthorizationService {
    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;
    @Autowired
    private UserModuleService userModuleService;
    @Autowired
    private StorageSampleScopeDAO storageSampleScopeDAO;
    @Autowired
    private SampleStorageAssignmentDAO sampleStorageAssignmentDAO;

    @Override
    public boolean hasPermission(String userId, String actionKey) {
        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, "storage", actionKey);
        return decision.allowed() && (unrestricted(decision) || !decision.labUnitIds().isEmpty());
    }

    private boolean unrestricted(AuthorizationDecision decision) {
        return decision.source() != AuthorizationSource.MODULE_PERMISSION || decision.allLabUnits();
    }

    @Override
    public Set<String> getAccessibleSampleItemIds(String userId, String actionKey) {
        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, "storage", actionKey);
        if (!decision.allowed()) {
            return Set.of();
        }
        if (unrestricted(decision)) {
            return null;
        }
        if (decision.labUnitIds().isEmpty()) {
            return Set.of();
        }
        Set<String> permitted = new HashSet<>(storageSampleScopeDAO.findSampleItemIdsByLabUnits(decision.labUnitIds()));
        List<Object[]> parents = storageSampleScopeDAO.findUnanalysedSampleItemParents();
        boolean changed;
        do {
            changed = false;
            for (Object[] parent : parents) {
                if (permitted.contains(String.valueOf(parent[1]))) {
                    changed |= permitted.add(String.valueOf(parent[0]));
                }
            }
        } while (changed);
        return permitted;
    }

    @Override
    public Set<String> getAccessibleSampleItemIdsForCurrentUser(String actionKey) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            // Non-HTTP service callers retain their existing authorization boundary.
            return null;
        }
        var request = attributes.getRequest();
        String userId = ControllerUtills.getSysUserId(request);
        if (userId == null || userModuleService.isSessionExpired(request)) {
            throw new AccessDeniedException("Storage access denied");
        }
        if (userModuleService.isUserAdmin(request)) {
            return null;
        }
        return getAccessibleSampleItemIds(userId, actionKey);
    }

    @Override
    public void requireSampleAccess(String sampleItemId, String actionKey) {
        Set<String> permitted = getAccessibleSampleItemIdsForCurrentUser(actionKey);
        if (permitted != null && !permitted.contains(sampleItemId)) {
            throw new AccessDeniedException("Storage sample access denied");
        }
    }

    @Override
    public void requireLocationAccess(String locationType, Integer locationId, String actionKey) {
        Set<String> permitted = getAccessibleSampleItemIdsForCurrentUser(actionKey);
        if (permitted != null) {
            var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (!hasPermission(ControllerUtills.getSysUserId(attributes.getRequest()), actionKey)
                    || !permitted.containsAll(
                            storageSampleScopeDAO.findAssignedSampleItemIdsInHierarchy(locationType, locationId))) {
                throw new AccessDeniedException("Storage location contains samples outside the authorized scope");
            }
        }
    }

    @Override
    public Map<String, Map<String, String>> getOccupiedCoordinatesForCurrentUser(Integer boxId) {
        Set<String> permitted = getAccessibleSampleItemIdsForCurrentUser("read");
        Map<String, Map<String, String>> occupied = sampleStorageAssignmentDAO
                .getOccupiedCoordinatesWithSampleInfo(boxId);
        Map<String, Map<String, String>> result = new HashMap<>();
        occupied.forEach((coordinate, sample) -> result.put(coordinate,
                permitted == null || permitted.contains(sample.get("sampleItemId")) ? new HashMap<>(sample)
                        : Map.of()));
        // Keep every occupied coordinate, including specimens whose identity is
        // restricted.
        return result;
    }
}
