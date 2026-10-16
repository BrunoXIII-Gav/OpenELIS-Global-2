import { openDocumentFieldValue } from "./documentPreview";

describe("additional result document preview", () => {
  let originalCreate;
  let originalRevoke;

  beforeEach(() => {
    jest.useFakeTimers();
    originalCreate = URL.createObjectURL;
    originalRevoke = URL.revokeObjectURL;
    URL.createObjectURL = jest.fn(() => "blob:https://openelis.test/preview");
    URL.revokeObjectURL = jest.fn();
    jest.spyOn(window, "open").mockImplementation(() => null);
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
    jest.restoreAllMocks();
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  });

  it("opens a Blob URL with the exact stored bytes and releases it later", async () => {
    const bytes = [37, 80, 68, 70, 45, 49, 46, 52, 10, 0, 128, 255];
    openDocumentFieldValue({
      fileType: "application/pdf",
      base64Content: window.btoa(String.fromCharCode(...bytes)),
    });
    const blob = URL.createObjectURL.mock.calls[0][0];
    expect(blob.type).toBe("application/pdf");
    const readback = await new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(Array.from(new Uint8Array(reader.result)));
      reader.readAsArrayBuffer(blob);
    });
    expect(readback).toEqual(bytes);
    expect(window.open).toHaveBeenCalledWith(
      "blob:https://openelis.test/preview",
      "_blank",
      "noopener,noreferrer",
    );
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    jest.advanceTimersByTime(60_000);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(
      "blob:https://openelis.test/preview",
    );
  });

  it("does not open a tab when no document is present", () => {
    openDocumentFieldValue(null);
    openDocumentFieldValue({ base64Content: " " });
    expect(window.open).not.toHaveBeenCalled();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
});
