export const PATIENT_FIXED_FIELD_DEFINITIONS = [
  {
    fieldKey: "photo",
    sortOrder: 10,
    required: false,
    readonly: false,
    supportsRequired: false,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.photo",
    defaultLabel: "Photo",
  },
  {
    fieldKey: "subjectNumber",
    sortOrder: 20,
    required: false,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.subjectNumber",
    defaultLabel: "Historia clínica",
  },
  {
    fieldKey: "nationalId",
    sortOrder: 30,
    required: false,
    readonly: true,
    supportsRequired: false,
    supportsReadonly: false,
    labelId: "patient.fixed.fields.nationalId",
    defaultLabel: "ID Paciente",
  },
  {
    fieldKey: "optionalIdentifiers",
    sortOrder: 40,
    required: false,
    readonly: false,
    supportsRequired: false,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.optionalIdentifiers",
    defaultLabel: "Identificadores opcionales",
  },
  {
    fieldKey: "lastName",
    sortOrder: 50,
    required: false,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.lastName",
    defaultLabel: "Apellido",
  },
  {
    fieldKey: "firstName",
    sortOrder: 60,
    required: false,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.firstName",
    defaultLabel: "Nombre",
  },
  {
    fieldKey: "primaryPhone",
    sortOrder: 70,
    required: false,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.primaryPhone",
    defaultLabel: "Teléfono principal",
  },
  {
    fieldKey: "email",
    sortOrder: 80,
    required: false,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.email",
    defaultLabel: "Correo electrónico del paciente",
  },
  {
    fieldKey: "gender",
    sortOrder: 90,
    required: true,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.gender",
    defaultLabel: "Género",
  },
  {
    fieldKey: "birthDateAge",
    sortOrder: 100,
    required: true,
    readonly: false,
    supportsRequired: true,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.birthDateAge",
    defaultLabel: "Fecha de nacimiento y edad",
  },
  {
    fieldKey: "emergencyContact",
    sortOrder: 110,
    required: false,
    readonly: false,
    supportsRequired: false,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.emergencyContact",
    defaultLabel: "Información de Contacto de Emergencia",
  },
  {
    fieldKey: "additionalInfo",
    sortOrder: 120,
    required: false,
    readonly: false,
    supportsRequired: false,
    supportsReadonly: true,
    labelId: "patient.fixed.fields.additionalInfo",
    defaultLabel: "Información Adicional",
  },
];

export const PATIENT_FIXED_FIELD_DEFINITION_MAP =
  PATIENT_FIXED_FIELD_DEFINITIONS.reduce((accumulator, definition) => {
    accumulator[definition.fieldKey] = definition;
    return accumulator;
  }, {});

export const normalizePatientFixedFieldConfigs = (configs = []) => {
  const incomingByKey = new Map(
    (Array.isArray(configs) ? configs : [])
      .filter((config) => config?.fieldKey)
      .map((config) => [config.fieldKey, config]),
  );

  return PATIENT_FIXED_FIELD_DEFINITIONS.map((definition) => {
    const incoming = incomingByKey.get(definition.fieldKey) || {};
    return {
      fieldKey: definition.fieldKey,
      visible: incoming.visible !== false,
      required:
        incoming.required !== null && incoming.required !== undefined
          ? !!incoming.required
          : !!definition.required,
      readonly:
        incoming.readonly !== null && incoming.readonly !== undefined
          ? !!incoming.readonly
          : !!definition.readonly,
      sortOrder:
        incoming.sortOrder !== null && incoming.sortOrder !== undefined
          ? Number(incoming.sortOrder)
          : definition.sortOrder,
    };
  });
};

export const getPatientFixedFieldLabel = (intl, fieldKey) => {
  const definition = PATIENT_FIXED_FIELD_DEFINITION_MAP[fieldKey];
  if (!definition) {
    return fieldKey;
  }

  return intl.formatMessage({
    id: definition.labelId,
    defaultMessage: definition.defaultLabel,
  });
};
