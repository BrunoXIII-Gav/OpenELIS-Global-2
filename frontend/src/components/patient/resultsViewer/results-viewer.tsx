import React, { useState, useEffect, useRef } from "react";
import { Heading, Grid, Column, Section, Loading, Breadcrumb, BreadcrumbItem } from "@carbon/react";
import "./results-viewer.styles.scss";
import { useParams } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import { getFromOpenElisServer } from "../../utils/Utils";
import PatientHeader from "../../common/PatientHeader.js";
import PatientHistorySummaryPanel from "../historySummary/PatientHistorySummaryPanel";

interface Patient {
  firstName: string;
  lastName: string;
  gender: string;
  birthDateForDisplay: string;
  subjectNumber: string;
  nationalId: string;
  patientPK: number;
}
const RoutedResultsViewer: React.FC = () => {
  const patientObj: Patient = {
    firstName: "",
    lastName: "",
    gender: "",
    birthDateForDisplay: "",
    subjectNumber: "",
    nationalId: "",
    patientPK: null,
  };

  const { patientId } = useParams();
  const [patient, setPatient] = useState(patientObj);
  const [loadingPatient, setLoadingPatient] = useState(true);

  const componentMounted = useRef(false);

  useEffect(() => {
    componentMounted.current = true;
    getFromOpenElisServer(
      "/rest/patient-details?patientID=" + patientId,
      (response) => {
        loadPatient(response);
        if (componentMounted.current) {
          setLoadingPatient(false);
        }
      },
    );
    return () => {
      componentMounted.current = false;
    };
  }, [patientId]);

  const loadPatient = (patient) => {
    if (componentMounted.current && patient) {
      setPatient(patient);
    }
  };
  const intl = useIntl();

  if (loadingPatient) {
    return (
      <>
        <Loading></Loading>
      </>
    );
  }

  return (
    <>
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Breadcrumb>
            <BreadcrumbItem href="/">
              {intl.formatMessage({ id: "home.label" })}
            </BreadcrumbItem>
            <BreadcrumbItem href="/PatientHistory">
              {intl.formatMessage({ id: "label.search.patient" })}
            </BreadcrumbItem>
          </Breadcrumb>
        </Column>
      </Grid>
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Section>
              <Heading>
                <FormattedMessage id="label.page.patientHistory" />
              </Heading>
            </Section>
          </Section>
        </Column>
      </Grid>
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <PatientHeader
            id={patient.patientPK}
            lastName={patient.lastName}
            firstName={patient.firstName}
            gender={patient.gender}
            dob={patient.birthDateForDisplay}
            subjectNumber={patient.subjectNumber}
            nationalId={patient.nationalId}
            className="patient-header2"
          >
            {" "}
          </PatientHeader>
        </Column>
      </Grid>
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <PatientHistorySummaryPanel patientId={patientId} />
        </Column>
      </Grid>
    </>
  );
};

export default RoutedResultsViewer;
