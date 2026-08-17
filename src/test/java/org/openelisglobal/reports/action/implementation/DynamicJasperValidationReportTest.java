package org.openelisglobal.reports.action.implementation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import org.junit.Test;

public class DynamicJasperValidationReportTest {

    @Test
    public void parseNumericValue_shouldSupportPlainDecimals() {
        assertEquals(new BigDecimal("19.75"), DynamicJasperValidationReport.parseNumericValue("19.75"));
    }

    @Test
    public void parseNumericValue_shouldSupportDecimalComma() {
        assertEquals(new BigDecimal("19.75"), DynamicJasperValidationReport.parseNumericValue("19,75"));
    }

    @Test
    public void parseNumericValue_shouldSupportThousandsSeparators() {
        assertEquals(new BigDecimal("1234.56"), DynamicJasperValidationReport.parseNumericValue("1,234.56"));
        assertEquals(new BigDecimal("1234.56"), DynamicJasperValidationReport.parseNumericValue("1.234,56"));
    }

    @Test
    public void parseNumericValue_shouldReturnNullForInvalidValues() {
        assertNull(DynamicJasperValidationReport.parseNumericValue(null));
        assertNull(DynamicJasperValidationReport.parseNumericValue(""));
        assertNull(DynamicJasperValidationReport.parseNumericValue("abc"));
    }

    @Test
    public void evaluateNumericCondition_shouldSupportLessThanAndGreaterThan() {
        assertTrue(DynamicJasperValidationReport.evaluateNumericCondition(new BigDecimal("10"), "<",
                new BigDecimal("20")));
        assertTrue(DynamicJasperValidationReport.evaluateNumericCondition(new BigDecimal("20"), ">=",
                new BigDecimal("20")));
        assertFalse(DynamicJasperValidationReport.evaluateNumericCondition(new BigDecimal("30"), "lt",
                new BigDecimal("20")));
    }

    @Test
    public void evaluateNumericCondition_shouldSupportEquality() {
        assertTrue(DynamicJasperValidationReport.evaluateNumericCondition(new BigDecimal("20.0"), "=",
                new BigDecimal("20.00")));
        assertFalse(DynamicJasperValidationReport.evaluateNumericCondition(new BigDecimal("19.99"), "eq",
                new BigDecimal("20")));
    }

    @Test
    public void extractCollectionDate_shouldReturnOnlyDateFromDateTime() {
        assertEquals("08/15/2026", DynamicJasperValidationReport.extractCollectionDate("08/15/2026 04:14"));
    }

    @Test
    public void extractCollectionDate_shouldReturnOnlyDateFromCompositeCollectionSummary() {
        assertEquals("08/15/2026",
                DynamicJasperValidationReport.extractCollectionDate("Sangre LNG0000000000050-1 08/15/2026 04:14"));
    }
}
