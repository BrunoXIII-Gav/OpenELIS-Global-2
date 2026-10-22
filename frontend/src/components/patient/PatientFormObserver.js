import { useEffect } from "react";
import { useFormikContext } from "formik";
import { sanitizePatientProperties } from "./patientPropertiesSanitizer";

const PatientFormObserver = (props) => {
  const { values } = useFormikContext();
  const { setOrderFormValues, formAction } = props;
  const sanitizedValues = sanitizePatientProperties(values);
  if (sanitizedValues.patientPK && props.readOnly) {
    sanitizedValues.readOnly = true;
  }
  const patientUpdateStatus =
    sanitizedValues.patientPK && sanitizedValues.readOnly
      ? "NO_ACTION"
      : formAction;
  const syncKey = JSON.stringify({
    patientUpdateStatus,
    patientProperties: sanitizedValues,
  });

  useEffect(() => {
    setOrderFormValues((previous) => ({
      ...(previous || {}),
      patientUpdateStatus,
      patientProperties: {
        ...sanitizedValues,
        patientUpdateStatus,
      },
    }));
  }, [patientUpdateStatus, setOrderFormValues, syncKey]);
  return null;
};

export default PatientFormObserver;
