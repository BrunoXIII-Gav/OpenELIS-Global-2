import React, { useEffect, useRef, useState } from "react";
import { Button, Stack, Grid, Column } from "@carbon/react";
import SearchPatientForm from "../patient/SearchPatientForm";
import CreatePatientForm from "../patient/CreatePatientForm";
import { FormattedMessage } from "react-intl";
import { getFromOpenElisServer } from "../utils/Utils";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";

const PatientInfo = (props) => {
  const { orderFormValues, setOrderFormValues, error, setPhoneValidation } =
    props;
  const componentMounted = useRef(false);
  const [searchPatientTab, setSearchPatientTab] = useState({
    kind: "primary",
    active: true,
  });
  const [newPatientTab, setNewPatientTab] = useState({
    kind: "tertiary",
    active: false,
  });
  const [selectedPatient, setSelectedPatient] = useState({
    id: "",
    healthRegion: [],
  });
  const emptyPatientPropertiesRef = useRef(
    createSampleOrderFormValues().patientProperties,
  );

  const getSelectedPatient = (patient) => {
    setSelectedPatient(patient);
    if (orderFormValues) {
      const patientWithStatus = {
        ...patient,
        patientUpdateStatus: "UPDATE",
      };
      setOrderFormValues({
        ...orderFormValues,
        patientUpdateStatus: "UPDATE",
        patientProperties: patientWithStatus,
      });
    }
    handleNewPatientTab();
  };

  const handleSearchPatientTab = () => {
    setSearchPatientTab({ kind: "primary", active: true });
    setNewPatientTab({ kind: "tertiary", active: false });
  };

  const handleNewPatientTab = () => {
    setNewPatientTab({ kind: "primary", active: true });
    setSearchPatientTab({ kind: "tertiary", active: false });
  };

  const handleManualNewPatientTab = () => {
    setSelectedPatient({
      id: "",
      healthRegion: [],
    });
    setOrderFormValues((previous) => ({
      ...previous,
      patientUpdateStatus: "ADD",
      patientProperties: {
        ...emptyPatientPropertiesRef.current,
      },
    }));
    handleNewPatientTab();
  };

  useEffect(() => {
    componentMounted.current = true;

    if (
      orderFormValues.patientProperties.firstName !== "" ||
      orderFormValues.patientProperties.guid !== ""
    ) {
      handleNewPatientTab();
    }
    window.scrollTo(0, 0);
    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    const patientProperties = orderFormValues?.patientProperties || {};
    const updateStatus = String(
      patientProperties.patientUpdateStatus ||
        orderFormValues?.patientUpdateStatus ||
        "",
    ).toUpperCase();
    const patientPk = String(patientProperties.patientPK || "").trim();

    if (updateStatus !== "UPDATE" || !patientPk) {
      return;
    }

    setSelectedPatient((previous) => {
      if (String(previous?.patientPK || "").trim() === patientPk) {
        return previous;
      }
      return {
        ...patientProperties,
        patientPK: patientPk,
        id: patientPk,
      };
    });
    handleNewPatientTab();
  }, [
    orderFormValues?.patientProperties?.patientPK,
    orderFormValues?.patientProperties?.patientUpdateStatus,
    orderFormValues?.patientUpdateStatus,
  ]);

  useEffect(() => {
    if (
      orderFormValues.patientProperties.guid &&
      !orderFormValues.patientProperties.lastName
    ) {
      const searchEndPoint =
        "/rest/patient-search-results?" +
        "guid=" +
        orderFormValues.patientProperties.guid;
      getFromOpenElisServer(searchEndPoint, (searchPatients) => {
        if (searchPatients.patientSearchResults.length > 0) {
          const searchEndPoint =
            "/rest/patient-details?patientID=" +
            searchPatients.patientSearchResults[0].patientID;
          getFromOpenElisServer(searchEndPoint, (patientDetails) => {
            getSelectedPatient(patientDetails);
            handleNewPatientTab();
          });
        }
      });
    }
  }, [orderFormValues.patientProperties.guid]);

  return (
    <>
      <Stack gap={10}>
        <div className="orderLegendBody">
          <Grid>
            <Column lg={16} md={8} sm={4}>
              <h3>
                <FormattedMessage id="banner.menu.patient" />
              </h3>
            </Column>
            <Column lg={4} md={4} sm={2}>
              <Button
                data-cy="searchPatientTabButton"
                kind={searchPatientTab.kind}
                onClick={handleSearchPatientTab}
              >
                <FormattedMessage id="search.patient.label" />
              </Button>
            </Column>
            <Column lg={4} md={4} sm={2}>
              <Button
                data-cy="newPatientTabButton"
                kind={newPatientTab.kind}
                onClick={handleManualNewPatientTab}
              >
                <FormattedMessage id="new.patient.label" />
              </Button>
            </Column>
            <Column lg={16} md={8} sm={4}>
              {searchPatientTab.active && (
                <SearchPatientForm getSelectedPatient={getSelectedPatient} />
              )}
            </Column>
            <Column lg={16} md={8} sm={4}>
              {newPatientTab.active && (
                <CreatePatientForm
                  showActionsButton={false}
                  isOrderEntryPatientStep={true}
                  selectedPatient={selectedPatient}
                  orderFormValues={orderFormValues}
                  setOrderFormValues={setOrderFormValues}
                  error={error}
                  setPhoneValidation={setPhoneValidation}
                />
              )}
            </Column>
          </Grid>
        </div>
      </Stack>
    </>
  );
};

export default PatientInfo;
