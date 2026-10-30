package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;

public class OrderDashboardCugServiceTest {
    @Test
    public void includesEverySampleCugWithoutDuplicatingOrderRows() {
        var dao = mock(SampleItemDAO.class);
        when(dao.findCugCodesByAccessionNumbers(List.of("LNG1", "LNG2"))).thenReturn(List.of(
                new Object[] { "LNG1", "5415.1" }, new Object[] { "LNG1", "5415.2" }, new Object[] { "LNG1", "5415.1" },
                new Object[] { "LNG2", " 5416.1 " }, new Object[] { "LNG2", " " }));
        var first = order("LNG1");
        var second = order("LNG2");
        var rows = new ArrayList<>(List.of(first, second));
        new OrderDashboardCugServiceImpl(dao).populateOrderCugs(rows);
        assertEquals(2, rows.size());
        assertEquals("5415.1, 5415.2", first.getCugCode());
        assertEquals("5416.1", second.getCugCode());
        assertEquals("LNG1", first.getLabNumber());
        verify(dao).findCugCodesByAccessionNumbers(List.of("LNG1", "LNG2"));
    }

    @Test
    public void missingCugIsEmptyRatherThanAnInventedCodeOrLaboratoryNumber() {
        var dao = mock(SampleItemDAO.class);
        var row = order("LNG1");
        row.setCugCode("stale");
        new OrderDashboardCugServiceImpl(dao).populateOrderCugs(List.of(row));
        assertEquals("", row.getCugCode());
    }

    @Test
    public void noOrdersOrLocalOrderNumbersDoNotQueryTheDatabase() {
        var dao = mock(SampleItemDAO.class);
        var service = new OrderDashboardCugServiceImpl(dao);
        service.populateOrderCugs(null);
        service.populateOrderCugs(List.of());
        service.populateOrderCugs(List.of(order("")));
        verifyZeroInteractions(dao);
    }

    private OrderDisplayBean order(String number) {
        var bean = new OrderDisplayBean();
        bean.setLabNumber(number);
        return bean;
    }
}
