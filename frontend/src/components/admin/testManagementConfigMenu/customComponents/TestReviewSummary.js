import React from "react";
import {
  Grid,
  Column,
  Heading,
  Section,
  OrderedList,
  ListItem,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import { TestNameValue } from "./TestNamePresentation";
import { hasSummaryValue, summaryBoolean } from "./testSummaryUtils";
import "./TestSummary.scss";

const SummaryGroup = ({ title, rows }) => {
  const populated = rows.filter((row) => hasSummaryValue(row.value));
  if (!populated.length) return null;
  return (
    <Column lg={8} md={8} sm={4}>
      <Section className="test-summary__group">
        <Heading>
          <FormattedMessage id={title} />
        </Heading>
        <dl>
          {populated.map(({ label, value, content }) => (
            <div className="test-summary__row" key={label}>
              <dt>
                <FormattedMessage id={label} />
              </dt>
              <dd>{content ?? value}</dd>
            </div>
          ))}
        </dl>
      </Section>
    </Column>
  );
};

export const TestReviewSummary = ({
  values,
  panelListTag = [],
  selectedLabUnitList,
  selectedUomList,
  selectedResultTypeList,
  resultTypeCodes = [],
  selectedSampleTypeList = [],
  selectedSampleTypeResp = [],
}) => {
  const intl = useIntl();
  const row = (label, value, content) => ({ label, value, content });
  const flag = (label, value) => row(label, summaryBoolean(value, intl));
  const name = (prefix, label) =>
    row(
      label,
      [values[`${prefix}English`], values[`${prefix}French`]],
      <TestNameValue
        english={values[`${prefix}English`]}
        french={values[`${prefix}French`]}
      />,
    );
  const sameReportName =
    values.testNameEnglish === values.testReportNameEnglish &&
    values.testNameFrench === values.testReportNameFrench;
  const typeCode =
    resultTypeCodes.find(
      (type) => String(type.id) === String(values.resultType),
    )?.value ||
    selectedResultTypeList?.code ||
    values.resultType;
  const typeLabel =
    hasSummaryValue(values.resultType) && values.resultType !== "0"
      ? intl.formatMessage({
          id: `test.resultType.option.${typeCode}`,
          defaultMessage: selectedResultTypeList?.value || String(typeCode),
        })
      : null;
  const samples = selectedSampleTypeList.filter((sample) =>
    hasSummaryValue(sample.value),
  );

  return (
    <Grid fullWidth className="test-summary">
      <SummaryGroup
        title="test.summary.identity"
        rows={[
          name("testName", "sample.entry.project.testName"),
          ...(!sameReportName
            ? [name("testReportName", "reporting.label.testName")]
            : []),
          row("test.section.label", selectedLabUnitList?.value),
          row(
            "field.panel",
            panelListTag
              .map((panel) => panel.value)
              .filter(hasSummaryValue)
              .join(", "),
          ),
          row("label.loinc", values.loinc),
        ]}
      />
      <SummaryGroup
        title="test.summary.result"
        rows={[
          flag("test.summary.primaryActive", values.resultActive),
          row("field.resultType", typeLabel),
          row("field.resultName", values.resultName),
          row("field.uom", selectedUomList?.value),
          row("field.referenceValue", values.dictionaryReference),
          row("label.default.result", values.defaultTestResult),
        ]}
      />
      <SummaryGroup
        title="test.summary.behavior"
        rows={[
          flag("dictionary.category.isActive", values.active),
          flag("label.orderable", values.orderable),
          flag("test.directSampleUsage", values.directSampleUsageEnabled),
          flag(
            "test.skipValidationWhenParentComplete",
            values.skipValidationWhenParentComplete,
          ),
        ]}
      />
      {samples.length > 0 && (
        <Column lg={8} md={8} sm={4}>
          <Section className="test-summary__group">
            <Heading>
              <FormattedMessage id="sample.type.and.test.sort.order" />
            </Heading>
            {samples.map((sample) => (
              <Section key={sample.id} className="test-summary__sample">
                <Heading>{sample.value}</Heading>
                {selectedSampleTypeResp
                  .filter((item) => item.sampleTypeId === sample.id)
                  .map(
                    (item, index) =>
                      item.tests?.length > 0 && (
                        <OrderedList key={`${sample.id}-${index}`}>
                          {item.tests.map((test) => (
                            <ListItem key={test.id}>{test.name}</ListItem>
                          ))}
                        </OrderedList>
                      ),
                  )}
              </Section>
            ))}
          </Section>
        </Column>
      )}
    </Grid>
  );
};
