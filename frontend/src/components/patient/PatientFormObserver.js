import { useEffect } from "react";
import { useFormikContext } from "formik";

const PatientFormObserver = (props) => {
  const { values } = useFormikContext();
  const { setOrderFormValues, formAction } = props;
  const syncKey = JSON.stringify({
    patientUpdateStatus: formAction,
    patientProperties: values,
  });

  useEffect(() => {
    setOrderFormValues((previous) => ({
      ...(previous || {}),
      patientUpdateStatus: formAction,
      patientProperties: {
        ...values,
      },
    }));
  }, [formAction, setOrderFormValues, syncKey]);
  return null;
};

export default PatientFormObserver;
