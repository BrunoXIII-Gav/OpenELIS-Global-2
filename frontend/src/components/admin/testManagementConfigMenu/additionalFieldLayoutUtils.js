// Keep array indexes stable: Formik and the field editor use them as identities.
const active = (field) => field?.active !== false;
const order = (value) => Number(value) || 1;
export const blockKey = (name) =>
  String(name || "")
    .trim()
    .toLocaleLowerCase();

export const getAdditionalBlocks = (fields, resultField) => {
  const blocks = new Map();
  [...fields, ...(resultField ? [resultField] : [])]
    .filter(active)
    .forEach((field) => {
      const name = String(field.blockName || "").trim();
      if (name && !blocks.has(blockKey(name))) {
        blocks.set(blockKey(name), {
          name,
          order: order(field.blockSortOrder),
        });
      }
    });
  return [...blocks.values()].sort(
    (a, b) => a.order - b.order || a.name.localeCompare(b.name),
  );
};

export const assignAdditionalFieldBlock = (
  fields,
  fieldIndex,
  name,
  resultField,
) => {
  const requestedName = String(name || "").trim();
  if (!requestedName) return fields;
  const blocks = getAdditionalBlocks(fields, resultField);
  const existing = blocks.find(
    (block) => blockKey(block.name) === blockKey(requestedName),
  );
  const blockName = existing?.name || requestedName;
  if (fields[fieldIndex]?.blockName === blockName) return fields;
  const siblings = [
    ...fields.filter((_, index) => index !== fieldIndex),
    ...(resultField ? [resultField] : []),
  ].filter(
    (field) =>
      active(field) && blockKey(field.blockName) === blockKey(blockName),
  );
  const blockSortOrder =
    existing?.order || Math.max(0, ...blocks.map((block) => block.order)) + 1;
  const fieldSortOrder =
    Math.max(0, ...siblings.map((field) => order(field.fieldSortOrder))) + 1;
  return fields.map((field, index) =>
    index === fieldIndex
      ? { ...field, blockName, blockSortOrder, fieldSortOrder }
      : field,
  );
};

export const moveAdditionalBlock = (fields, name, direction) => {
  const blocks = getAdditionalBlocks(fields);
  const index = blocks.findIndex(
    (block) => blockKey(block.name) === blockKey(name),
  );
  const next = index + direction;
  if (index < 0 || next < 0 || next >= blocks.length) return fields;
  [blocks[index], blocks[next]] = [blocks[next], blocks[index]];
  const ranks = new Map(
    blocks.map((block, index) => [blockKey(block.name), index + 1]),
  );
  return fields.map((field) =>
    ranks.has(blockKey(field.blockName))
      ? { ...field, blockSortOrder: ranks.get(blockKey(field.blockName)) }
      : field,
  );
};

export const getAdditionalFieldSiblings = (fields, fieldIndex) =>
  fields
    .map((field, index) => ({ field, index }))
    .filter(
      ({ field }) =>
        active(field) &&
        blockKey(field.blockName) === blockKey(fields[fieldIndex]?.blockName),
    )
    .sort(
      (a, b) =>
        order(a.field.fieldSortOrder) - order(b.field.fieldSortOrder) ||
        a.index - b.index,
    );

export const moveAdditionalField = (fields, fieldIndex, direction) => {
  const siblings = getAdditionalFieldSiblings(fields, fieldIndex);
  const index = siblings.findIndex((entry) => entry.index === fieldIndex);
  const next = index + direction;
  if (index < 0 || next < 0 || next >= siblings.length) return fields;
  [siblings[index], siblings[next]] = [siblings[next], siblings[index]];
  const ranks = new Map(
    siblings.map((entry, index) => [entry.index, index + 1]),
  );
  return fields.map((field, index) =>
    ranks.has(index) ? { ...field, fieldSortOrder: ranks.get(index) } : field,
  );
};
