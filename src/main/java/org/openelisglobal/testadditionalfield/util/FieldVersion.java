package org.openelisglobal.testadditionalfield.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;

public final class FieldVersion {
    private FieldVersion() {
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.trimToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String of(AnalysisAdditionalFieldValue value) {
        return (value == null ? "absent" : value.getId() + ":" + value.getLastupdated()) + "|"
                + hash(value == null ? null : value.getFieldValue());
    }

    public static boolean unchanged(String version, String submitted) {
        return version != null && version.endsWith("|" + hash(submitted));
    }
}
