import React, { useEffect, useRef, useState } from "react";
import { Button, Link, Column, Grid } from "@carbon/react";
import { Add } from "@carbon/react/icons";
import { getFromOpenElisServer } from "../utils/Utils";
import SampleType from "./SampleType";
import { FormattedMessage } from "react-intl";

const buildEmptySample = (index) => ({
  index,
  sampleRejected: false,
  rejectionReason: "",
  requestReferralEnabled: false,
  referralItems: [],
  sampleTypeId: "",
  sampleXML: null,
  panels: [],
  tests: [],
  additionalFields: [],
});

const AddSample = (props) => {
  const { samples, setSamples, error, orderFormValues } = props;
  const componentMounted = useRef(false);

  const [rejectSampleReasons, setRejectSampleReasons] = useState([]);
  const patientUpdateStatus = String(
    orderFormValues?.patientProperties?.patientUpdateStatus ||
      orderFormValues?.patientUpdateStatus ||
      "ADD",
  ).toUpperCase();
  const patientProperties = orderFormValues?.patientProperties || {};
  const patientPk = String(patientProperties?.patientPK || "").trim();
  const patientGuid = String(patientProperties?.guid || "").trim();
  const patientNationalId = String(patientProperties?.nationalId || "").trim();
  const patientSubjectNumber = String(
    patientProperties?.subjectNumber || "",
  ).trim();

  const canGenerateCug =
    (patientUpdateStatus === "UPDATE" && patientPk !== "") ||
    (patientUpdateStatus === "ADD" &&
      (patientNationalId !== "" ||
        patientSubjectNumber !== "" ||
        patientGuid !== ""));

  const patientIdForCug =
    patientUpdateStatus === "UPDATE" && patientPk !== "" ? patientPk : "";
  const patientCugKey = [
    patientUpdateStatus,
    patientPk,
    patientGuid,
    patientSubjectNumber,
    patientNationalId,
  ].join("|");

  const handleAddNewSample = () => {
    setSamples((previous) => {
      const updateSamples = [
        ...previous,
        buildEmptySample(previous.length + 1),
      ];
      console.debug(JSON.stringify(updateSamples));
      return updateSamples;
    });
  };

  const sampleTypeObject = (object) => {
    const sampleIndex = object.sampleObjectIndex;
    if (sampleIndex === undefined || sampleIndex === null) {
      return;
    }

    setSamples((previous) => {
      const newState = [...previous];
      const previousSample = newState[sampleIndex];
      if (!previousSample) {
        return previous;
      }

      const updatedSample = { ...previousSample };

      if (Object.prototype.hasOwnProperty.call(object, "sampleTypeId")) {
        updatedSample.sampleTypeId = object.sampleTypeId;
      } else if (
        Object.prototype.hasOwnProperty.call(object, "sampleRejected")
      ) {
        updatedSample.sampleRejected = object.sampleRejected;
      } else if (
        Object.prototype.hasOwnProperty.call(object, "rejectionReason")
      ) {
        updatedSample.rejectionReason = object.rejectionReason;
      } else if (
        Object.prototype.hasOwnProperty.call(object, "selectedTests")
      ) {
        updatedSample.tests = [...(object.selectedTests || [])];
      } else if (
        Object.prototype.hasOwnProperty.call(object, "selectedPanels")
      ) {
        updatedSample.panels = [...(object.selectedPanels || [])];
      } else if (Object.prototype.hasOwnProperty.call(object, "sampleXML")) {
        updatedSample.sampleXML = object.sampleXML
          ? {
              ...object.sampleXML,
              additionalFieldValues: {
                ...(object.sampleXML.additionalFieldValues || {}),
              },
            }
          : object.sampleXML;
      } else if (
        Object.prototype.hasOwnProperty.call(object, "requestReferralEnabled")
      ) {
        updatedSample.requestReferralEnabled = object.requestReferralEnabled;
      } else if (
        Object.prototype.hasOwnProperty.call(object, "referralItems")
      ) {
        updatedSample.referralItems = [...(object.referralItems || [])];
      } else if (
        Object.prototype.hasOwnProperty.call(object, "additionalFields")
      ) {
        updatedSample.additionalFields = [...(object.additionalFields || [])];
      }

      newState[sampleIndex] = updatedSample;
      console.debug(JSON.stringify(newState));
      return newState;
    });
  };

  const removeSample = (index) => {
    setSamples((previous) => {
      const updateSamples = previous.filter(
        (_, sampleIndex) => sampleIndex !== index,
      );
      console.debug(JSON.stringify(updateSamples));
      return updateSamples;
    });
  };

  const fetchRejectSampleReasons = (res) => {
    if (componentMounted.current) {
      setRejectSampleReasons(Array.isArray(res) ? res : []);
    }
  };

  const handleRemoveSample = (e, sample) => {
    e.preventDefault();
    let filtered = samples.filter(function (element) {
      return element !== sample;
    });
    console.debug(JSON.stringify(filtered));
    setSamples(filtered);
  };

  useEffect(() => {
    componentMounted.current = true;
    getFromOpenElisServer(
      "/rest/test-rejection-reasons",
      fetchRejectSampleReasons,
    );
    window.scrollTo(0, 0);
    return () => {
      componentMounted.current = false;
    };
  }, []);

  return (
    <>
      <h3>
        <FormattedMessage id="order.sample.section.heading" />
      </h3>
      <Grid>
        <Column lg={16} md={8} sm={4}>
          <div className="orderLegendBody">
            {samples.map((sample, i) => {
              return (
                <div className="sampleType" key={i}>
                  <h4>
                    <FormattedMessage id="order.sample.card.label" /> {i + 1}
                    <span className="requiredlabel">*</span>
                  </h4>
                  <Link href="#" onClick={(e) => handleRemoveSample(e, sample)}>
                    {<FormattedMessage id="sample.remove.action" />}
                  </Link>
                  <SampleType
                    index={i}
                    rejectSampleReasons={rejectSampleReasons}
                    removeSample={removeSample}
                    sample={sample}
                    setSample={(newSample) => {
                      let newSamples = [...samples];
                      newSamples[i] = newSample;
                      setSamples(newSamples);
                    }}
                    sampleTypeObject={sampleTypeObject}
                    error={error}
                    patientId={patientIdForCug}
                    canGenerateCug={canGenerateCug}
                    patientCugKey={patientCugKey}
                    existingCugs={(samples || [])
                      .filter((_, sampleIndex) => sampleIndex !== i)
                      .map((entry) => entry?.sampleXML?.cug)
                      .filter(Boolean)}
                  />
                </div>
              );
            })}

            <Button onClick={handleAddNewSample}>
              {<FormattedMessage id="order.sample.add.action" />}
              &nbsp; &nbsp;
              <Add size={16} />
            </Button>
          </div>
        </Column>
      </Grid>
    </>
  );
};

export default AddSample;
