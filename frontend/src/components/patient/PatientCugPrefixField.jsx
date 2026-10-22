import React, { useEffect, useRef, useState } from "react";
import { TextInput } from "@carbon/react";
import { useFormikContext } from "formik";
import { useIntl } from "react-intl";
import { getFromOpenElisServer } from "../utils/Utils";
import CreatePatientValidationSchema from "../formModel/validationSchema/CreatePatientValidationShema";

export default function PatientCugPrefixField({
  validate,
  ready,
  onReadyChange,
  forOrderEntry = false,
}) {
  const { values } = useFormikContext();
  const intl = useIntl();
  const [preview, setPreview] = useState({ prefix: "", assigned: false });
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
      onReadyChange(valid);
      if (values.patientPK && values.cugPrefix) return;
      if (!values.patientPK && !valid) return;
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
  }, [valuesKey, ready, intl, onReadyChange, forOrderEntry]);

  return (
    <TextInput
      id="patient-cug-prefix"
      labelText={intl.formatMessage({ id: "patient.cug.prefix.label" })}
      value={
        values.patientPK && values.cugPrefix ? values.cugPrefix : preview.prefix
      }
      readOnly
      helperText={intl.formatMessage({
        id:
          values.patientPK && (values.cugPrefix || preview.assigned)
            ? "patient.cug.prefix.saved"
            : "patient.cug.prefix.provisional",
      })}
    />
  );
}
