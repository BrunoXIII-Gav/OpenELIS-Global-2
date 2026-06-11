import { FormattedMessage } from "react-intl";
import React from "react";
export const patientSearchHeaderData = [
  {
    key: "lastName",
    header: <FormattedMessage id="patient.last.name" />,
  },
  {
    key: "firstName",
    header: <FormattedMessage id="patient.first.name" />,
  },
  {
    key: "gender",
    header: <FormattedMessage id="patient.gender" />,
  },
  {
    key: "dob",
    header: <FormattedMessage id="patient.dob" />,
  },
  {
    key: "nationalId",
    header: <FormattedMessage id="patient.merge.nationalId" />,
  },
  {
    key: "dataSourceName",
    header: <FormattedMessage id="patient.dataSourceName" />,
  },
];
