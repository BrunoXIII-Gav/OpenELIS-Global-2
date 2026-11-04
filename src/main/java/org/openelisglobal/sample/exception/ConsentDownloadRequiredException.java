package org.openelisglobal.sample.exception;

public class ConsentDownloadRequiredException extends RuntimeException {
    public static final String CODE = "consent.download.requiredBeforeSubmit";

    public ConsentDownloadRequiredException() {
        super(CODE);
    }
}
