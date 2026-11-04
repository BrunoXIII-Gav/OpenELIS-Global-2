import React, { useState } from "react";
import { fireEvent, render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import AddSample from "./AddSample";
import messages from "../../languages/en.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock("./SampleType", () => ({ sample }) => (
  <div data-testid="sample-card">{sample.sampleTypeId}</div>
));

function Harness() {
  const [samples, setSamples] = useState([
    { index: 0, sampleTypeId: "blood", tests: [{ id: "1" }], sampleXML: {} },
    { index: 1, sampleTypeId: "saliva", tests: [{ id: "2" }], sampleXML: {} },
  ]);
  return (
    <AddSample samples={samples} setSamples={setSamples} orderFormValues={{}} />
  );
}

test("creating an order keeps both sample cards and removes only the selected one", () => {
  getFromOpenElisServer.mockImplementation((_url, callback) => callback([]));
  const view = render(
    <IntlProvider locale="en" messages={messages}>
      <Harness />
    </IntlProvider>,
  );
  expect(
    view.getAllByTestId("sample-card").map((card) => card.textContent),
  ).toEqual(["blood", "saliva"]);
  fireEvent.click(view.getAllByText(messages["sample.remove.action"])[0]);
  expect(
    view.getAllByTestId("sample-card").map((card) => card.textContent),
  ).toEqual(["saliva"]);
  fireEvent.click(view.getByText(messages["order.sample.add.action"]));
  expect(view.getAllByTestId("sample-card")).toHaveLength(2);
});
