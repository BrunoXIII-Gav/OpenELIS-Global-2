import React, { useState, useEffect, useRef } from "react";
import SearchPatientForm from "../patient/SearchPatientForm";
import {
  Button,
  Column,
  Grid,
  Form,
  Select,
  SelectItem,
  Loading,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import CustomLabNumberInput from "../common/CustomLabNumberInput";
import { getFromOpenElisServer } from "../utils/Utils";

function SearchOrder() {
  const intl = useIntl();
  const [selectedPatient, setSelectedPatient] = useState({});
  const componentMounted = useRef(false);
  const [accessionNumber, setAccessionNumber] = useState("");
  const [patientOrders, setPatientOrders] = useState([]);
  const [selectedAccessionNumber, setSelectedAccessionNumber] = useState("");
  const [loadingPatientOrders, setLoadingPatientOrders] = useState(false);

  const getSelectedPatient = (patient) => {
    setSelectedPatient(patient);
    setPatientOrders([]);
    setSelectedAccessionNumber("");
  };

  const handleSearch = (e) => {
    e.preventDefault();
    var labNumber = accessionNumber ? accessionNumber.trim() : "";
    window.location.href = "/ModifyOrder?accessionNumber=" + labNumber;
  };

  useEffect(() => {
    componentMounted.current = true;
    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedPatient?.patientPK) {
      return;
    }

    setLoadingPatientOrders(true);
    getFromOpenElisServer(
      "/rest/patient-orders?patientId=" + selectedPatient.patientPK,
      (response) => {
        if (!componentMounted.current) {
          return;
        }

        const orders = Array.isArray(response) ? response : [];
        setPatientOrders(orders);
        setSelectedAccessionNumber(
          orders.length > 0 ? orders[0].accessionNumber : "",
        );
        setLoadingPatientOrders(false);
      },
    );
  }, [selectedPatient?.patientPK]);

  const openOrderForEdition = () => {
    if (!selectedAccessionNumber) {
      return;
    }
    window.location.href =
      "/ModifyOrder?accessionNumber=" +
      selectedAccessionNumber +
      "&patientId=" +
      selectedPatient.patientPK;
  };

  return (
    <>
      {loadingPatientOrders && <Loading />}
      <div className="orderLegendBody">
        <Form onSubmit={handleSearch}>
          <Grid>
            <Column lg={16} md={8} sm={4}>
              <h4>
                <FormattedMessage id="sample.label.search.labnumber" />
              </h4>
            </Column>
            <Column lg={16} md={8} sm={4}>
              <CustomLabNumberInput
                placeholder={"Enter Lab No"}
                id="labNumber"
                name="labNumber"
                value={accessionNumber}
                onChange={(e, rawVal) =>
                  setAccessionNumber(rawVal ? rawVal : e?.target?.value)
                }
                labelText={<FormattedMessage id="search.label.accession" />}
              />
            </Column>
            <Column lg={16} md={8} sm={4}>
              <br></br>
            </Column>
            <Column lg={16} md={8} sm={4}>
              <Button data-cy="submit-button" type="submit">
                <FormattedMessage id="label.button.submit" />
              </Button>
            </Column>
          </Grid>
        </Form>
      </div>
      <div className="orderLegendBody">
        <Grid>
          <Column lg={16} md={8} sm={4}>
            <h4>
              <FormattedMessage id="sample.label.search.patient" />
            </h4>
          </Column>
          <Column lg={16} md={8} sm={4}>
            <SearchPatientForm
              getSelectedPatient={getSelectedPatient}
              hideNationalIdColumn={true}
              disableNationalIdSearch={true}
            ></SearchPatientForm>
            {!!selectedPatient?.patientPK && (
              <Grid>
                <Column lg={16} md={8} sm={4}>
                  <br />
                </Column>
                <Column lg={8} md={4} sm={4}>
                  <Select
                    id="patientOrders"
                    labelText={intl.formatMessage({
                      id: "patient.merge.orders",
                    })}
                    value={selectedAccessionNumber}
                    onChange={(e) => setSelectedAccessionNumber(e.target.value)}
                  >
                    {patientOrders.length === 0 ? (
                      <SelectItem
                        value=""
                        text={intl.formatMessage({
                          id: "sample.label.noorder",
                        })}
                      />
                    ) : (
                      patientOrders.map((order) => (
                        <SelectItem
                          key={order.sampleId}
                          value={order.accessionNumber}
                          text={order.accessionNumber}
                        />
                      ))
                    )}
                  </Select>
                </Column>
                <Column lg={8} md={4} sm={4}>
                  <br />
                  <Button
                    kind="tertiary"
                    type="button"
                    disabled={!selectedAccessionNumber}
                    onClick={openOrderForEdition}
                  >
                    <FormattedMessage id="eorder.button.editOrder" />
                  </Button>
                </Column>
              </Grid>
            )}
          </Column>
        </Grid>
      </div>
    </>
  );
}

export default SearchOrder;
