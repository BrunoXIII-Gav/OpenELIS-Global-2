export const evaluateSingleCondition = (condition, context) => {
  if (!condition || !condition.fieldKey) {
    return false;
  }

  const operator = (condition.operator || "equals").toLowerCase();
  const leftValue = String(context?.[condition.fieldKey] ?? "").trim();
  const rightValue = String(condition.value ?? "").trim();
  const rightValues = Array.isArray(condition.values)
    ? condition.values.map((value) => String(value ?? "").trim())
    : rightValue
      ? [rightValue]
      : [];

  switch (operator) {
    case "equals":
      return leftValue.toLowerCase() === rightValue.toLowerCase();
    case "notequals":
      return leftValue.toLowerCase() !== rightValue.toLowerCase();
    case "in":
      return rightValues.some(
        (value) => leftValue.toLowerCase() === value.toLowerCase(),
      );
    case "notin":
      return rightValues.every(
        (value) => leftValue.toLowerCase() !== value.toLowerCase(),
      );
    case "hasvalue":
      return !!leftValue;
    case "istrue":
      return ["true", "yes", "1"].includes(leftValue.toLowerCase());
    case "isfalse":
      return ["false", "no", "0"].includes(leftValue.toLowerCase());
    default:
      return false;
  }
};

export const evaluateConditions = (conditions, logic, context) => {
  if (!Array.isArray(conditions) || conditions.length === 0) {
    return true;
  }
  const useAny = String(logic || "ALL").toUpperCase() === "ANY";
  if (useAny) {
    return conditions.some((condition) =>
      evaluateSingleCondition(condition, context),
    );
  }
  return conditions.every((condition) =>
    evaluateSingleCondition(condition, context),
  );
};
