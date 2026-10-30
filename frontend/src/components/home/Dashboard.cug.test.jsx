import React from "react";
import { act, fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import HomeDashBoard from "./Dashboard";
import { NotificationContext } from "../layout/Layout";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { getFromOpenElisServer } from "../utils/Utils";
import messages from "../../languages/en.json";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  hasRole: () => true,
}));

const panels = [
  [
    "dashboard.awaiting.sample.label",
    "/SampleManagement?accessionNumber=5415.1",
  ],
  ["dashboard.in.progress.label", "/ModifyOrder?accessionNumber=5415.1"],
  [
    "dashboard.awaiting.results.label",
    "/result?type=order&doRange=false&accessionNumber=5415.1",
  ],
  [
    "dashboard.validation.ready.label",
    "validation?type=order&accessionNumber=5415.1",
  ],
  ["dashboard.complete.orders.label", null],
  [
    "dashboard.alternate.order.flow.label",
    "/ModifyOrder?accessionNumber=5415.1",
  ],
];
let rows;
const notifications = {
  addNotification: jest.fn(),
  setNotificationVisible: jest.fn(),
  notificationVisible: false,
};
const clipboard = Object.getOwnPropertyDescriptor(navigator, "clipboard");

beforeEach(() => {
  jest.clearAllMocks();
  rows = [
    {
      id: "1",
      priority: "ROUTINE",
      orderDate: "2026-10-29 10:00:00",
      labNumber: "LNG0000000000078",
      cugCode: "5415.1",
      patientId: "patient",
      testName: "Test",
      testSection: "1",
    },
  ];
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: { writeText: jest.fn().mockResolvedValue() },
  });
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.startsWith("/rest/user-test-sections"))
      callback([{ id: "1", value: "Unit" }]);
    else if (
      url.includes("visibility-config") ||
      url.includes("waiting-time-config")
    )
      callback({});
    else if (url.includes("/metrics?")) callback({});
    else callback({ displayItems: rows });
  });
});

afterAll(() => {
  if (clipboard) Object.defineProperty(navigator, "clipboard", clipboard);
  else delete navigator.clipboard;
});

const openPanel = (title) => {
  const view = render(
    <IntlProvider locale="en" messages={messages}>
      <UserSessionDetailsContext.Provider value={{ userSessionDetails: {} }}>
        <NotificationContext.Provider value={notifications}>
          <HomeDashBoard />
        </NotificationContext.Provider>
      </UserSessionDetailsContext.Provider>
    </IntlProvider>,
  );
  fireEvent.click(view.getByText(messages[title]));
  return view;
};

test.each(panels)(
  "%s displays and copies only CUG, retaining its destination",
  async (title, href) => {
    const view = openPanel(title);
    await wait(() => expect(view.getByText("5415.1")).toBeTruthy());
    const header = view.container.querySelector("thead");
    expect(header.textContent).toContain("CUG");
    expect(header.textContent).not.toContain(messages["eorder.labNumber"]);
    expect(view.queryByText("LNG0000000000078")).toBeNull();
    const code = view.getByText("5415.1");
    if (href) expect(code.closest("a").getAttribute("href")).toBe(href);
    else expect(code.closest("a")).toBeNull();
    fireEvent.click(view.getByRole("button", { name: "Copy CUG 5415.1" }));
    await wait(() =>
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith("5415.1"),
    );
  },
);

test("each CUG in a multiple-sample order has its own copy button and link", async () => {
  rows[0].cugCode = "5415.1, 5415.2";
  const view = openPanel("dashboard.in.progress.label");
  await wait(() => expect(view.getByText("5415.2")).toBeTruthy());
  expect(view.container.querySelectorAll("tbody tr").length).toBe(1);
  fireEvent.click(view.getByRole("button", { name: "Copy CUG 5415.2" }));
  await wait(() =>
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("5415.2"),
  );
  expect(view.getByText("5415.2").closest("a").getAttribute("href")).toBe(
    "/ModifyOrder?accessionNumber=5415.2",
  );
});

test("orders without CUG do not show a laboratory number or an empty copy button", async () => {
  rows[0].cugCode = "";
  const view = openPanel("dashboard.complete.orders.label");
  await wait(() =>
    expect(view.container.querySelector("tbody tr")).toBeTruthy(),
  );
  expect(view.queryByText("LNG0000000000078")).toBeNull();
  expect(view.queryByRole("button", { name: /Copy CUG/ })).toBeNull();
});

test("clipboard rejection shows a translated error", async () => {
  navigator.clipboard.writeText.mockRejectedValue(new Error("denied"));
  const view = openPanel("dashboard.in.progress.label");
  await act(async () =>
    fireEvent.click(view.getByRole("button", { name: "Copy CUG 5415.1" })),
  );
  expect(notifications.addNotification).toHaveBeenCalledWith(
    expect.objectContaining({
      message: messages["instructions.copy.cug.error"],
    }),
  );
});

test("copies the selected CUG when the Clipboard API is unavailable", async () => {
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: undefined,
  });
  const previousCommand = document.execCommand;
  const command = jest.fn(() => {
    expect(document.activeElement.value).toBe("5415.1");
    return true;
  });
  document.execCommand = command;
  try {
    const view = openPanel("dashboard.in.progress.label");
    await act(async () =>
      fireEvent.click(view.getByRole("button", { name: "Copy CUG 5415.1" })),
    );
    expect(command).toHaveBeenCalledWith("copy");
    expect(document.querySelector("textarea")).toBeNull();
  } finally {
    document.execCommand = previousCommand;
  }
});
