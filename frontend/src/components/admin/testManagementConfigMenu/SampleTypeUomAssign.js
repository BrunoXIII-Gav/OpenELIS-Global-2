import React, { useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  Column,
  Grid,
  Heading,
  Loading,
  Section,
  Select,
  SelectItem,
} from "@carbon/react";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import { NotificationContext } from "../../layout/Layout.js";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils.js";
import { CustomSharedList } from "./CustomSharedList.js";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "master.lists.page.test.management",
    link: "/MasterListsPage/testManagementConfigMenu",
  },
  {
    label: "configuration.uom.manage",
    link: "/MasterListsPage/UomManagement",
  },
  {
    label: "configuration.uom.assign",
    link: "/MasterListsPage/SampleTypeUomAssign",
  },
];

function SampleTypeUomAssign() {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const mountedRef = useRef(false);

  const [isLoading, setIsLoading] = useState(true);
  const [formData, setFormData] = useState(null);
  const [sampleTypeId, setSampleTypeId] = useState("");
  const [selectedSampleTypeData, setSelectedSampleTypeData] = useState(null);

  const loadAssignments = () => {
    setIsLoading(true);
    getFromOpenElisServer("/rest/SampleTypeUomAssign", (response) => {
      if (!mountedRef.current) {
        return;
      }
      setFormData(response || null);
      setIsLoading(false);
    });
  };

  useEffect(() => {
    mountedRef.current = true;
    loadAssignments();
    return () => {
      mountedRef.current = false;
      setIsLoading(false);
    };
  }, []);

  const sampleTypes = formData?.sampleTypeList || [];
  const allUoms = formData?.uomList || [];
  const assignmentMap = formData?.sampleTypeUomMap || {};

  const availableBySampleTypeId = useMemo(() => {
    const map = {};
    sampleTypes.forEach((sampleType) => {
      const assigned = assignmentMap[sampleType.id] || [];
      const assignedIds = new Set(assigned.map((uom) => String(uom.id)));
      map[sampleType.id] = allUoms.filter(
        (uom) => !assignedIds.has(String(uom.id)),
      );
    });
    return map;
  }, [allUoms, assignmentMap, sampleTypes]);

  useEffect(() => {
    if (!sampleTypeId) {
      setSelectedSampleTypeData(null);
      return;
    }

    const sampleType = sampleTypes.find(
      (entry) => String(entry.id) === String(sampleTypeId),
    );
    if (!sampleType) {
      setSelectedSampleTypeData(null);
      return;
    }

    setSelectedSampleTypeData({
      sampleType,
      assignedUoms: assignmentMap[sampleTypeId] || [],
      availableUoms: availableBySampleTypeId[sampleTypeId] || [],
    });
  }, [availableBySampleTypeId, assignmentMap, sampleTypeId, sampleTypes]);

  const handleSave = () => {
    if (!sampleTypeId || !selectedSampleTypeData) {
      window.location.reload();
      return;
    }

    postToOpenElisServerJsonResponse(
      "/rest/SampleTypeUomAssign",
      JSON.stringify({
        sampleTypeId,
        unitOfMeasureIds: selectedSampleTypeData.assignedUoms.map((uom) =>
          String(uom.id),
        ),
      }),
      (response) => {
        if (!mountedRef.current) {
          return;
        }

        if (response) {
          setFormData(response);
          addNotification({
            title: intl.formatMessage({ id: "notification.title" }),
            message: intl.formatMessage({ id: "uom.assign.notification.save" }),
            kind: NotificationKinds.success,
          });
          return;
        }

        addNotification({
          kind: NotificationKinds.error,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "server.error.msg" }),
        });
        setNotificationVisible(true);
      },
    );
  };

  if (isLoading) {
    return <Loading />;
  }

  return (
    <>
      {notificationVisible ? <AlertDialog /> : null}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <div className="orderLegendBody">
          <Grid fullWidth>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Heading>
                  <FormattedMessage id="label.button.select" />
                </Heading>
              </Section>
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <Grid fullWidth>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Heading>
                  <FormattedMessage id="configuration.uom.assign" />
                </Heading>
              </Section>
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <Grid fullWidth>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <FormattedMessage id="configuration.uom.assign.explain" />
              </Section>
            </Column>
          </Grid>
          <br />
          <Grid fullWidth>
            <Column lg={16} md={8} sm={4}>
              <Section>
                {sampleTypes.length > 0 ? (
                  <Select
                    size="sm"
                    id="sampleTypeUomAssignList"
                    labelText={
                      <span style={{ fontSize: "1.2rem", fontWeight: 500 }}>
                        {`${intl.formatMessage({ id: "sample.type" })} : `}
                      </span>
                    }
                    value={sampleTypeId}
                    onChange={(event) => {
                      setSampleTypeId(event.target.value);
                    }}
                  >
                    <SelectItem
                      disabled
                      hidden
                      value=""
                      text={intl.formatMessage({
                        id: "configuration.uom.assign.select",
                      })}
                    />
                    {sampleTypes
                      .filter((sampleType) => String(sampleType.id) !== "0")
                      .map((sampleType) => (
                        <SelectItem
                          key={sampleType.id}
                          value={sampleType.id}
                          text={sampleType.value}
                        />
                      ))}
                  </Select>
                ) : null}
              </Section>
            </Column>
          </Grid>
          <br />
          <Grid fullWidth>
            <Column lg={16} md={8} sm={4}>
              {selectedSampleTypeData && sampleTypeId ? (
                <CustomSharedList
                  leftTitle={`${selectedSampleTypeData.sampleType.value} - ${intl.formatMessage(
                    {
                      id: "configuration.uom.assign.assigned",
                    },
                  )}`}
                  rightTitle={intl.formatMessage({
                    id: "configuration.uom.assign.available",
                  })}
                  leftList={selectedSampleTypeData.assignedUoms}
                  rightList={selectedSampleTypeData.availableUoms}
                  renderItem={(item) => item}
                  onChange={(newLeft, newRight) => {
                    setSelectedSampleTypeData((prev) => ({
                      ...prev,
                      assignedUoms: newLeft,
                      availableUoms: newRight,
                    }));
                  }}
                />
              ) : null}
            </Column>
          </Grid>
          <br />
          <Grid fullWidth>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Button kind="primary" onClick={handleSave}>
                  <FormattedMessage id="label.button.save" />
                </Button>
              </Section>
            </Column>
          </Grid>
        </div>
      </div>
    </>
  );
}

export default injectIntl(SampleTypeUomAssign);
