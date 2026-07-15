package org.openelisglobal.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.constants.Constants;
import org.springframework.security.core.GrantedAuthority;

public final class SamlRoleMapping {

    private SamlRoleMapping() {
    }

    public static Set<String> toInternalRoleNames(Collection<? extends GrantedAuthority> authorities) {
        Set<String> mappedRoles = new LinkedHashSet<>();
        if (authorities == null) {
            return mappedRoles;
        }

        for (GrantedAuthority authority : authorities) {
            if (authority == null) {
                continue;
            }
            ParsedSamlRole parsedRole = parseAuthority(authority.getAuthority());
            String mappedRole = parsedRole == null ? null : parsedRole.getInternalRoleName();
            if (!GenericValidator.isBlankOrNull(mappedRole)) {
                mappedRoles.add(mappedRole);
            }
        }

        return mappedRoles;
    }

    public static String toInternalRoleName(String authority) {
        ParsedSamlRole parsedRole = parseAuthority(authority);
        return parsedRole == null ? null : parsedRole.getInternalRoleName();
    }

    public static ParsedSamlRole parseAuthority(String authority) {
        String normalized = normalizeAuthority(authority);
        if (GenericValidator.isBlankOrNull(normalized)) {
            return null;
        }

        String[][] mappings = {
                { "user-account-administrator", Constants.ROLE_USER_ACCOUNT_ADMIN },
                { "user-account-admin", Constants.ROLE_USER_ACCOUNT_ADMIN },
                { "global-administrator", Constants.ROLE_GLOBAL_ADMIN },
                { "global-admin", Constants.ROLE_GLOBAL_ADMIN },
                { "validation-biologist", Constants.ROLE_VALIDATION_BIOLOGIST },
                { "validation-medical", Constants.ROLE_VALIDATION_MEDICAL },
                { "generic-sample", Constants.ROLE_GENERIC_SAMPLE },
                { "analyser-import", Constants.ROLE_ANALYSER_IMPORT },
                { "analyzer-import", Constants.ROLE_ANALYSER_IMPORT },
                { "audit-trail", Constants.ROLE_AUDIT_TRAIL },
                { "admin", Constants.ROLE_GLOBAL_ADMIN },
                { "audit", Constants.ROLE_AUDIT_TRAIL },
                { "administration", Constants.ROLE_ADMINISTRATION },
                { "reception", Constants.ROLE_RECEPTION },
                { "order", Constants.ROLE_ORDER },
                { "patient", Constants.ROLE_PATIENT },
                { "results", Constants.ROLE_RESULTS },
                { "aliquot", Constants.ROLE_ALIQUOT },
                { "validation", Constants.ROLE_VALIDATION },
                { "reports", Constants.ROLE_REPORTS },
                { "storage", Constants.ROLE_STORAGE },
                { "pathologist", Constants.ROLE_PATHOLOGIST } };

        for (String[] mapping : mappings) {
            String token = mapping[0];
            if (normalized.equals(token)) {
                return new ParsedSamlRole(mapping[1], null);
            }
            if (normalized.startsWith(token + "-")) {
                return new ParsedSamlRole(mapping[1], normalized.substring(token.length() + 1));
            }
        }

        return null;
    }

    private static String normalizeAuthority(String authority) {
        if (GenericValidator.isBlankOrNull(authority)) {
            return null;
        }

        String normalized = authority.trim();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        normalized = normalized.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        if (normalized.startsWith("oeg-")) {
            normalized = normalized.substring("oeg-".length());
        }
        return normalized;
    }

    public static final class ParsedSamlRole {
        private final String internalRoleName;
        private final String labScope;

        private ParsedSamlRole(String internalRoleName, String labScope) {
            this.internalRoleName = internalRoleName;
            this.labScope = labScope;
        }

        public String getInternalRoleName() {
            return internalRoleName;
        }

        public String getLabScope() {
            return labScope;
        }
    }
}
