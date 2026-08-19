import { useEffect } from "react";
import { useFormikContext } from "formik";
import { sanitizePatientProperties } from "./patientPropertiesSanitizer";

const PatientFormObserver = (props) => {
  const { values } = useFormikContext();
  const { setOrderFormValues, formAction } = props;
  const sanitizedValues = sanitizePatientProperties(values);
  const syncKey = JSON.stringify({
    patientUpdateStatus: formAction,
    patientProperties: sanitizedValues,
  });

  useEffect(() => {
    setOrderFormValues((previous) => ({
      ...(previous || {}),
      patientUpdateStatus: formAction,
      patientProperties: {
        ...sanitizedValues,
      },
    }));
  }, [formAction, setOrderFormValues, syncKey]);
  return null;
};

export default PatientFormObserver;
