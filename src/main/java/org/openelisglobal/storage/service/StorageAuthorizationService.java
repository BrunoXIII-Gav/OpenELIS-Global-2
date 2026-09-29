package org.openelisglobal.storage.service;

/** Applies Storage module permissions before exposing storage data or operations. */
public interface StorageAuthorizationService {

    boolean hasPermission(String userId, String actionKey);
}
