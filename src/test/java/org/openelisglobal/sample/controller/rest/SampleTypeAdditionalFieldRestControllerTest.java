package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@RunWith(MockitoJUnitRunner.class)
public class SampleTypeAdditionalFieldRestControllerTest {

    @Mock
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;

    @InjectMocks
    private SampleTypeAdditionalFieldRestController controller;

    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        UserSessionData usd = new UserSessionData();
        usd.setSytemUserId(22);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, usd);
    }

    @Test
    public void getFieldsForSampleType_shouldReturnFields() {
        SampleTypeAdditionalFieldPayload payload = new SampleTypeAdditionalFieldPayload();
        payload.setId(10);
        payload.setSampleTypeId("1");
        payload.setFieldKey("batch_code");
        payload.setDisplayName("Batch Code");
        payload.setFieldType("TEXT");

        when(sampleTypeAdditionalFieldService.getFieldsForSampleType("1", false))
                .thenReturn(Collections.singletonList(payload));

        List<SampleTypeAdditionalFieldPayload> response = controller.getFieldsForSampleType("1", false);

        assertEquals(1, response.size());
        assertEquals(Integer.valueOf(10), response.get(0).getId());
        assertEquals("batch_code", response.get(0).getFieldKey());
    }

    @Test
    public void getFieldsForSampleType_shouldTranslateIllegalArgumentIntoBadRequest() {
        when(sampleTypeAdditionalFieldService.getFieldsForSampleType("x", false))
                .thenThrow(new IllegalArgumentException("sampleTypeId must be numeric"));

        boolean thrown = false;
        try {
            controller.getFieldsForSampleType("x", false);
        } catch (ResponseStatusException e) {
            thrown = true;
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }

        assertTrue(thrown);
    }

    @Test
    public void createField_shouldReturnCreatedAndUseSessionUserId() {
        SampleTypeAdditionalFieldPayload requestPayload = new SampleTypeAdditionalFieldPayload();
        requestPayload.setSampleTypeId("1");
        requestPayload.setDisplayName("Storage Condition");
        requestPayload.setFieldType("TEXT");

        SampleTypeAdditionalFieldPayload createdPayload = new SampleTypeAdditionalFieldPayload();
        createdPayload.setId(101);
        createdPayload.setSampleTypeId("1");
        createdPayload.setFieldKey("storage_condition");
        createdPayload.setDisplayName("Storage Condition");

        when(sampleTypeAdditionalFieldService.createField(any(SampleTypeAdditionalFieldPayload.class), eq("22")))
                .thenReturn(createdPayload);

        ResponseEntity<SampleTypeAdditionalFieldPayload> response = controller.createField(request, requestPayload);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Integer.valueOf(101), response.getBody().getId());
        verify(sampleTypeAdditionalFieldService).createField(requestPayload, "22");
    }

    @Test
    public void createField_shouldTranslateIllegalArgumentIntoBadRequest() {
        SampleTypeAdditionalFieldPayload requestPayload = new SampleTypeAdditionalFieldPayload();
        requestPayload.setSampleTypeId("1");
        requestPayload.setDisplayName("Duplicated");
        requestPayload.setFieldType("TEXT");

        when(sampleTypeAdditionalFieldService.createField(any(SampleTypeAdditionalFieldPayload.class), eq("22")))
                .thenThrow(new IllegalArgumentException("Field key already exists"));

        boolean thrown = false;
        try {
            controller.createField(request, requestPayload);
        } catch (ResponseStatusException e) {
            thrown = true;
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }

        assertTrue(thrown);
    }

    @Test
    public void deactivateField_shouldReturnNoContent() {
        doNothing().when(sampleTypeAdditionalFieldService).deactivateField(101, "22");

        ResponseEntity<Void> response = controller.deactivateField(request, 101);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(sampleTypeAdditionalFieldService).deactivateField(101, "22");
    }

    @Test
    public void deactivateField_shouldTranslateIllegalArgumentIntoBadRequest() {
        doThrow(new IllegalArgumentException("Field not found")).when(sampleTypeAdditionalFieldService)
                .deactivateField(999, "22");

        boolean thrown = false;
        try {
            controller.deactivateField(request, 999);
        } catch (ResponseStatusException e) {
            thrown = true;
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }

        assertTrue(thrown);
    }
}
