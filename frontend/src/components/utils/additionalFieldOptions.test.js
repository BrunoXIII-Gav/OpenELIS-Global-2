import { getAdditionalFieldOptions } from "./additionalFieldOptions";

test.each(["SELECT", "RADIO", "MULTISELECT"])(
  "%s keeps only selected historical choices",
  (fieldType) => {
    const options = [
      { optionKey: "a", active: true },
      { optionKey: "b", active: false },
      { optionKey: "c", active: false },
    ];
    expect(getAdditionalFieldOptions({ fieldType, options })).toEqual([
      options[0],
    ]);
    expect(getAdditionalFieldOptions({ fieldType, options }, " a, b ")).toEqual(
      options.slice(0, 2),
    );
  },
);

test("user candidates stay subject to their existing eligibility rules", () => {
  expect(
    getAdditionalFieldOptions(
      { fieldType: "USER", options: [{ optionKey: "17", active: false }] },
      "17",
    ),
  ).toEqual([]);
});
