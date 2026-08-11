import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import PatientHistorySummaryPanel from "./PatientHistorySummaryPanel";

jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
}));

const { getFromOpenElisServer } = require("../../utils/Utils");

const messages = {
  "label.page.patientHistory": "Patient History",
  "patientHistory.empty.orders": "No patient orders found.",
  "patientHistory.empty.results": "No patient results found.",
  "patientHistory.empty.samples": "No patient samples found.",
  "patientHistory.empty.storage": "No stored patient samples found.",
  "patientHistory.detail.additionalFields": "Additional sample fields",
  "patientHistory.detail.collectionData": "Sample collection data",
  "patientHistory.detail.empty.collectionData":
    "No collection data was captured for this sample.",
  "patientHistory.detail.empty.additionalFields":
    "No additional sample fields were captured for this record.",
  "patientHistory.detail.empty.orderData":
    "No associated order or reception fields were captured for this record.",
  "patientHistory.detail.empty.receptionData":
    "No reception data was captured for this sample.",
  "patientHistory.detail.orderInfo": "Order information",
  "patientHistory.detail.receptionData": "Sample reception",
  "patientHistory.detail.empty.resultValues":
    "This result does not have reportable values yet.",
  "patientHistory.detail.orderData": "Associated order and reception",
  "patientHistory.detail.resultData": "Result summary",
  "patientHistory.detail.resultValues": "Reported values",
  "patientHistory.detail.sampleData": "Sample summary",
  "patientHistory.detail.testFields": "Test fields",
  "patientHistory.filters.fromDate": "From date",
  "patientHistory.filters.search": "Lab or order number",
  "patientHistory.filters.searchPlaceholder":
    "Filter by lab number, order number, requester, test...",
  "patientHistory.filters.toDate": "To date",
  "patientHistory.metrics.completedTests": "Completed tests",
  "patientHistory.metrics.orders": "Orders",
  "patientHistory.metrics.pendingTests": "Pending tests",
  "patientHistory.metrics.samples": "Samples",
  "patientHistory.metrics.storedSamples": "Stored samples",
  "patientHistory.metrics.totalTests": "Total tests",
  "patientHistory.summary.subtitle":
    "A read-only timeline of the patient's orders, samples, storage, and test activity.",
  "patientHistory.summary.title": "Patient history overview",
  "patientHistory.summary.unavailable":
    "Patient history summary is temporarily unavailable.",
  "patientHistory.table.accessionNumber": "Accession number",
  "patientHistory.table.assignedDate": "Assigned date",
  "patientHistory.table.clinicalOrderId": "Order reference",
  "patientHistory.table.collectionDate": "Collection date",
  "patientHistory.table.completedTests": "Completed tests",
  "patientHistory.table.details": "Details",
  "patientHistory.table.hideDetails": "Hide",
  "patientHistory.table.location": "Storage location",
  "patientHistory.table.notes": "Notes",
  "patientHistory.table.parentSample": "Parent sample",
  "patientHistory.table.pendingTests": "Pending tests",
  "patientHistory.table.position": "Position",
  "patientHistory.table.priority": "Priority",
  "patientHistory.table.receivedDate": "Received date",
  "patientHistory.table.referringSite": "Facility",
  "patientHistory.table.requestDate": "Request date",
  "patientHistory.table.requester": "Requester",
  "patientHistory.table.resultDate": "Result date",
  "patientHistory.table.resultStatus": "Result status",
  "patientHistory.table.sampleItem": "Sample item",
  "patientHistory.table.sampleIdentifier": "Sample identifier",
  "patientHistory.table.sampleStatus": "Sample status",
  "patientHistory.table.sampleType": "Sample type",
  "patientHistory.table.status": "Status",
  "patientHistory.table.testName": "Test",
  "patientHistory.table.totalTests": "Total tests",
  "patientHistory.tabs.orders": "Orders",
  "patientHistory.tabs.results": "Results",
  "patientHistory.tabs.samples": "Samples",
  "patientHistory.tabs.storage": "Stored samples",
  "patientHistory.table.viewDetails": "View",
  "yes.option": "Yes",
  "no.option": "No",
};

const renderComponent = () =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <PatientHistorySummaryPanel patientId="77" />
    </IntlProvider>,
  );

describe("PatientHistorySummaryPanel", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders fetched patient history summary", async () => {
    getFromOpenElisServer.mockImplementation((endpoint, callback) => {
      callback({
        patientId: "77",
        metrics: {
          totalOrders: 2,
          totalSamples: 3,
          storedSamples: 1,
          totalTests: 7,
          completedTests: 5,
          pendingTests: 2,
        },
        orders: [
          {
            id: "1",
            accessionNumber: "LAB-1001",
            clinicalOrderId: "ORD-22",
            clientReference: "",
            priority: "STAT",
            requestDate: "07/09/2026",
            receivedDate: "07/10/2026",
            status: "Finished",
            referringSiteName: "Central Clinic",
            requesterName: "Garcia, Ana",
            orderFields: [],
          },
        ],
        samples: [
          {
            id: "11",
            accessionNumber: "LAB-1001",
            clinicalOrderId: "ORD-22",
            sampleItemExternalId: "LAB-1001-1",
            sampleType: "Blood",
            collectionDate: "07/09/2026",
            status: "Entered",
            parentSampleItemExternalId: "",
            storageLocation: "Room A > Rack 1 > Box 2",
            storageAssignedDate: "2026-07-11 09:00:00",
            storagePositionCoordinate: "A2",
            storageNotes: "Frozen",
            totalTests: 3,
            completedTests: 2,
            pendingTests: 1,
            stored: true,
            collectionFields: [],
            receptionFields: [],
            fixedFields: [],
            orderFields: [],
            additionalFields: [],
          },
        ],
        results: [
          {
            id: "200",
            accessionNumber: "LAB-1001",
            clinicalOrderId: "ORD-22",
            sampleType: "Blood",
            collectionDate: "07/09/2026",
            sampleStatus: "Entered",
            testName: "Hemoglobin",
            testStatus: "Finalized",
            resultDate: "07/10/2026",
            resultName: "Hemoglobin",
            resultValue: "13.2",
            resultType: "TEXT",
            resultDisplayConfigJson: "",
            fixedFields: [],
            sampleOrderFields: [],
            sampleAdditionalFields: [],
            resultValues: [],
            additionalFieldDefinitions: [],
            additionalFieldValues: {},
          },
        ],
      });
    });

    renderComponent();

    await waitFor(() =>
      expect(screen.getByText("Patient history overview")).not.toBeNull(),
    );

    expect(screen.getByText("2")).not.toBeNull();
    expect(screen.getByText("3")).not.toBeNull();
    expect(screen.getByText("7")).not.toBeNull();
    expect(screen.getByText("LAB-1001")).not.toBeNull();
    expect(screen.getByText("Central Clinic")).not.toBeNull();
  });
});
