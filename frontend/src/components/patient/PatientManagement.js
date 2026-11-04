import React, { useEffect, useState } from "react";
import { FormattedMessage, injectIntl } from "react-intl";
import "../Style.css";
import { Heading, Grid, Column, Section, Button } from "@carbon/react";
import SearchPatientForm from "./SearchPatientForm";
import CreatePatientForm from "./CreatePatientForm";
import PageBreadCrumb from "../common/PageBreadCrumb";
import { getFromOpenElisServer } from "../utils/Utils";
let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "patient.label.modify", link: "/PatientManagement" },
];

function PatientManagement() {
  const [selectedPatient, setSelectedPatient] = useState({});
  const [searchPatientTab, setSearchPatientTab] = useState({
    kind: "primary",
    active: true,
  });
  const [newPatientTab, setNewPatientTab] = useState({
    kind: "tertiary",
    active: false,
  });
  const [patientAccess, setPatientAccess] = useState({
    canRead: false,
    canCreate: false,
    canUpdate: false,
    loaded: false,
  });

  useEffect(() => {
    getFromOpenElisServer("/rest/patient-management/access", (response) => {
      const access = response || {};
      const canSearch = access.canRead === true || access.canUpdate === true;
      const canCreate = access.canCreate === true;
      setPatientAccess({
        canRead: access.canRead === true,
        canCreate,
        canUpdate: access.canUpdate === true,
        loaded: true,
      });
      if (!canSearch && canCreate) {
        setNewPatientTab({ kind: "primary", active: true });
        setSearchPatientTab({ kind: "tertiary", active: false });
      }
    });
  }, []);

  const handleSearchPatientTab = () => {
    setNewPatientTab({ kind: "tertiary", active: false });
    setSearchPatientTab({ kind: "primary", active: true });
  };

  const handleNewPatientTab = () => {
    setNewPatientTab({ kind: "primary", active: true });
    setSearchPatientTab({ kind: "tertiary", active: false });
  };

  const getSelectedPatient = (patient) => {
    setSelectedPatient(patient);
    setNewPatientTab({ kind: "primary", active: true });
    setSearchPatientTab({ kind: "tertiary", active: false });
  };

  return (
    <>
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Section>
              <Heading>
                <FormattedMessage id="patient.label.modify" />
              </Heading>
            </Section>
          </Section>
        </Column>
      </Grid>
      <br></br>
      <div className="orderLegendBody">
        <Grid>
          {patientAccess.loaded &&
            (patientAccess.canRead || patientAccess.canUpdate) && (
              <Column lg={4} md={3} sm={2}>
                <Button
                  id="searchPatient"
                  kind={searchPatientTab.kind}
                  onClick={handleSearchPatientTab}
                >
                  <FormattedMessage
                    id="search.patient.label"
                    defaultMessage="Search for Patient"
                  />
                </Button>
              </Column>
            )}
          {patientAccess.loaded && patientAccess.canCreate && (
            <Column lg={4} md={3} sm={2}>
              <Button
                id="newPatient"
                kind={newPatientTab.kind}
                onClick={handleNewPatientTab}
              >
                <FormattedMessage
                  id="new.patient.label"
                  defaultMessage="New Patient"
                />
              </Button>
            </Column>
          )}

          {patientAccess.loaded &&
            (patientAccess.canRead || patientAccess.canUpdate) &&
            searchPatientTab.active && (
              <Column lg={16} md={8} sm={4}>
                <SearchPatientForm
                  getSelectedPatient={getSelectedPatient}
                ></SearchPatientForm>
              </Column>
            )}

          <br></br>
          {patientAccess.loaded &&
            (patientAccess.canCreate ||
              patientAccess.canRead ||
              patientAccess.canUpdate) &&
            newPatientTab.active && (
              <Column lg={16} md={8} sm={4}>
                <CreatePatientForm
                  showActionsButton={true}
                  selectedPatient={selectedPatient}
                ></CreatePatientForm>
              </Column>
            )}
        </Grid>
      </div>
    </>
  );
}

export default injectIntl(PatientManagement);
