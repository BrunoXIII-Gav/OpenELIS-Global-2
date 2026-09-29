import React from "react";
import {
  fireEvent,
  act,
  render,
  screen,
  wait,
  within,
} from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { NotificationContext } from "../../../layout/Layout";
import { StepThreeTestResultTypeAndLoinc } from "./TestStepForm";
import { TestFormData } from "./TestFormData";
import messages from "../../../../languages/en.json";
import spanishMessages from "../../../../languages/es.json";

jest.mock("../../../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
  ConfigurationContext: require("react").createContext(null),
}));
jest.mock("../../../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock("../sortableListComponent/SortableList", () => ({
  CustomCommonSortableOrderList: () => null,
}));

const click = async (element) =>
  act(async () => {
    fireEvent.click(element);
  });
const change = async (element, event) =>
  act(async () => {
    fireEvent.change(element, event);
  });

const field = (id, displayName, blockName, blockSortOrder, fieldSortOrder) => ({
  id,
  displayName,
  fieldKey: id,
  fieldType: "TEXT",
  blockName,
  blockSortOrder,
  fieldSortOrder,
  active: true,
  required: false,
  entryScope: "OFFICIAL",
  includeInValidation: true,
  options: [],
  metadataJson: JSON.stringify({
    customSetting: "preserved",
    resultBlock: blockName,
    blockSortOrder,
    fieldSortOrder,
  }),
});
const fields = () => [
  field("a", "Alpha", "A", 1, 2),
  field("b", "Beta", "A", 1, 3),
  field("c", "Gamma", "B", 2, 1),
];
const renderForm = (
  overrides = {},
  locale = "en",
  types = [{ id: "1", value: "Text", code: "R" }],
) => {
  const onNext = jest.fn();
  const result = render(
    <IntlProvider
      locale={locale}
      messages={locale === "es" ? spanishMessages : messages}
    >
      <NotificationContext.Provider
        value={{
          setNotificationVisible: jest.fn(),
          addNotification: jest.fn(),
        }}
      >
        <StepThreeTestResultTypeAndLoinc
          formData={{
            ...TestFormData,
            testId: "test-1",
            resultActive: false,
            resultType: "1",
            resultBlockName: "A",
            additionalFields: fields(),
            ...overrides,
          }}
          resultTypeList={types.map(({ id, value }) => ({ id, value }))}
          resultTypeCodes={types.map(({ id, code }) => ({ id, value: code }))}
          handleNextStep={onNext}
          handlePreviousStep={jest.fn()}
          setSelectedResultTypeList={jest.fn()}
        />
      </NotificationContext.Provider>
    </IntlProvider>,
  );
  const card = (name) =>
    screen
      .getByText(name, { selector: ".test-additional-fields__field-title" })
      .closest(".test-additional-fields__card");
  const names = () =>
    [
      ...result.container.querySelectorAll(
        ".test-additional-fields__field-title",
      ),
    ].map((node) => node.textContent);
  const selectBlock = async (name, block) => {
    const input = within(card(name)).getByLabelText("Block");
    await change(input, { target: { value: block } });
    await act(async () => {
      fireEvent.keyDown(input, { key: "ArrowDown", code: "ArrowDown" });
    });
    await click(
      within(card(name)).getByText(block, {
        selector: ".cds--list-box__menu-item__option",
      }),
    );
  };
  return { ...result, onNext, card, names, selectBlock };
};

beforeAll(() => {
  Element.prototype.scrollIntoView = jest.fn();
});

test("existing fields keep position until OK, then use their destination block order and preserve metadata", async () => {
  const { card, names, selectBlock, onNext } = renderForm();
  await click(within(card("Alpha")).getByText("Edit"));
  await selectBlock("Alpha", "B");
  expect(names()).toEqual(["Alpha", "Beta", "Gamma"]);
  await click(within(card("Alpha")).getByText("Ok"));
  await wait(() => expect(names()).toEqual(["Beta", "Gamma", "Alpha"]));
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(onNext).toHaveBeenCalled());
  const saved = onNext.mock.calls[0][0].additionalFields.find(
    (item) => item.id === "a",
  );
  expect(saved).toMatchObject({
    blockName: "B",
    blockSortOrder: 2,
    fieldSortOrder: 2,
  });
  expect(JSON.parse(saved.metadataJson)).toMatchObject({
    customSetting: "preserved",
    resultBlock: "B",
    blockSortOrder: 2,
    fieldSortOrder: 2,
  });
});

test("a new field stays at the end while choosing a block and joins it on OK, including after reload", async () => {
  const view = renderForm();
  await click(screen.getByText(messages["test.additionalFields.addField"]));
  await change(
    view.container.querySelector("#additional-field-display-name-3"),
    { target: { value: "New field" } },
  );
  await change(view.container.querySelector("#additional-field-key-3"), {
    target: { value: "new_field" },
  });
  await view.selectBlock("New field", "A");
  expect(view.names()).toEqual(["Alpha", "Beta", "Gamma", "New field"]);
  await click(within(view.card("New field")).getByText("Ok"));
  await wait(() =>
    expect(view.names()).toEqual(["Alpha", "Beta", "New field", "Gamma"]),
  );
  expect(within(view.card("New field")).getByText("Edit")).toBeInTheDocument();
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(view.onNext).toHaveBeenCalled());
  const data = view.onNext.mock.calls[0][0];
  expect(
    data.additionalFields.find((item) => item.fieldKey === "new_field")
      .fieldSortOrder,
  ).toBe(4);
  view.unmount();
  const reloaded = renderForm(data);
  expect(reloaded.names()).toEqual(["Alpha", "Beta", "New field", "Gamma"]);
});

test("block and field arrows update displayed and submitted order, including the shared primary-result block", async () => {
  const { container, names, onNext } = renderForm({ resultActive: true });
  expect(
    container.querySelector('[id^="additional-field-block-order-"]'),
  ).toBeNull();
  await click(screen.getByRole("button", { name: "Move block B up" }));
  await wait(() => expect(names()).toEqual(["Gamma", "Alpha", "Beta"]));
  await click(screen.getByRole("button", { name: "Move field Beta up" }));
  await wait(() => expect(names()).toEqual(["Gamma", "Beta", "Alpha"]));
  expect(
    screen.getByRole("button", { name: "Move block B up" }),
  ).toBeDisabled();
  expect(
    screen.getByRole("button", { name: "Move field Beta up" }),
  ).not.toBeDisabled();
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(onNext).toHaveBeenCalled());
  const saved = onNext.mock.calls[0][0];
  expect(saved.additionalFields.map((item) => item.displayName)).toEqual([
    "Gamma",
    "Beta",
    "Alpha",
  ]);
  expect(JSON.parse(saved.resultDisplayConfigJson).blockSortOrder).toBe(2);
  expect(
    saved.additionalFields.map(
      (item) => JSON.parse(item.metadataJson).fieldSortOrder,
    ),
  ).toEqual([1, 2, 3]);
});

test("Next validates the selected destination before saving an unconfirmed move", async () => {
  const sourceFields = [
    {
      ...field("a", "Alpha", "A", 1, 1),
      fieldType: "NUMBER",
      tubeQuantitySource: true,
    },
    {
      ...field("c", "Gamma", "B", 2, 1),
      fieldType: "NUMBER",
      tubeQuantitySource: true,
    },
  ];
  const { card, selectBlock, onNext, names } = renderForm({
    additionalFields: sourceFields,
  });
  await click(within(card("Alpha")).getByText("Edit"));
  await selectBlock("Alpha", "B");
  await click(screen.getByText("Next", { selector: "button" }));
  expect(onNext).not.toHaveBeenCalled();
  expect(names()).toEqual(["Alpha", "Gamma"]);
  await selectBlock("Alpha", "A");
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(onNext).toHaveBeenCalled());
});

const layoutNames = (container) =>
  [
    ...container.querySelectorAll(
      ".test-additional-fields__field-title, .test-additional-fields__primary-title",
    ),
  ].map((node) => node.textContent);

const selectPrimaryBlock = async (container, name) => {
  const input = container.querySelector("#result-block-name");
  await change(input, { target: { value: name } });
  await act(async () => {
    fireEvent.keyDown(input, { key: "ArrowDown", code: "ArrowDown" });
  });
  await click(
    screen.getByText(name, { selector: ".cds--list-box__menu-item__option" }),
  );
};

test("a primary-only block has one compact row and preserves configuration when toggled", async () => {
  const { container } = renderForm({
    resultActive: true,
    resultName: "Main result",
    resultBlockName: "Primary block",
    additionalFields: [],
  });
  expect(container.querySelector("#result-block-sort-order")).toBeNull();
  expect(container.querySelector("#result-field-sort-order")).toBeNull();
  expect(layoutNames(container)).toEqual(["Main result"]);
  expect(screen.getByText("Primary")).toBeInTheDocument();
  expect(
    screen.getByText("Primary block", {
      selector: ".test-additional-fields__block-title",
    }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "Move block Primary block up" }),
  ).toBeDisabled();
  expect(
    screen.getByRole("button", { name: "Move field Main result down" }),
  ).toBeDisabled();
  await click(container.querySelector("#result-active"));
  expect(layoutNames(container)).toEqual([]);
  await click(container.querySelector("#result-active"));
  expect(layoutNames(container)).toEqual(["Main result"]);
  expect(container.querySelector("#result-block-name").value).toBe(
    "Primary block",
  );
});

test("primary and additional fields can move past each other and reload in the saved order", async () => {
  const view = renderForm({ resultActive: true, resultName: "Main result" });
  expect(layoutNames(view.container)).toEqual([
    "Main result",
    "Alpha",
    "Beta",
    "Gamma",
  ]);
  await click(
    screen.getByRole("button", { name: "Move field Main result down" }),
  );
  expect(layoutNames(view.container)).toEqual([
    "Alpha",
    "Main result",
    "Beta",
    "Gamma",
  ]);
  await click(screen.getByRole("button", { name: "Move field Beta up" }));
  expect(layoutNames(view.container)).toEqual([
    "Alpha",
    "Beta",
    "Main result",
    "Gamma",
  ]);
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(view.onNext).toHaveBeenCalled());
  const data = view.onNext.mock.calls[0][0];
  expect(data.additionalFields).toHaveLength(3);
  expect(JSON.parse(data.resultDisplayConfigJson)).toMatchObject({
    resultBlock: "A",
    blockSortOrder: 1,
    fieldSortOrder: 3,
  });
  expect(
    data.additionalFields.map(
      (item) => JSON.parse(item.metadataJson).fieldSortOrder,
    ),
  ).toEqual([1, 2, 1]);
  view.unmount();
  const reloaded = renderForm(data);
  expect(layoutNames(reloaded.container)).toEqual([
    "Alpha",
    "Beta",
    "Main result",
    "Gamma",
  ]);
});

test("a primary-only block can be reordered, assigned to an existing block and assigned to a new block with spaces", async () => {
  const { container, onNext } = renderForm({
    resultActive: true,
    resultName: "Main result",
    resultBlockName: "C",
    resultBlockSortOrder: 3,
  });
  await click(screen.getByRole("button", { name: "Move block C up" }));
  expect(layoutNames(container)).toEqual([
    "Alpha",
    "Beta",
    "Main result",
    "Gamma",
  ]);
  await selectPrimaryBlock(container, "A");
  expect(
    container.querySelectorAll(".test-additional-fields__block-title"),
  ).toHaveLength(2);
  expect(layoutNames(container)).toEqual([
    "Alpha",
    "Beta",
    "Main result",
    "Gamma",
  ]);
  const selector = container
    .querySelector("#result-block-name")
    .closest(".test-additional-fields__block-selector");
  await click(within(selector).getByText("Create block"));
  const input = within(selector).getByLabelText("New block name");
  await change(input, { target: { value: "New " } });
  expect(input.value).toBe("New ");
  expect(layoutNames(container)).toEqual([
    "Alpha",
    "Beta",
    "Main result",
    "Gamma",
  ]);
  await change(input, { target: { value: "New primary block " } });
  await click(within(selector).getByText("Create", { selector: "button" }));
  expect(layoutNames(container)).toEqual([
    "Alpha",
    "Beta",
    "Gamma",
    "Main result",
  ]);
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(onNext).toHaveBeenCalled());
  const saved = onNext.mock.calls[0][0];
  expect(JSON.parse(saved.resultDisplayConfigJson)).toMatchObject({
    resultBlock: "New primary block",
    blockSortOrder: 4,
    fieldSortOrder: 1,
  });
});

test("inactive primary controls stay visible and disabled, keep values, and become editable on reactivation", async () => {
  const { container, onNext } = renderForm(
    {
      resultActive: true,
      resultName: "Main result",
      loinc: "1234-5",
      resultTubeSelectorEnabled: true,
      resultTubeSelectorMin: "2",
      resultTubeSelectorMax: "5",
      resultTubeActivationCount: "3",
    },
    "en",
    [{ id: "1", value: "Numeric", code: "N" }],
  );
  const ids = [
    "select-result-type",
    "loinc",
    "resultName",
    "result-block-name",
    "result-entry-scope",
    "result-tube-activation-count",
    "result-tube-selector-enabled",
    "result-tube-quantity-source",
    "result-tube-label-enabled",
    "result-child-tube-usage-block-enabled",
    "result-tube-selector-min",
    "result-tube-selector-max",
  ];
  const originalValues = ids.map(
    (id) => container.querySelector(`#${id}`).value,
  );
  await click(container.querySelector("#result-active"));
  ids.forEach((id) => expect(container.querySelector(`#${id}`)).toBeDisabled());
  expect(container.querySelector("#select-result-type")).not.toHaveAttribute(
    "required",
  );
  expect(
    container.querySelector("#select-result-type").closest(".cds--select")
      .parentElement.textContent,
  ).not.toContain("*");
  expect(
    container.querySelector("#additional-field-display-name-0"),
  ).not.toBeDisabled();
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(onNext).toHaveBeenCalled());
  const saved = onNext.mock.calls[0][0];
  expect(saved.resultType).toBe("1");
  expect(JSON.parse(saved.resultDisplayConfigJson)).toMatchObject({
    active: false,
    tubeSelector: { enabled: true, min: 2, max: 5 },
    tubeBlock: { enabled: true, activationCount: 3 },
  });
  await click(container.querySelector("#result-active"));
  ids.forEach((id) =>
    expect(container.querySelector(`#${id}`)).not.toBeDisabled(),
  );
  expect(ids.map((id) => container.querySelector(`#${id}`).value)).toEqual(
    originalValues,
  );
  expect(container.querySelector("#select-result-type")).toHaveAttribute(
    "required",
  );
});

test("an inactive empty result type can continue with a valid API fallback; an active empty type cannot", async () => {
  const { container, onNext } = renderForm({
    resultActive: true,
    resultType: "",
  });
  await click(screen.getByText("Next", { selector: "button" }));
  expect(onNext).not.toHaveBeenCalled();
  await click(container.querySelector("#result-active"));
  await click(screen.getByText("Next", { selector: "button" }));
  await wait(() => expect(onNext).toHaveBeenCalled());
  const saved = onNext.mock.calls[0][0];
  expect(saved.resultType).toBe("1");
  expect(JSON.parse(saved.resultDisplayConfigJson).active).toBe(false);
  expect(container.querySelector("#select-result-type")).not.toHaveAttribute(
    "aria-invalid",
    "true",
  );
});

test("all result types use Spanish i18n labels while option IDs and submitted selection stay unchanged", async () => {
  const types = [
    { id: "101", code: "N", value: "Numeric" },
    { id: "102", code: "A", value: "Alphanumeric" },
    { id: "103", code: "R", value: "Free text" },
    { id: "104", code: "D", value: "Select list" },
    { id: "105", code: "M", value: "Multi-select list" },
    { id: "106", code: "C", value: "Cascading multi-select list" },
    { id: "107", code: "T", value: "Titer" },
  ];
  const { container, onNext } = renderForm(
    { resultActive: true, resultType: "103" },
    "es",
    types,
  );
  const select = container.querySelector("#select-result-type");
  expect(
    [...select.options].slice(1).map((option) => [option.value, option.text]),
  ).toEqual([
    ["101", "Numérico"],
    ["102", "Alfanumérico"],
    ["103", "Texto libre"],
    ["104", "Lista de selección"],
    ["105", "Lista de selección múltiple"],
    ["106", "Lista de selección múltiple en cascada"],
    ["107", "Título (dilución)"],
  ]);
  await change(select, { target: { value: "102" } });
  await click(
    screen.getByText(spanishMessages["next.action.button"], {
      selector: "button",
    }),
  );
  await wait(() => expect(onNext).toHaveBeenCalled());
  expect(onNext.mock.calls[0][0].resultType).toBe("102");
});
