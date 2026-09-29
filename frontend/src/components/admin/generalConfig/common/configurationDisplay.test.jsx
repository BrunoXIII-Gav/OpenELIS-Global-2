/* eslint-env jest */
import React from "react";
import { render, screen, fireEvent, act, wait } from "@testing-library/react";
import { IntlProvider, createIntl } from "react-intl";
import { MemoryRouter } from "react-router-dom";
import { NotificationContext } from "../../../layout/Layout";
import {
  getFromOpenElisServer,
  postToOpenElisServer,
} from "../../../utils/Utils";
import ConfigMenuDisplay from "./ConfigMenuDisplay";
import GenericConfigEdit from "./GenericConfigEdit";
import {
  getSiteInformationDisplayValue,
  getSiteInformationDisplayName,
} from "./siteInformationDisplay";
import catalog from "./configurationDisplayMessages.json";
import es from "../../../../languages/es.json";
import en from "../../../../languages/en.json";
import fr from "../../../../languages/fr.json";

jest.mock("../../../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
}));
jest.mock("../../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServer: jest.fn(),
}));
jest.mock("../../../common/PageBreadCrumb", () => () => null);
jest.mock("../../../common/CustomNotification", () => ({
  AlertDialog: () => null,
  NotificationKinds: { error: "error" },
}));

const wrap = (child, locale = "es", messages = es) => (
  <MemoryRouter>
    <IntlProvider locale={locale} messages={messages}>
      <NotificationContext.Provider
        value={{
          notificationVisible: false,
          setNotificationVisible: jest.fn(),
          addNotification: jest.fn(),
        }}
      >
        {child}
      </NotificationContext.Provider>
    </IntlProvider>
  </MemoryRouter>
);
const intl = createIntl({ locale: "es", messages: es });
beforeEach(() => jest.clearAllMocks());

test("translated result row selects the original ID and saves raw boolean data", async () => {
  const original = Object.freeze({
    id: "17",
    paramName: "simplifiedTestNames",
    description:
      "Use a single name for tests with matching English and French names",
    valueType: "boolean",
    value: "false",
  });
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.includes("startingRecNo=1"))
      callback({ menuList: [{ ...original, name: original.paramName }] });
    else if (url.includes("startingRecNo=")) callback({ menuList: [] });
    else callback(original);
  });
  const view = render(
    wrap(
      <ConfigMenuDisplay
        menuType="ResultConfigurationMenu"
        id="sidenav.label.admin.formEntry.resultConfig"
      />,
    ),
  );
  await act(async () => {
    await Promise.resolve();
  });
  await wait(() =>
    expect(screen.getByText("Nombres de pruebas simplificados")).toBeTruthy(),
  );
  expect(screen.getByText("No")).toBeTruthy();
  await act(async () => fireEvent.click(screen.getByRole("radio")));
  await act(async () =>
    fireEvent.click(view.container.querySelector('[data-cy="modify-Button"]')),
  );
  expect(
    getFromOpenElisServer.mock.calls.some(
      ([url]) => url === "/rest/ResultConfiguration?ID=17",
    ),
  ).toBe(true);
  await act(async () =>
    fireEvent.click(screen.getByLabelText(es["true.label"])),
  );
  await act(async () =>
    fireEvent.click(view.container.querySelector('[data-cy="save-Button"]')),
  );
  expect(postToOpenElisServer).toHaveBeenCalledWith(
    "/rest/ResultConfiguration?ID=17",
    JSON.stringify({ ...original, value: "true" }),
    expect.any(Function),
  );
});

test.each([
  [
    "PatientConfiguration",
    "orderProviderSelectionPolicy",
    "SELF_ONLY",
    "Solo el profesional conectado",
  ],
  [
    "SampleEntryConfig",
    "enabledOrderPriorities",
    "ROUTINE,ASAP,STAT",
    "Rutina, Lo antes posible, Inmediata",
  ],
  [
    "ValidationConfiguration",
    "firstNameCharset",
    "'.a-zàâçéèêëîïôöùûüÿñæœ -",
    null,
  ],
  [
    "SampleEntryConfig",
    "profProfileFieldDefs",
    '{"USER":{"label":"English custom text"}}',
    null,
  ],
])(
  "%s translates labels without rewriting %s on save",
  async (menuType, paramName, value, preview) => {
    const original = Object.freeze({
      paramName,
      description: "Original server description",
      value,
      valueType: "text",
      tag: "",
    });
    getFromOpenElisServer.mockImplementation((url, callback) =>
      callback(original),
    );
    const view = render(
      wrap(<GenericConfigEdit menuType={menuType} ID="42" />),
    );
    expect(screen.getByText(es[catalog[paramName].nameId])).toBeTruthy();
    expect(screen.getByText(es[catalog[paramName].descriptionId])).toBeTruthy();
    expect(screen.getByRole("textbox").value).toBe(value);
    if (preview) expect(screen.getByText(preview)).toBeTruthy();
    await act(async () =>
      fireEvent.click(view.container.querySelector('[data-cy="save-Button"]')),
    );
    expect(postToOpenElisServer).toHaveBeenCalledWith(
      `/rest/${menuType}?ID=42`,
      JSON.stringify(original),
      expect.any(Function),
    );
  },
);

test("switching language changes only displayed names", () => {
  const original = {
    paramName: "alertWhenInvalidResult",
    description: "Original description",
    valueType: "boolean",
    value: "true",
  };
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback(original),
  );
  const editor = <GenericConfigEdit menuType="ResultConfiguration" ID="1" />;
  const view = render(wrap(editor));
  expect(screen.getByText(es[catalog[original.paramName].nameId])).toBeTruthy();
  view.rerender(wrap(editor, "en", en));
  expect(screen.getByText(en[catalog[original.paramName].nameId])).toBeTruthy();
  expect(postToOpenElisServer).not.toHaveBeenCalled();
});

test("unknown or custom configuration values remain readable without invented translations", () => {
  const pattern = "true,false,a-z";
  expect(
    getSiteInformationDisplayValue(intl, "firstNameCharset", pattern, "text"),
  ).toBe(pattern);
  expect(
    getSiteInformationDisplayValue(
      intl,
      "customCriticalMessage",
      "Call Dr. Smith",
      "text",
    ),
  ).toBe("Call Dr. Smith");
  expect(
    getSiteInformationDisplayValue(intl, "unregistered", "true", "text"),
  ).toBe("true");
  expect(getSiteInformationDisplayName(intl, "custom property")).toBe(
    "custom property",
  );
});

test("every registered configuration has English, Spanish and French messages", () => {
  for (const entry of Object.values(catalog)) {
    for (const messages of [en, es, fr]) {
      expect(messages[entry.nameId]).toBeTruthy();
      expect(messages[entry.descriptionId]).toBeTruthy();
    }
  }
});
