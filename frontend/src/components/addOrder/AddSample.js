import React, { useEffect, useRef, useState } from "react";
import { Button, Link, Column, Grid } from "@carbon/react";
import { Add } from "@carbon/react/icons";
import { getFromOpenElisServer } from "../utils/Utils";
import SampleType from "./SampleType";
import { FormattedMessage } from "react-intl";
const AddSample = (props) => {
  const { samples, setSamples, error } = props;
  const componentMounted = useRef(false);

  const [rejectSampleReasons, setRejectSampleReasons] = useState([]);

  const handleAddNewSample = () => {
    let updateSamples = [...samples];
    updateSamples.push({
      index: updateSamples.length + 1,
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
    console.debug(JSON.stringify(updateSamples));
    setSamples(updateSamples);
  };

  const sampleTypeObject = (object) => {
    let newState = [...samples];
    switch (true) {
      case Object.prototype.hasOwnProperty.call(object, "sampleTypeId"):
        newState[object.sampleObjectIndex].sampleTypeId = object.sampleTypeId;
        break;
      case Object.prototype.hasOwnProperty.call(object, "sampleRejected"):
        newState[object.sampleObjectIndex].sampleRejected =
          object.sampleRejected;
        break;
      case Object.prototype.hasOwnProperty.call(object, "rejectionReason"):
        newState[object.sampleObjectIndex].rejectionReason =
          object.rejectionReason;
        break;
      case Object.prototype.hasOwnProperty.call(object, "selectedTests"):
        newState[object.sampleObjectIndex].tests = object.selectedTests || [];
        break;
      case Object.prototype.hasOwnProperty.call(object, "selectedPanels"):
        newState[object.sampleObjectIndex].panels = object.selectedPanels || [];
        break;
      case Object.prototype.hasOwnProperty.call(object, "sampleXML"):
        newState[object.sampleObjectIndex].sampleXML = object.sampleXML;
        break;
      case Object.prototype.hasOwnProperty.call(
        object,
        "requestReferralEnabled",
      ):
        newState[object.sampleObjectIndex].requestReferralEnabled =
          object.requestReferralEnabled;
        break;
      case Object.prototype.hasOwnProperty.call(object, "referralItems"):
        newState[object.sampleObjectIndex].referralItems =
          object.referralItems || [];
        break;
      case Object.prototype.hasOwnProperty.call(object, "additionalFields"):
        newState[object.sampleObjectIndex].additionalFields =
          object.additionalFields || [];
        break;
      default:
        console.debug(JSON.stringify(newState));
        props.setSamples(newState);
    }
    props.setSamples(newState);
  };

  const removeSample = (index) => {
    let updateSamples = samples.splice(index, 1);
    console.debug(JSON.stringify(updateSamples));
    setSamples(updateSamples);
  };

  const fetchRejectSampleReasons = (res) => {
    if (componentMounted.current) {
      setRejectSampleReasons(res);
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
        <FormattedMessage id="label.button.sample" />
      </h3>
      <Grid>
        <Column lg={16} md={8} sm={4}>
          <div className="orderLegendBody">
            {samples.map((sample, i) => {
              return (
                <div className="sampleType" key={i}>
                  <h4>
                    <FormattedMessage id="label.button.sample" /> {i + 1}
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
                  />
                </div>
              );
            })}

            <Button onClick={handleAddNewSample}>
              {<FormattedMessage id="sample.add.action" />}
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
