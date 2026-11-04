package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;

import org.junit.Test;

public class AlternateOrderFlowPolicyTest {
    @Test
    public void optionDefaultsOffAndDoesNotChangeNormalOrders() {
        var test = new org.openelisglobal.test.valueholder.Test();
        assertFalse(test.getAllowProcessingInAlternateFlow());
        assertTrue(AlternateOrderFlowPolicy.allowsProcessing(false, test));
        assertFalse(AlternateOrderFlowPolicy.allowsProcessing(true, test));
        test.setAllowProcessingInAlternateFlow(true);
        assertTrue(AlternateOrderFlowPolicy.allowsProcessing(false, test));
        assertFalse(AlternateOrderFlowPolicy.isResultsOnly(false, test));
        assertTrue(AlternateOrderFlowPolicy.isResultsOnly(true, test));
    }

    @Test
    public void parentCompletionFlagIsIndependentInAllCombinations() {
        for (boolean parent : new boolean[] { false, true }) {
            for (boolean exception : new boolean[] { false, true }) {
                var test = new org.openelisglobal.test.valueholder.Test();
                test.setSkipValidationWhenParentComplete(parent);
                test.setAllowProcessingInAlternateFlow(exception);
                assertEquals(exception, AlternateOrderFlowPolicy.allowsProcessing(true, test));
                assertEquals(parent, test.getSkipValidationWhenParentComplete());
            }
        }
    }
}
