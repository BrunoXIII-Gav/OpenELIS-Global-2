import {
  assignAdditionalFieldBlock,
  getAdditionalBlocks,
  moveAdditionalBlock,
  moveAdditionalField,
} from "./additionalFieldLayoutUtils";

const field = (
  id,
  blockName,
  blockSortOrder,
  fieldSortOrder,
  active = true,
) => ({
  id,
  blockName,
  blockSortOrder,
  fieldSortOrder,
  active,
  displayName: id,
});

test("assignment reuses the canonical block and appends after its fields, including the primary result", () => {
  const fields = [
    field("a", "Tubo 1", 4, 2),
    field("b", "Tubo 2", 9, 1),
    field("disabled", "Tubo 1", 4, 99, false),
  ];
  const moved = assignAdditionalFieldBlock(
    fields,
    1,
    " tubo 1 ",
    field("result", "Tubo 1", 4, 7),
  );
  expect(moved[1]).toMatchObject({
    blockName: "Tubo 1",
    blockSortOrder: 4,
    fieldSortOrder: 8,
  });
  expect(fields[1].blockName).toBe("Tubo 2");
  expect(moved[0]).toBe(fields[0]);
});

test("a new block keeps internal spaces and is placed after existing blocks", () => {
  const fields = [field("a", "Tubo 1", 2, 1), field("b", "Tubo 2", 5, 1)];
  expect(
    assignAdditionalFieldBlock(fields, 1, "  Nuevo bloque 3  ")[1],
  ).toMatchObject({
    blockName: "Nuevo bloque 3",
    blockSortOrder: 6,
    fieldSortOrder: 1,
  });
  expect(assignAdditionalFieldBlock(fields, 1, " ")).toBe(fields);
  expect(assignAdditionalFieldBlock(fields, 1, "Tubo 2")).toBe(fields);
});

test("moving a block moves all its fields, synchronizes inactive siblings and resolves tied orders", () => {
  const fields = [
    field("a", "A", 1, 1),
    field("b", "B", 1, 1),
    field("c", "B", 1, 2),
    field("d", "B", 1, 3, false),
  ];
  const moved = moveAdditionalBlock(fields, "B", -1);
  expect(getAdditionalBlocks(moved).map(({ name }) => name)).toEqual([
    "B",
    "A",
  ]);
  expect(moved.map((item) => item.blockSortOrder)).toEqual([2, 1, 1, 1]);
  expect(moved.map((item) => item.id)).toEqual(fields.map((item) => item.id));
  expect(moveAdditionalBlock(moved, "B", -1)).toBe(moved);
  expect(moveAdditionalBlock(moved, "A", 1)).toBe(moved);
});

test("field arrows reorder active siblings including the primary result and retain identities", () => {
  const fields = [
    field("a", "A", 1, 1),
    field("b", "A", 1, 1),
    field("c", "B", 2, 1),
    field("d", "A", 1, 9, false),
    field("result", "A", 1, 3),
  ];
  const moved = moveAdditionalField(fields, 1, -1);
  expect(moved.map((item) => item.fieldSortOrder)).toEqual([2, 1, 1, 9, 3]);
  expect(moved[2]).toBe(fields[2]);
  expect(moved[3]).toBe(fields[3]);
  expect(moveAdditionalField(moved, 1, -1)).toBe(moved);
  const primaryMoved = moveAdditionalField(moved, 4, -1);
  expect(primaryMoved.map((item) => item.fieldSortOrder)).toEqual([
    3, 1, 1, 9, 2,
  ]);
  expect(moveAdditionalField(primaryMoved, 0, 1)).toBe(primaryMoved);
});
