package org.openelisglobal.patient.util;

import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.patientidentitytype.service.PatientIdentityTypeService;
import org.openelisglobal.patientidentitytype.valueholder.PatientIdentityType;
import org.openelisglobal.spring.util.SpringContext;

public final class PatientIdentifierUtil {

    public static final String PRIMARY_IDENTIFIER_DNI = "DNI";
    public static final String PRIMARY_IDENTIFIER_PASSPORT = "PASSPORT";
    public static final String PRIMARY_IDENTIFIER_FOREIGN_ID = "FOREIGN_ID";
    public static final String NATIONAL_ID_SOURCE = "NATIONAL_ID_SOURCE";

    private PatientIdentifierUtil() {
    }

    public static void synchronizeDerivedNationalId(PatientManagementInfo patientInfo) {
        if (patientInfo == null) {
            return;
        }

        if (!usesDerivedNationalIdMode(patientInfo)) {
            return;
        }

        String primaryType = determinePrimaryIdentifierType(patientInfo);
        patientInfo.setPrimaryPatientIdentifierType(primaryType);
        patientInfo.setNationalId(StringUtils.defaultString(getValueForType(patientInfo, primaryType)));
    }

    public static String determinePrimaryIdentifierType(PatientManagementInfo patientInfo) {
        if (patientInfo == null) {
            return "";
        }

        String explicitType = normalizePrimaryIdentifierType(patientInfo.getPrimaryPatientIdentifierType());
        if (StringUtils.isNotBlank(explicitType)) {
            return explicitType;
        }

        return inferPrimaryIdentifierType(patientInfo.getNationalId(), patientInfo.getDni(), patientInfo.getPassportNumber(),
                patientInfo.getForeignId());
    }

    public static String inferPrimaryIdentifierType(String nationalId, String dni, String passportNumber,
            String foreignId) {
        String normalizedNationalId = StringUtils.trimToEmpty(nationalId);
        String normalizedDni = StringUtils.trimToEmpty(dni);
        String normalizedPassport = StringUtils.trimToEmpty(passportNumber);
        String normalizedForeignId = StringUtils.trimToEmpty(foreignId);

        if (StringUtils.isNotBlank(normalizedNationalId)) {
            if (normalizedNationalId.equals(normalizedDni)) {
                return PRIMARY_IDENTIFIER_DNI;
            }
            if (normalizedNationalId.equals(normalizedPassport)) {
                return PRIMARY_IDENTIFIER_PASSPORT;
            }
            if (normalizedNationalId.equals(normalizedForeignId)) {
                return PRIMARY_IDENTIFIER_FOREIGN_ID;
            }
        }

        if (StringUtils.isNotBlank(normalizedDni)) {
            return PRIMARY_IDENTIFIER_DNI;
        }
        if (StringUtils.isNotBlank(normalizedPassport)) {
            return PRIMARY_IDENTIFIER_PASSPORT;
        }
        if (StringUtils.isNotBlank(normalizedForeignId)) {
            return PRIMARY_IDENTIFIER_FOREIGN_ID;
        }

        return "";
    }

    public static String getValueForType(PatientManagementInfo patientInfo, String type) {
        if (patientInfo == null) {
            return "";
        }

        String normalizedType = normalizePrimaryIdentifierType(type);
        if (PRIMARY_IDENTIFIER_DNI.equals(normalizedType)) {
            return StringUtils.trimToEmpty(patientInfo.getDni());
        }
        if (PRIMARY_IDENTIFIER_PASSPORT.equals(normalizedType)) {
            return StringUtils.trimToEmpty(patientInfo.getPassportNumber());
        }
        if (PRIMARY_IDENTIFIER_FOREIGN_ID.equals(normalizedType)) {
            return StringUtils.trimToEmpty(patientInfo.getForeignId());
        }

        return "";
    }

    public static String normalizePrimaryIdentifierType(String type) {
        String normalized = StringUtils.trimToEmpty(type).toUpperCase(Locale.ROOT);
        if (PRIMARY_IDENTIFIER_DNI.equals(normalized) || PRIMARY_IDENTIFIER_PASSPORT.equals(normalized)
                || PRIMARY_IDENTIFIER_FOREIGN_ID.equals(normalized)) {
            return normalized;
        }
        return "";
    }

    public static boolean hasAtLeastOnePatientIdentifier(PatientManagementInfo patientInfo) {
        return StringUtils.isNotBlank(StringUtils.trimToEmpty(patientInfo.getDni()))
                || StringUtils.isNotBlank(StringUtils.trimToEmpty(patientInfo.getPassportNumber()))
                || StringUtils.isNotBlank(StringUtils.trimToEmpty(patientInfo.getForeignId()));
    }

    public static boolean usesDerivedNationalIdMode(PatientManagementInfo patientInfo) {
        if (patientInfo == null) {
            return false;
        }

        return hasAtLeastOnePatientIdentifier(patientInfo)
                || StringUtils.isNotBlank(normalizePrimaryIdentifierType(patientInfo.getPrimaryPatientIdentifierType()));
    }

    public static String getStoredPrimaryIdentifierType(List<PatientIdentity> identityList) {
        if (identityList == null || identityList.isEmpty()) {
            return "";
        }
        PatientIdentityTypeService identityTypeService = SpringContext.getBean(PatientIdentityTypeService.class);
        PatientIdentityType identityType = identityTypeService.getNamedIdentityType(NATIONAL_ID_SOURCE);
        if (identityType == null) {
            return "";
        }
        for (PatientIdentity identity : identityList) {
            if (identityType.getId().equals(identity.getIdentityTypeId())) {
                return normalizePrimaryIdentifierType(identity.getIdentityData());
            }
        }
        return "";
    }
}
