package org.openelisglobal.sample.exception;

public class OrderDocumentConsentRequiredException extends RuntimeException {
    public static final String CODE = "order.document.download.required";
    private final String fieldKey;

    public OrderDocumentConsentRequiredException(String fieldKey) {
        super(CODE);
        this.fieldKey = fieldKey;
    }

    public String getFieldKey() {
        return fieldKey;
    }
}
