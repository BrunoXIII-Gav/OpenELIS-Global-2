import React, { useEffect, useRef, useState } from "react";
import { Button, TextInput } from "@carbon/react";
import { useFormikContext } from "formik";
import { useIntl } from "react-intl";
import { getFromOpenElisServer } from "../utils/Utils";
import CreatePatientValidationSchema from "../formModel/validationSchema/CreatePatientValidationShema";

export default function PatientCugPrefixField({
  validate,
  ready,
  onReadyChange,
  forOrderEntry = false,
  allowAssignedEdit = false,
}) {
  const { values, setFieldValue } = useFormikContext();
  const intl = useIntl();
  const [preview, setPreview] = useState({ prefix: "", assigned: false });
  const [fieldsReady, setFieldsReady] = useState(false);
  const manual = values.requestedCugPrefix != null;
  const manualValue = String(values.requestedCugPrefix ?? "");
  const editingAssigned =
    Boolean(values.patientPK) && allowAssignedEdit && !forOrderEntry;
  const canUseManual = !values.patientPK || editingAssigned;
  const assignedPrefix =
    values.cugPrefix || values.expectedCugPrefix || preview.prefix;
  const validManual =
    /^[1-9][0-9]*$/.test(manualValue) &&
    (manualValue.length < 19 ||
      (manualValue.length === 19 && manualValue <= "9223372036854775806"));
  const validator = useRef(validate);
  validator.current = validate;
  const valuesKey = JSON.stringify(values);

  useEffect(() => {
    let active = true;
    setPreview({ prefix: "", assigned: false });
    onReadyChange(false);
    const load = async () => {
      const valid =
        ready &&
        Object.keys(validator.current(values)).length === 0 &&
        (await CreatePatientValidationSchema(intl).isValid(values));
      if (!active) return;
      setFieldsReady(valid);
      onReadyChange(valid && (!canUseManual || !manual || validManual));
      if (values.patientPK && (values.cugPrefix || values.expectedCugPrefix))
        return;
      if (!values.patientPK && !valid) return;
      if (!values.patientPK && manual) return;
      const parameters = new URLSearchParams();
      if (values.patientPK) parameters.set("patientId", values.patientPK);
      if (forOrderEntry) parameters.set("forOrderEntry", "true");
      const query = parameters.toString() ? `?${parameters}` : "";
      getFromOpenElisServer(
        `/rest/sample-cug/patient-prefix${query}`,
        (response) => {
          if (active)
            setPreview({
              prefix: response?.prefix || "",
              assigned: response?.assigned === true,
            });
        },
      );
    };
    load();
    return () => {
      active = false;
    };
  }, [valuesKey, ready, intl, onReadyChange, forOrderEntry, allowAssignedEdit]);

  return (
    <>
      <TextInput
        id="patient-cug-prefix"
        labelText={intl.formatMessage({ id: "patient.cug.prefix.label" })}
        value={
          values.patientPK
            ? editingAssigned && manual
              ? manualValue
              : assignedPrefix
            : fieldsReady
              ? manual
                ? manualValue
                : preview.prefix
              : ""
        }
        inputMode="numeric"
        maxLength={19}
        readOnly={
          !fieldsReady ||
          (Boolean(values.patientPK) && (!editingAssigned || !assignedPrefix))
        }
        invalid={canUseManual && fieldsReady && manual && !validManual}
        invalidText={intl.formatMessage({ id: "patient.cug.prefix.invalid" })}
        onChange={(event) => {
          if (canUseManual && fieldsReady) {
            if (editingAssigned && values.expectedCugPrefix == null) {
              setFieldValue("expectedCugPrefix", assignedPrefix, false);
            }
            setFieldValue("requestedCugPrefix", event.target.value);
          }
        }}
        helperText={intl.formatMessage({
          id:
            editingAssigned && manual
              ? "patient.cug.prefix.change.help"
              : values.patientPK && (assignedPrefix || preview.assigned)
                ? "patient.cug.prefix.saved"
                : manual
                  ? "patient.cug.prefix.manual"
                  : "patient.cug.prefix.provisional",
        })}
      />
      {canUseManual && fieldsReady && manual && (
        <Button
          kind="ghost"
          size="sm"
          type="button"
          onClick={() => {
            setFieldValue("requestedCugPrefix", null);
            setFieldValue("expectedCugPrefix", null);
          }}
        >
          {intl.formatMessage({
            id: editingAssigned
              ? "patient.cug.prefix.cancel"
              : "patient.cug.prefix.automatic",
          })}
        </Button>
      )}
    </>
  );
}
