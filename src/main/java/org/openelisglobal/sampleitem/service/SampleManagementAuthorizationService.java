package org.openelisglobal.sampleitem.service;

/** Resolves the generic sample actions granted to the current user. */
public interface SampleManagementAuthorizationService {

    SampleManagementAccess getAccess(String userId);
}
