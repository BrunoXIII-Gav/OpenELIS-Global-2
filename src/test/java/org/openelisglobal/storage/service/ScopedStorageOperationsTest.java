package org.openelisglobal.storage.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.service.SampleItemService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.storage.controller.SampleStorageRestController;
import org.openelisglobal.storage.dao.SampleStorageAssignmentDAO;
import org.openelisglobal.storage.dao.StorageDeviceDAO;
import org.openelisglobal.storage.dao.StorageRoomDAO;
import org.openelisglobal.storage.dao.StorageSampleScopeDAO;
import org.openelisglobal.storage.valueholder.SampleStorageAssignment;
import org.openelisglobal.storage.valueholder.StorageRoom;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class ScopedStorageOperationsTest {
    @Mock
    private StorageAuthorizationService authorization;
    @Mock
    private SampleItemDAO sampleItemDAO;
    @Mock
    private SampleItemService sampleItemService;
    @Mock
    private SampleService sampleService;
    @Mock
    private SampleStorageAssignmentDAO sampleStorageAssignmentDAO;
    @Mock
    private StorageLocationService storageLocationService;
    @Mock
    private IStatusService statusService;
    @InjectMocks
    private SampleStorageServiceImpl samples;

    private SampleItem item(String id, String externalId) {
        SampleItem item = new SampleItem();
        item.setId(id);
        item.setExternalId(externalId);
        return item;
    }

    @Before
    public void scope() {
        lenient().when(authorization.getAccessibleSampleItemIdsForCurrentUser("read")).thenReturn(Set.of("43", "44"));
    }

    private void specimenList() {
        when(sampleItemDAO.getAllSampleItems()).thenReturn(List.of(item("99", "OTHER"), item("43", "OWN"), item("44", "OWN-ALIQUOT")));
    }

    @Test
    public void listAndMetricsExcludeOtherUnitsIncludingUnassignedSpecimens() {
        specimenList();
        List<Map<String, Object>> result = samples.getAllSamplesWithAssignments();
        assertEquals(List.of("43", "44"), result.stream().map(row -> row.get("id")).toList());
        var metrics = samples.getStorageMetrics();
        assertEquals(2, metrics.get("totalSampleItems"));
        assertEquals(2L, metrics.get("active"));
        assertEquals(0L, metrics.get("disposed"));
    }

    @Test
    public void searchDoesNotFindForeignInternalIdsOrExternalIds() {
        specimenList();
        StorageSearchServiceImpl search = new StorageSearchServiceImpl();
        ReflectionTestUtils.setField(search, "sampleStorageService", samples);
        OrderAdditionalFieldService additional = mock(OrderAdditionalFieldService.class);
        when(additional.findSampleIdBySearchableFieldValue(anyString())).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(search, "orderAdditionalFieldService", additional);
        assertTrue(search.searchSamples("OTHER").isEmpty());
        assertTrue(search.searchSamples("99").isEmpty());
        assertEquals(2, search.searchSamples("OWN").size());
    }

    @Test
    public void paginationAndTotalApplyAfterUnitFiltering() {
        specimenList();
        StorageDashboardServiceImpl dashboard = new StorageDashboardServiceImpl();
        ReflectionTestUtils.setField(dashboard, "sampleStorageService", samples);
        SampleStorageRestController controller = new SampleStorageRestController();
        ReflectionTestUtils.setField(controller, "storageDashboardService", dashboard);
        ReflectionTestUtils.setField(controller, "sampleStorageService", samples);
        Map<?, ?> body = (Map<?, ?>) controller.getSampleItems(null, null, null, 0, 5).getBody();
        assertEquals(2, body.get("totalItems"));
        assertEquals(1, body.get("totalPages"));
        assertEquals(2, ((List<?>) body.get("items")).size());
        Map<?, ?> second = (Map<?, ?>) controller.getSampleItems(null, null, null, 1, 5).getBody();
        assertTrue(((List<?>) second.get("items")).isEmpty());
        List<?> metrics = (List<?>) controller.getSampleItems("true", null, null, 0, 5).getBody();
        assertEquals(2, ((Map<?, ?>) metrics.getFirst()).get("totalSampleItems"));
    }

    @Test
    public void assignmentPagesExcludeForeignSpecimensBeforeCounting() {
        SampleStorageAssignment own = new SampleStorageAssignment();
        own.setSampleItemId(43);
        SampleStorageAssignment foreign = new SampleStorageAssignment();
        foreign.setSampleItemId(99);
        when(sampleStorageAssignmentDAO.getAll()).thenReturn(List.of(foreign, own));
        var page = samples.getSampleAssignments(PageRequest.of(0, 1));
        assertEquals(1L, page.getTotalElements());
        assertEquals(List.of(own), page.getContent());
        verify(sampleStorageAssignmentDAO, never()).findAll(any());
    }

    @Test
    public void metadataCannotOverwriteAnOccupiedForeignCoordinate() {
        SampleStorageAssignment own = new SampleStorageAssignment();
        own.setSampleItemId(43);
        own.setLocationId(1);
        own.setLocationType("box");
        own.setPositionCoordinate("A1");
        SampleStorageAssignment foreign = new SampleStorageAssignment();
        foreign.setSampleItemId(99);
        when(sampleStorageAssignmentDAO.findBySampleItemId("43")).thenReturn(own);
        when(sampleStorageAssignmentDAO.findByBoxAndCoordinate(1, "A2")).thenReturn(foreign);
        assertThrows(LIMSRuntimeException.class, () -> samples.updateAssignmentMetadata("43", "A2", "notes"));
        assertEquals("A1", own.getPositionCoordinate());
        verify(sampleStorageAssignmentDAO, never()).update(any());
    }

    @Test
    public void roomSpecimenCountUsesUnitScopeWhileLocationsRemainShared() {
        StorageLocationServiceImpl locations = new StorageLocationServiceImpl();
        StorageRoomDAO roomDAO = mock(StorageRoomDAO.class);
        StorageDeviceDAO deviceDAO = mock(StorageDeviceDAO.class);
        StorageSampleScopeDAO scopeDAO = mock(StorageSampleScopeDAO.class);
        ReflectionTestUtils.setField(locations, "storageAuthorizationService", authorization);
        ReflectionTestUtils.setField(locations, "storageRoomDAO", roomDAO);
        ReflectionTestUtils.setField(locations, "storageDeviceDAO", deviceDAO);
        ReflectionTestUtils.setField(locations, "storageSampleScopeDAO", scopeDAO);
        StorageRoom room = new StorageRoom();
        room.setId(1);
        room.setName("Shared room");
        when(roomDAO.getAll()).thenReturn(List.of(room));
        when(scopeDAO.findAssignedSampleItemIdsInHierarchy("room", 1)).thenReturn(Set.of("43", "99"));
        var rooms = locations.getRoomsForAPI();
        assertEquals(1, rooms.size());
        assertEquals("Shared room", rooms.getFirst().get("name"));
        assertEquals(1, rooms.getFirst().get("sampleCount"));
    }

    @Test public void flexibleIdentifierAssignmentIsCheckedBeforeAnyWrite() {
        when(sampleItemService.get("99")).thenReturn(item("99", "OTHER"));
        doThrow(new AccessDeniedException("denied")).when(authorization).requireSampleAccess("99", "update");
        assertThrows(AccessDeniedException.class, () -> samples.assignSampleItemWithLocation("99", "1", "device", null, null));
        verifyZeroInteractions(sampleStorageAssignmentDAO, storageLocationService);
    }

    @Test
    public void externalReferenceCannotBypassTheSpecimenScope() {
        when(sampleItemService.getSampleItemsByExternalID("OTHER-REFERENCE")).thenReturn(List.of(item("99", "OTHER-REFERENCE")));
        doThrow(new AccessDeniedException("denied")).when(authorization).requireSampleAccess("99", "update");
        assertThrows(AccessDeniedException.class, () -> samples.assignSampleItemWithLocation("OTHER-REFERENCE", "1", "device", null, null));
        verifyZeroInteractions(sampleStorageAssignmentDAO, storageLocationService);
    }

    @Test
    public void accessionReferenceCannotBypassTheSpecimenScope() {
        Sample order = new Sample();
        order.setId("1");
        when(sampleService.getSampleByAccessionNumber("OTHER-ORDER")).thenReturn(order);
        when(sampleItemService.getSampleItemsBySampleId("1")).thenReturn(List.of(item("99", "OTHER-REFERENCE")));
        doThrow(new AccessDeniedException("denied")).when(authorization).requireSampleAccess("99", "manage");
        assertThrows(AccessDeniedException.class,
                () -> samples.disposeSampleItem("OTHER-ORDER", "reason", "method", null));
        verifyZeroInteractions(sampleStorageAssignmentDAO, storageLocationService, statusService);
    }

    @Test public void movementAndDisposalDoNotModifyForeignSpecimens() {
        when(sampleItemService.get("99")).thenReturn(item("99", "OTHER"));
        doThrow(new AccessDeniedException("denied")).when(authorization).requireSampleAccess(eq("99"), anyString());
        assertThrows(AccessDeniedException.class, () -> samples.moveSampleItemWithLocation("99", "1", "device", null, "reason", null));
        assertThrows(AccessDeniedException.class, () -> samples.disposeSampleItem("99", "reason", "method", null));
        verifyZeroInteractions(sampleStorageAssignmentDAO, storageLocationService, statusService);
    }

    @Test
    public void metadataAndDirectDetailRequestsAreProtected() {
        doThrow(new AccessDeniedException("denied")).when(authorization).requireSampleAccess(eq("99"), anyString());
        assertThrows(AccessDeniedException.class, () -> samples.updateAssignmentMetadata("99", "A2", "notes"));
        assertThrows(AccessDeniedException.class, () -> samples.getSampleItemLocationForStorage("99"));
        verifyZeroInteractions(sampleStorageAssignmentDAO);
    }

    @Test
    public void controllerPreserves403InsteadOfTurningDenialInto500() {
        SampleStorageRestController controller = new SampleStorageRestController();
        ReflectionTestUtils.setField(controller, "sampleStorageService", samples);
        doThrow(new AccessDeniedException("denied")).when(authorization).requireSampleAccess("99", "read");
        assertThrows(AccessDeniedException.class, () -> controller.getSampleItemLocation("99"));
    }

    @Test
    public void sharedLocationUpdateAndCascadeStopBeforeMutation() {
        StorageLocationServiceImpl locations = new StorageLocationServiceImpl();
        StorageRoomDAO roomDAO = mock(StorageRoomDAO.class);
        ReflectionTestUtils.setField(locations, "storageAuthorizationService", authorization);
        ReflectionTestUtils.setField(locations, "storageRoomDAO", roomDAO);
        StorageRoom room = new StorageRoom();
        room.setId(1);
        when(roomDAO.get(1)).thenReturn(Optional.of(room));
        doThrow(new AccessDeniedException("foreign specimens")).when(authorization).requireLocationAccess("room", 1,
                "manage");
        assertThrows(AccessDeniedException.class, () -> locations.update(room));
        assertThrows(AccessDeniedException.class, () -> locations.updateRoom(1, room));
        assertThrows(AccessDeniedException.class, () -> locations.deleteLocationWithCascade(1, StorageRoom.class));
        verify(roomDAO, never()).update(any());
        verify(roomDAO, never()).delete(any());
    }

    @Test
    public void hierarchyUnassignmentUsesTypeAndIdRatherThanCollidingNumericIds() {
        StorageLocationServiceImpl locations = new StorageLocationServiceImpl();
        StorageSampleScopeDAO scopeDAO = mock(StorageSampleScopeDAO.class);
        ReflectionTestUtils.setField(locations, "storageSampleScopeDAO", scopeDAO);
        ReflectionTestUtils.setField(locations, "sampleStorageAssignmentDAO", sampleStorageAssignmentDAO);
        org.openelisglobal.storage.valueholder.StorageDevice device = new org.openelisglobal.storage.valueholder.StorageDevice();
        device.setId(1);
        SampleStorageAssignment own = new SampleStorageAssignment();
        own.setLocationType("device");
        own.setLocationId(1);
        own.setSampleItemId(43);
        when(scopeDAO.findAssignmentsInHierarchy("device", 1)).thenReturn(List.of(own));
        ReflectionTestUtils.invokeMethod(locations, "unassignSamplesFromHierarchy", device);
        verify(scopeDAO).findAssignmentsInHierarchy("device", 1);
        verify(sampleStorageAssignmentDAO).delete(own);
        verifyNoMoreInteractions(sampleStorageAssignmentDAO);
    }
}
