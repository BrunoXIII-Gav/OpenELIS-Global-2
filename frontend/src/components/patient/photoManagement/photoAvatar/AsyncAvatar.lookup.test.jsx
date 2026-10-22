import React from "react";
import { render } from "@testing-library/react";
import AsyncAvatar from "./AyncAvatar";
import { getFromOpenElisServer } from "../../../utils/Utils";

jest.mock("../../../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback({ data: "" }),
  );
});

test.each([false, true])(
  "avatar requests preserve the lookup context (order=%p)",
  (forOrderEntry) => {
    render(
      <AsyncAvatar
        patientId="11"
        patientName="Test"
        hasPhoto
        forOrderEntry={forOrderEntry}
      />,
    );
    expect(getFromOpenElisServer).toHaveBeenCalledWith(
      `/rest/patient-photos/11/true${forOrderEntry ? "?forOrderEntry=true" : ""}`,
      expect.any(Function),
    );
  },
);
