import React, { useContext, useState, useEffect, useRef } from "react";
import { Heading, Button, Loading, Grid, Column, Section, Select, SelectItem } from "@carbon/react";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils.js";
import { NotificationContext } from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { CustomSharedList } from "./CustomSharedList.js";

let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "master.lists.page.test.management",
    link: "/MasterListsPage/testManagementConfigMenu",
  },
  {
    label: "configuration.method",
    link: "/MasterListsPage/MethodManagement",
  },
  {
    label: "configuration.method.assign",
    link: "/MasterListsPage/MethodTestAssign",
  },
];

function MethodTestAssign() {
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const intl = useIntl();
  const [isLoading, setIsLoading] = useState(true);
  const [methodTestList, setMethodTestList] = useState([]);
  const [methodId, setMethodId] = useState("");
  const [selectedMethodData, setSelectedMethodData] = useState({});

  const componentMounted = useRef(false);

  const handlePostMethodTestAssignCall = () => {
    if (!methodId || !selectedMethodData?.selectedMethod) {
      window.location.reload();
      return;
    }

    postToOpenElisServerJsonResponse(
      "/rest/MethodTestAssign",
      JSON.stringify({
        methodId: methodId,
        currentTests: selectedMethodData?.selectedMethod?.tests?.map((item) =>
          String(item.id),
        ),
        availableTests: selectedMethodData?.selectedMethod?.availableTests?.map(
          (item) => String(item.id),
        ),
      }),
      (res) => {
        if (res) {
          setIsLoading(false);
          addNotification({
            title: intl.formatMessage({ id: "notification.title" }),
            message: intl.formatMessage({
              id: "notification.user.post.save.success",
            }),
            kind: NotificationKinds.success,
          });
          setTimeout(() => {
            window.location.reload();
          }, 200);
          return;
        }

        addNotification({
          kind: NotificationKinds.error,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "server.error.msg" }),
        });
        setNotificationVisible(true);
        setTimeout(() => {
          window.location.reload();
        }, 200);
      },
    );
  };

  useEffect(() => {
    if (componentMounted.current && methodId) {
      getFromOpenElisServer(
        `/rest/MethodTestAssign?methodId=${methodId}`,
        (res) => {
          if (res) {
            setSelectedMethodData(res);
          }
        },
      );
    }
  }, [methodId]);

  useEffect(() => {
    componentMounted.current = true;
    setIsLoading(true);
    getFromOpenElisServer(`/rest/MethodTestAssign`, (res) => {
      if (res) {
        setMethodTestList(res);
      }
      setIsLoading(false);
    });

    return () => {
      componentMounted.current = false;
      setIsLoading(false);
    };
  }, []);

  if (isLoading) {
    return (
      <>
        <Loading />
      </>
    );
  }

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <div className="orderLegendBody">
          <Grid fullWidth={true}>
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
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Section>
                  <Section>
                    <Heading>
                      <FormattedMessage id="configuration.method.assign" />
                    </Heading>
                  </Section>
                </Section>
              </Section>
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <FormattedMessage id="configuration.method.assign.explain" />
              </Section>
            </Column>
          </Grid>
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Section>
                {methodTestList?.methodList?.length > 0 ? (
                  <Select
                    size="sm"
                    id="methodTestList"
                    labelText={
                      <span style={{ fontSize: "1.2rem", fontWeight: 500 }}>
                        {`${intl.formatMessage({ id: "referral.label.testmethod" })} : `}
                      </span>
                    }
                    value={methodId}
                    onChange={(e) => {
                      setMethodId(e.target.value);
                    }}
                  >
                    <SelectItem
                      disabled
                      hidden
                      value=""
                      text={intl.formatMessage({
                        id: "configuration.method.assign.select",
                      })}
                    />
                    {methodTestList?.methodList
                      ?.filter((method) => String(method.id) !== "0")
                      .map((method) => (
                        <SelectItem
                          key={method.id}
                          value={method.id}
                          text={method.value}
                        />
                      ))}
                  </Select>
                ) : null}
              </Section>
            </Column>
          </Grid>
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              {selectedMethodData?.selectedMethod && methodId ? (
                <CustomSharedList
                  leftTitle={`${selectedMethodData?.selectedMethod?.methodIdValuePair?.value} - ${intl.formatMessage({
                    id: "configuration.method.assign.assignedTests",
                  })}`}
                  rightTitle={intl.formatMessage({
                    id: "configuration.method.assign.availableTests",
                  })}
                  leftList={selectedMethodData?.selectedMethod?.tests}
                  rightList={selectedMethodData?.selectedMethod?.availableTests}
                  renderItem={(item) => item}
                  onChange={(newLeft, newRight) => {
                    setSelectedMethodData((prev) => ({
                      ...prev,
                      selectedMethod: {
                        ...prev.selectedMethod,
                        tests: newLeft,
                        availableTests: newRight,
                      },
                    }));
                  }}
                />
              ) : null}
            </Column>
          </Grid>
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Button
                  kind="primary"
                  onClick={() => {
                    handlePostMethodTestAssignCall();
                  }}
                >
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

export default injectIntl(MethodTestAssign);
