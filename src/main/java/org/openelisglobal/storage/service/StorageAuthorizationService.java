package org.openelisglobal.storage.service;

import java.util.Map;
import java.util.Set;

/**
 * Applies Storage module permissions before exposing storage data or
 * operations.
 */
public interface StorageAuthorizationService {

    boolean hasPermission(String userId, String actionKey);

    /** Null means unrestricted; an empty set means no accessible specimens. */
    Set<String> getAccessibleSampleItemIds(String userId, String actionKey);

    /** Also preserves trusted service calls outside an HTTP request. */
    Set<String> getAccessibleSampleItemIdsForCurrentUser(String actionKey);

    void requireSampleAccess(String sampleItemId, String actionKey);

    void requireLocationAccess(String locationType, Integer locationId, String actionKey);

    Map<String, Map<String, String>> getOccupiedCoordinatesForCurrentUser(Integer boxId);
}
