import React, { useState, useEffect, useRef } from "react";
import {
  Heading,
  Grid,
  Column,
  Section,
  Loading,
  Breadcrumb,
  BreadcrumbItem,
} from "@carbon/react";
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
  const [restrictedPatientFieldTags, setRestrictedPatientFieldTags] = useState<
    string[]
  >([]);
  const [loadingAccess, setLoadingAccess] = useState(true);

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
    getFromOpenElisServer("/rest/patient-management/access", (response) => {
      if (componentMounted.current) {
        setRestrictedPatientFieldTags(
          Array.isArray(response?.restrictedFieldTagKeys)
            ? response.restrictedFieldTagKeys
            : [],
        );
        setLoadingAccess(false);
      }
    });
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
  const hasRestrictedPatientTag = (tagKey: string) =>
    restrictedPatientFieldTags.includes(tagKey);

  if (loadingPatient || loadingAccess) {
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
              {intl.formatMessage({ id: "label.page.patientHistory" })}
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
            lastName={
              hasRestrictedPatientTag("patient-last-name")
                ? ""
                : patient.lastName
            }
            firstName={
              hasRestrictedPatientTag("patient-first-name")
                ? ""
                : patient.firstName
            }
            gender={
              hasRestrictedPatientTag("patient-gender") ? "" : patient.gender
            }
            dob={
              hasRestrictedPatientTag("patient-birth-date-age")
                ? ""
                : patient.birthDateForDisplay
            }
            subjectNumber={
              hasRestrictedPatientTag("patient-subject-number")
                ? ""
                : patient.subjectNumber
            }
            nationalId={
              hasRestrictedPatientTag("patient-national-id")
                ? ""
                : patient.nationalId
            }
            hasPhoto={!hasRestrictedPatientTag("patient-photo")}
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
