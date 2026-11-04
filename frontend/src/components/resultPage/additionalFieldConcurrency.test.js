import {
  captureAdditionalFieldBaseline,
  buildAdditionalFieldPatch,
  resolveAdditionalFieldConflict,
} from "./additionalFieldConcurrency";

test("only edited fields are sent and original versions stay unchanged", () => {
  const row = captureAdditionalFieldBaseline({
    additionalFieldValues: { a: "old", b: "other" },
    additionalFieldVersions: { a: "v1", b: "v2" },
  });
  row.additionalFieldValues = { a: "edited", b: "other" };
  const payload = buildAdditionalFieldPatch(row);
  expect(payload.additionalFieldValues).toEqual({ a: "edited" });
  expect(payload.additionalFieldVersions).toEqual({ a: "v1", b: "v2" });
  expect(payload.additionalFieldInitialValues).toBeUndefined();
});
test("clearing and removing an existing field remain explicit changes", () => {
  const row = captureAdditionalFieldBaseline({
    additionalFieldValues: { a: "old", b: "other" },
  });
  row.additionalFieldValues = { a: "" };
  expect(buildAdditionalFieldPatch(row).additionalFieldValues).toEqual({
    a: "",
    b: "",
  });
});
test("a conflict preserves all drafts and only rebases the explicitly chosen field", () => {
  const row = captureAdditionalFieldBaseline({
    additionalFieldValues: { a: "old", b: "other" },
    additionalFieldVersions: { a: "v1", b: "v2" },
  });
  row.additionalFieldValues = { a: "my draft", b: "another draft" };
  const conflict = {
    fieldKey: "a",
    currentValue: "someone else",
    currentVersion: "v3",
  };
  const keep = resolveAdditionalFieldConflict(row, conflict, true);
  expect(keep.additionalFieldValues).toEqual(row.additionalFieldValues);
  expect(keep.additionalFieldVersions).toEqual({ a: "v3", b: "v2" });
  expect(buildAdditionalFieldPatch(keep).additionalFieldValues).toEqual({
    a: "my draft",
    b: "another draft",
  });
  const useCurrent = resolveAdditionalFieldConflict(row, conflict, false);
  expect(buildAdditionalFieldPatch(useCurrent).additionalFieldValues).toEqual({
    b: "another draft",
  });
});
