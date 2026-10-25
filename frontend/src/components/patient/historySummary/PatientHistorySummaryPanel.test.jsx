import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { waitFor } from "@testing-library/dom";
import { IntlProvider } from "react-intl";
import PatientHistorySummaryPanel from "./PatientHistorySummaryPanel";
import englishMessages from "../../../languages/en.json";

jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  putToOpenElisServerFullResponse: jest.fn(),
}));

const {
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} = require("../../utils/Utils");

const messages = {
  ...englishMessages,
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
    "Filter by lab number, order number, CUG, requester, test...",
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
  "patientHistory.table.cugCode": "CUG",
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
  "sample.management.order.fixed.referringSiteName": "Facility",
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
  "label.clear": "Clear filters",
  "yes.option": "Yes",
  "no.option": "No",
};

const renderComponent = () =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <PatientHistorySummaryPanel patientId="77" />
    </IntlProvider>,
  );

const click = async (element) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

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
            orderFields: [
              {
                key: "referringSiteName",
                label: "Facility",
                value: "Central Clinic",
                fieldType: "TEXT",
                source: "ORDER_FIXED",
              },
            ],
          },
        ],
        samples: [
          {
            id: "11",
            accessionNumber: "LAB-1001",
            clinicalOrderId: "ORD-22",
            sampleItemExternalId: "LAB-1001-1",
            cugCode: "123.4",
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
            cugCode: "123.4",
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

    // The overview counts the returned rows after filtering, not server totals.
    expect(
      screen.getByText("Total tests", {
        selector: ".patient-history-summary__metric-label",
      }).parentElement,
    ).toHaveTextContent("Total tests1");
    expect(
      screen.getByText("Stored samples", {
        selector: ".patient-history-summary__metric-label",
      }).parentElement,
    ).toHaveTextContent("Stored samples1");
    expect(screen.getAllByText("LAB-1001").length).toBeGreaterThan(0);
    await click(screen.getByRole("button", { name: "View" }));
    expect(screen.getByText("Central Clinic")).not.toBeNull();
  });

  it("links saved result documents to the analysis and field endpoint", async () => {
    getFromOpenElisServer.mockImplementation((endpoint, callback) => {
      callback({
        patientId: "77",
        metrics: {
          totalOrders: 0,
          totalSamples: 0,
          storedSamples: 0,
          totalTests: 1,
          completedTests: 1,
          pendingTests: 0,
        },
        orders: [],
        samples: [],
        results: [
          {
            id: "78",
            accessionNumber: "LAB-1001",
            testName: "Document test",
            fixedFields: [],
            sampleOrderFields: [],
            sampleAdditionalFields: [],
            resultValues: [],
            additionalFieldDefinitions: [
              {
                id: 10,
                fieldKey: "doccc1",
                displayName: "Attachment",
                fieldType: "DOCUMENT",
                blockName: "Official",
                entryScope: "OFFICIAL",
              },
            ],
            additionalFieldValues: {
              doccc1: JSON.stringify({ fileName: "saved-document.pdf" }),
            },
          },
        ],
      });
    });
    renderComponent();
    await click(await screen.findByRole("tab", { name: "Results" }));
    await click(await screen.findByRole("button", { name: "View" }));
    const link = await screen.findByRole("link", {
      name: "saved-document.pdf",
    });
    expect(link).toHaveAttribute(
      "href",
      "/api/OpenELIS-Global/rest/test-additional-fields/files/78/doccc1?download=false",
    );
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  const validationSummary = (overrides = {}) => ({
    patientId: "77",
    metrics: {},
    orders: [],
    samples: [],
    results: [],
    validations: [
      {
        analysisId: "78",
        accessionNumber: "LAB-1",
        clinicalOrderId: "ORD-1",
        cugCode: "13.1",
        testName: "Extraction",
        validationDate: "2026-10-15",
        validators: [{ name: "Carla Enciso" }, { name: "Mario Cornejo" }],
        reportAvailable: true,
      },
      {
        analysisId: "79",
        accessionNumber: "LAB-2",
        clinicalOrderId: "ORD-2",
        cugCode: "13.2",
        testName: "DMPK",
        validationDate: "2026-10-14",
        validators: [],
        reportAvailable: true,
      },
    ],
    ...overrides,
  });

  const loadValidationSummary = async (overrides = {}) => {
    getFromOpenElisServer.mockImplementation((endpoint, callback) =>
      callback(validationSummary(overrides)),
    );
    renderComponent();
    await click(await screen.findByRole("tab", { name: "Validation" }));
  };

  it("shows validators and a separate PDF link for each validated test", async () => {
    await loadValidationSummary();
    expect(screen.getByText("Carla Enciso, Mario Cornejo")).toBeInTheDocument();
    expect(screen.getByText("Not recorded")).toBeInTheDocument();
    const links = screen.getAllByRole("link", { name: "Open PDF" });
    expect(links[0]).toHaveAttribute(
      "href",
      "/api/OpenELIS-Global/rest/patient-history/77/validations/78/report",
    );
    expect(links[1]).toHaveAttribute(
      "href",
      "/api/OpenELIS-Global/rest/patient-history/77/validations/79/report",
    );
    fireEvent.change(screen.getByLabelText("Lab or order number"), {
      target: { value: "Extraction" },
    });
    expect(screen.queryByText("DMPK")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Open PDF" })).toHaveLength(1);
  });

  it.each(["patient-validations", "patient-history"])(
    "hides validation when the %s group is restricted",
    async (group) => {
      getFromOpenElisServer.mockImplementation((endpoint, callback) =>
        callback(validationSummary({ restrictedFieldGroupKeys: [group] })),
      );
      renderComponent();
      await screen.findByText("Patient history overview");
      expect(
        screen.queryByRole("tab", { name: "Validation" }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("Carla Enciso, Mario Cornejo"),
      ).not.toBeInTheDocument();
    },
  );

  it("hides restricted validation fields and PDF links", async () => {
    await loadValidationSummary({
      restrictedFieldTagKeys: [
        "patient-validation-validator",
        "patient-validation-date",
      ],
    });
    expect(
      screen.queryByRole("columnheader", { name: "Validated by" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("columnheader", { name: "Validation date" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Open PDF" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Extraction")).toBeInTheDocument();
  });

  it("keeps validator information when only the PDF is restricted", async () => {
    await loadValidationSummary({
      restrictedFieldTagKeys: ["patient-validation-report"],
    });
    expect(screen.getByText("Carla Enciso, Mario Cornejo")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Open PDF" }),
    ).not.toBeInTheDocument();
  });

  const deliverySummary = () =>
    validationSummary({
      validations: validationSummary().validations.map((row, index) =>
        index === 0
          ? { ...row, deliveredDate: "2026-10-16", canRegisterDelivery: true }
          : { ...row, canRegisterDelivery: false },
      ),
    });

  it("shows the delivery date and offers delivery only where permitted", async () => {
    getFromOpenElisServer.mockImplementation((endpoint, callback) =>
      callback(deliverySummary()),
    );
    renderComponent();
    await click(await screen.findByRole("tab", { name: "Validation" }));
    expect(
      screen.getByRole("columnheader", { name: "Delivery date" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Not delivered")).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Correct delivery" }),
    ).toHaveLength(1);
    expect(
      screen.queryByRole("button", { name: "Register delivery" }),
    ).not.toBeInTheDocument();
  });

  it("requires a reason before correcting a recorded delivery", async () => {
    getFromOpenElisServer.mockImplementation((endpoint, callback) =>
      callback(deliverySummary()),
    );
    renderComponent();
    await click(await screen.findByRole("tab", { name: "Validation" }));
    await click(screen.getByRole("button", { name: "Correct delivery" }));
    fireEvent.change(screen.getByLabelText("Delivery date"), {
      target: { value: "2026-10-17" },
    });
    await click(screen.getByRole("button", { name: "Save" }));
    expect(
      screen.getByText(
        "A reason is required to change a recorded delivery date.",
      ),
    ).toBeInTheDocument();
    expect(putToOpenElisServerFullResponse).not.toHaveBeenCalled();

    putToOpenElisServerFullResponse.mockImplementation(
      (endpoint, body, callback) => callback({ ok: true }),
    );
    fireEvent.change(screen.getByLabelText("Correction reason"), {
      target: { value: "Typo" },
    });
    await click(screen.getByRole("button", { name: "Save" }));
    expect(putToOpenElisServerFullResponse).toHaveBeenCalledWith(
      "/rest/patient-history/77/validations/78/delivery",
      JSON.stringify({ deliveredDate: "2026-10-17", reason: "Typo" }),
      expect.any(Function),
    );
    await waitFor(() => expect(getFromOpenElisServer).toHaveBeenCalledTimes(2));
  });

  it("hides the delivery date when it is restricted", async () => {
    await loadValidationSummary({
      restrictedFieldTagKeys: ["patient-validation-delivery-date"],
    });
    expect(
      screen.queryByRole("columnheader", { name: "Delivery date" }),
    ).not.toBeInTheDocument();
  });
});
