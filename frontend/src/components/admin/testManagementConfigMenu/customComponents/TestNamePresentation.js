import React, { useContext, useState } from "react";
import { Checkbox, TextInput } from "@carbon/react";
import { useFormikContext } from "formik";
import { FormattedMessage, useIntl } from "react-intl";
import { ConfigurationContext } from "../../../layout/Layout";

export const namesMatch = (english, french) =>
  (english ?? "") === (french ?? "");

export const canSimplifyTestNames = (values) =>
  namesMatch(values.testNameEnglish, values.testNameFrench) &&
  namesMatch(values.testReportNameEnglish, values.testReportNameFrench);

export const useSimplifiedTestNames = () => {
  const configuration = useContext(ConfigurationContext);
  return (
    String(configuration?.configurationProperties?.SIMPLIFIED_TEST_NAMES) ===
    "true"
  );
};

// Only fill an empty report pair. Existing report names are never replaced here.
export const prepareSimplifiedNames = (values) => {
  if (!canSimplifyTestNames(values) || values.testReportNameEnglish)
    return values;
  return {
    ...values,
    testReportNameEnglish: values.testNameEnglish,
    testReportNameFrench: values.testNameFrench,
  };
};

export const TestNameValue = ({ english, french }) => {
  const simplified = useSimplifiedTestNames();
  if (simplified && namesMatch(english, french)) return <>{english}</>;
  return (
    <>
      <FormattedMessage id="english.label" />
      {": "}
      {english}
      <br />
      <FormattedMessage id="french.label" />
      {": "}
      {french}
    </>
  );
};

export const SimplifiedTestNameFields = () => {
  const intl = useIntl();
  const { values, initialValues, setValues, handleBlur, touched, errors } =
    useFormikContext();
  const [customReportName, setCustomReportName] = useState(
    initialValues.testReportNameEnglish !== initialValues.testNameEnglish,
  );
  const label = (id) => intl.formatMessage({ id });
  return (
    <>
      <TextInput
        id="testNameEn"
        name="testNameEnglish"
        labelText={label("sample.entry.project.testName")}
        required
        maxLength={255}
        value={values.testNameEnglish}
        onBlur={handleBlur}
        invalid={Boolean(touched.testNameEnglish && errors.testNameEnglish)}
        invalidText={errors.testNameEnglish}
        onChange={(event) => {
          const name = event.target.value;
          setValues((previous) => ({
            ...previous,
            testNameEnglish: name,
            testNameFrench: name,
            ...(!customReportName
              ? {
                  testReportNameEnglish: name,
                  testReportNameFrench: name,
                }
              : {}),
          }));
        }}
      />
      <br />
      <Checkbox
        id="custom-test-report-name"
        labelText={label("test.names.customReportName")}
        checked={customReportName}
        onChange={(_, { checked }) => {
          setCustomReportName(checked);
          if (!checked)
            setValues((previous) => ({
              ...previous,
              testReportNameEnglish: previous.testNameEnglish,
              testReportNameFrench: previous.testNameFrench,
            }));
        }}
      />
      {customReportName && (
        <>
          <br />
          <TextInput
            id="reportingTestNameEn"
            name="testReportNameEnglish"
            labelText={label("reporting.label.testName")}
            required
            maxLength={255}
            value={values.testReportNameEnglish}
            onBlur={handleBlur}
            invalid={Boolean(
              touched.testReportNameEnglish && errors.testReportNameEnglish,
            )}
            invalidText={errors.testReportNameEnglish}
            onChange={(event) => {
              const name = event.target.value;
              setValues((previous) => ({
                ...previous,
                testReportNameEnglish: name,
                testReportNameFrench: name,
              }));
            }}
          />
        </>
      )}
    </>
  );
};
