package org.openelisglobal.orderadditionalfield.service;

import org.openelisglobal.test.valueholder.Test;

/** Test exceptions never change the workflow classification of their order. */
public final class AlternateOrderFlowPolicy {
    private AlternateOrderFlowPolicy() {
    }

    public static boolean allowsProcessing(boolean alternateOrderFlow, Test test) {
        return !alternateOrderFlow || isResultsOnly(alternateOrderFlow, test);
    }

    public static boolean isResultsOnly(boolean alternateOrderFlow, Test test) {
        return alternateOrderFlow && test != null && Boolean.TRUE.equals(test.getAllowProcessingInAlternateFlow());
    }
}
