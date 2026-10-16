import React from "react";
import { FormattedMessage } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb";
import AuditTrailReport from "./AuditTrailReport.js";
import { Accordion, AccordionItem, Heading } from "@carbon/react";
import AuditEventReport from "./AuditEventReport";

const AuditTrailReportIndex = () => {
  return (
    <>
      <br />
      <PageBreadCrumb breadcrumbs={[{ label: "home.label", link: "/" }]} />
      <div className="orderLegendBody">
        <>
          <Heading>
            <FormattedMessage id="audit.events.title" />
          </Heading>
          <AuditEventReport />
          <Accordion>
            <AccordionItem
              title={<FormattedMessage id="audit.events.legacy" />}
            >
              <AuditTrailReport
                report={"auditTrail"}
                id={"reports.auditTrail"}
              />
            </AccordionItem>
          </Accordion>
        </>
      </div>
    </>
  );
};

export default AuditTrailReportIndex;
