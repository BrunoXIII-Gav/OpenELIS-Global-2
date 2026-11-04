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
  convertAlphaNumLabNumForDisplay: (value) => value,
  hasRole: () => true,
}));

const panels = [
  [
    "dashboard.awaiting.sample.label",
    "/SampleManagement?accessionNumber=LNG0000000000078",
  ],
  [
    "dashboard.in.progress.label",
    "/ModifyOrder?accessionNumber=LNG0000000000078",
  ],
  [
    "dashboard.awaiting.results.label",
    "/result?type=order&doRange=false&accessionNumber=LNG0000000000078",
  ],
  [
    "dashboard.validation.ready.label",
    "validation?type=order&accessionNumber=LNG0000000000078",
  ],
  ["dashboard.complete.orders.label", null],
  [
    "dashboard.alternate.order.flow.label",
    "/ModifyOrder?accessionNumber=LNG0000000000078",
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
  "%s displays the laboratory number and retains its destination",
  async (title, href) => {
    const view = openPanel(title);
    await wait(() => expect(view.getByText("LNG0000000000078")).toBeTruthy());
    expect(view.container.querySelector("thead").textContent).toContain(
      messages["eorder.labNumber"],
    );
    const number = view.getByText("LNG0000000000078");
    if (href) expect(number.closest("a").getAttribute("href")).toBe(href);
    else expect(number.closest("a")).toBeNull();
    fireEvent.click(
      view.getByRole("button", { name: messages["instructions.copy.labnum"] }),
    );
    await wait(() =>
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
        "LNG0000000000078",
      ),
    );
  },
);
