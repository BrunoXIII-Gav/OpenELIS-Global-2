import React, { useEffect, useRef, useState } from "react";
import { Button, Link, Column, Grid } from "@carbon/react";
import { Add } from "@carbon/react/icons";
import { FormattedMessage } from "react-intl";
import SampleType from "../addOrder/SampleType";
import { getFromOpenElisServer } from "../utils/Utils";
import { buildEmptyModifySample } from "./modifyOrderSamples";

const EditSample = (props) => {
  const {
    samples,
    setSamples,
    error,
    patientId,
    patientNationalId,
    onRemoveExistingSample,
  } = props;

  const componentMounted = useRef(false);
  const [rejectSampleReasons, setRejectSampleReasons] = useState([]);

  const handleAddNewSample = () => {
    setSamples((previous) => [
      ...previous,
      buildEmptyModifySample(previous.length),
    ]);
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
      return newState;
    });
  };

  const fetchRejectSampleReasons = (response) => {
    if (componentMounted.current) {
      setRejectSampleReasons(Array.isArray(response) ? response : []);
    }
  };

  const handleRemoveSample = (event, sample) => {
    event.preventDefault();
    if (sample?.existingSampleItemId && sample?.canRemoveSample) {
      onRemoveExistingSample?.(sample.existingSampleItemId);
    }
    setSamples((previous) =>
      previous
        .filter((entry) => entry !== sample)
        .map((entry, index) => ({ ...entry, index })),
    );
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

  const normalizedPatientId = String(patientId || "").trim();
  const normalizedPatientNationalId = String(patientNationalId || "").trim();
  const canGenerateCug =
    normalizedPatientId !== "" && normalizedPatientNationalId !== "";
  const patientCugKey = [normalizedPatientId, normalizedPatientNationalId].join(
    "|",
  );

  return (
    <>
      <h3>
        <FormattedMessage id="order.sample.section.heading" />
      </h3>
      <Grid>
        <Column lg={16} md={8} sm={4}>
          <div className="orderLegendBody">
            {samples.map((sample, index) => {
              const canRemoveCard =
                !sample?.existingSampleItemId || sample?.canRemoveSample;

              return (
                <div className="sampleType" key={sample.existingSampleItemId || index}>
                  <h4>
                    <FormattedMessage id="order.sample.card.label" />{" "}
                    {index + 1}
                    <span className="requiredlabel">*</span>
                  </h4>
                  {canRemoveCard && (
                    <Link href="#" onClick={(event) => handleRemoveSample(event, sample)}>
                      <FormattedMessage id="sample.remove.action" />
                    </Link>
                  )}
                  <SampleType
                    index={index}
                    rejectSampleReasons={rejectSampleReasons}
                    sample={sample}
                    sampleTypeObject={sampleTypeObject}
                    error={error}
                    patientId={normalizedPatientId}
                    canGenerateCug={canGenerateCug}
                    patientCugKey={patientCugKey}
                    disableSampleTypeSelection={!!sample?.existingSampleItemId}
                    lockedTestIds={sample?.lockedTestIds || []}
                    existingCugs={(samples || [])
                      .filter((_, sampleIndex) => sampleIndex !== index)
                      .map((entry) => entry?.sampleXML?.cug)
                      .filter(Boolean)}
                  />
                </div>
              );
            })}

            <Button onClick={handleAddNewSample}>
              <FormattedMessage id="order.sample.add.action" />
              &nbsp; &nbsp;
              <Add size={16} />
            </Button>
          </div>
        </Column>
      </Grid>
    </>
  );
};

export default EditSample;
