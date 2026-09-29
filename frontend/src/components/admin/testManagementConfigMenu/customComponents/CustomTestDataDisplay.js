import React from "react";
import { Heading, Section } from "@carbon/react";
import { FormattedMessage } from "react-intl";
import { TestNameValue } from "./TestNamePresentation";
import { hasSummaryValue } from "./testSummaryUtils";
import "./TestSummary.scss";

// Compact context from the current wizard values, not a second saved-data review.
export const CustomTestDataDisplay = ({ testToDisplay }) => {
  if (!testToDisplay) return null;
  const { localization = {}, testUnit, sampleType } = testToDisplay;
  const hasName =
    hasSummaryValue(localization.english) ||
    hasSummaryValue(localization.french);
  return (
    <Section className="test-summary-header">
      <Heading>
        {hasName ? (
          <TestNameValue
            english={localization.english}
            french={localization.french}
          />
        ) : (
          <FormattedMessage id="test.summary.new" />
        )}
      </Heading>
      {(hasSummaryValue(testUnit) || hasSummaryValue(sampleType)) && (
        <dl className="test-summary-header__details">
          {hasSummaryValue(testUnit) && (
            <div>
              <dt>
                <FormattedMessage id="test.section.label" />
              </dt>
              <dd>{testUnit}</dd>
            </div>
          )}
          {hasSummaryValue(sampleType) && (
            <div>
              <dt>
                <FormattedMessage id="field.sampleType" />
              </dt>
              <dd>{sampleType}</dd>
            </div>
          )}
        </dl>
      )}
    </Section>
  );
};
