import { useEffect } from "react";
import { useFormikContext } from "formik";

const PatientRegistrationReadiness = ({ ready, validate, onReadyChange }) => {
  const { values } = useFormikContext();
  useEffect(() => {
    onReadyChange(Boolean(ready) && Object.keys(validate(values)).length === 0);
  }, [ready, values, validate, onReadyChange]);
  return null;
};
export default PatientRegistrationReadiness;
