package org.openelisglobal.administration.service;

/** Applies Administration module permissions to global configuration. */
public interface AdministrationAuthorizationService {

    boolean hasPermission(String userId, String actionKey);
}
