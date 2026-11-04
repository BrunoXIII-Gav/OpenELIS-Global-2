package org.openelisglobal.sample.exception;

import org.openelisglobal.common.exception.LIMSRuntimeException;

public class OrderTestHasResultsException extends LIMSRuntimeException {
    public static final String CODE = "order.edit.test.hasResults";

    public OrderTestHasResultsException() {
        super(CODE);
    }
}
