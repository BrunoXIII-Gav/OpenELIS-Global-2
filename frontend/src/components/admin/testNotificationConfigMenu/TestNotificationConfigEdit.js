import React, { useContext, useState, useEffect, useRef } from "react";
import {
  Heading,
  Button,
  Loading,
  Grid,
  Column,
  Section,
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableHeader,
  TableCell,
  TableSelectRow,
  TableSelectAll,
  TableContainer,
  Pagination,
  Search,
  Modal,
  TextInput,
  Dropdown,
  TextArea,
  Checkbox,
} from "@carbon/react";
import {
  getFromOpenElisServer,
  postToOpenElisServerFullResponse,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils.js";
import {
  ConfigurationContext,
  NotificationContext,
} from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import { useLocation } from "react-router-dom";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { ArrowLeft, ArrowRight, Cost } from "@carbon/icons-react";
import ActionPaginationButtonType from "../../common/ActionPaginationButtonType.js";

let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "testnotificationconfig.browse.title",
    link: "/MasterListsPage/testNotificationConfigMenu",
  },
];

function TestNotificationConfigEdit() {
  const { configurationProperties } = useContext(ConfigurationContext);
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const intl = useIntl();
  const location = useLocation();

  const ID = (() => {
    const search = location.search;
    if (search) {
      const urlParams = new URLSearchParams(search);
      return urlParams.get("testId") || "0";
    }
    return "0";
  })();

  const componentMounted = useRef(false);
  const latestPostDataRef = useRef({});
  const [indMsg, setIndMsg] = useState("0");
  const [loading, setLoading] = useState(true);
  const [saveButton, setSaveButton] = useState(false);
  const [sysDefaultMsg, setSysDefaultMsg] = useState(true);
  const [testNotificationConfigEditData, setTestNotificationConfigEditData] =
    useState({});
  const [
    testNotificationConfigEditDataPost,
    setTestNotificationConfigEditDataPost,
  ] = useState({});
  const [testNamesList, setTestNamesList] = useState([]);
  const [testName, setTestName] = useState("");
  const [profileUsersByCode, setProfileUsersByCode] = useState({});
  const [loadingProfileUsersByCode, setLoadingProfileUsersByCode] = useState(
    {},
  );

  const professionalProfileOptions = (
    configurationProperties?.professionalProfileOptions || ""
  )
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [code, ...labelParts] = entry.split("|");
      return {
        code: (code || "").trim(),
        label: (labelParts.join("|") || code || "").trim(),
      };
    });

  const internalProfilePhaseItems = [
    {
      id: "RESULT_PENDING_VALIDATION",
      value: intl.formatMessage({
        id: "testnotification.phase.pendingValidation",
      }),
    },
    {
      id: "RESULT_VALIDATION",
      value: intl.formatMessage({ id: "testnotification.phase.validated" }),
    },
  ];

  const createDefaultInternalProfileRule = () => ({
    notificationMethod: "EMAIL",
    notificationPersonType: "INTERNAL_PROFILE",
    notificationNature: "RESULT_PENDING_VALIDATION",
    active: true,
    professionalProfileCode: "",
    selectedUserIds: [],
    additionalContactsCsv: "",
    subjectTemplate: "[testName] Pending Validation",
    messageTemplate:
      "[testName] has been entered and is pending validation.\n\n[patientFirstName] [patientLastNameInitial]: [testResult]",
  });

  const getInternalProfileRules = (config) =>
    config?.internalProfileEmailNotifications || [];

  const updateInternalProfileRules = (updater) => {
    updatePostData((prev) => {
      const currentRules = getInternalProfileRules(prev?.config);
      const nextRules =
        typeof updater === "function" ? updater(currentRules) : updater;
      return {
        ...prev,
        config: {
          ...(prev.config || {}),
          internalProfileEmailNotifications: nextRules,
        },
      };
    });
  };

  useEffect(() => {
    if (testNotificationConfigEditData) {
      const nextValue = {
        ...latestPostDataRef.current,
        formName: testNotificationConfigEditData.formName,
        formMethod: testNotificationConfigEditData.formMethod,
        cancelAction: testNotificationConfigEditData.cancelAction,
        submitOnCancel: testNotificationConfigEditData.submitOnCancel,
        cancelMethod: testNotificationConfigEditData.cancelMethod,
        config: testNotificationConfigEditData.config,
        systemDefaultPayloadTemplate:
          testNotificationConfigEditData.systemDefaultPayloadTemplate,
        editSystemDefaultPayloadTemplate:
          testNotificationConfigEditData.editSystemDefaultPayloadTemplate,
      };
      latestPostDataRef.current = nextValue;
      setTestNotificationConfigEditDataPost(nextValue);
    }
  }, [testNotificationConfigEditData]);

  const updatePostData = (updater) => {
    const nextValue = updater(latestPostDataRef.current || {});
    latestPostDataRef.current = nextValue;
    setTestNotificationConfigEditDataPost(nextValue);
  };

  const handleMenuItems = (res) => {
    if (res) {
      setTestNotificationConfigEditData(res);
    }
    setLoading(false);
  };

  const handleTestNamesList = (res) => {
    if (res) {
      setTestNamesList(res);
    }
    setLoading(false);
  };

  useEffect(() => {
    componentMounted.current = true;
    if (ID && ID !== "0") {
      getFromOpenElisServer(
        `/rest/TestNotificationConfig?testId=${ID}`,
        handleMenuItems,
      );
    }
    getFromOpenElisServer(`/rest/test-list`, handleTestNamesList);
    return () => {
      componentMounted.current = false;
    };
  }, [ID, location.search]);

  useEffect(() => {
    const testId = testNotificationConfigEditData?.config?.testId;
    if (testNamesList && testId) {
      const test = testNamesList.find((item) => item.id === testId);
      if (test) {
        setTestName(test.value);
      }
    }
  }, [testNamesList, testNotificationConfigEditData]);

  function handleSubjectTemplateChange(e) {
    updatePostData((prev) => ({
      ...prev,
      editSystemDefaultPayloadTemplate: true,
    }));
    updatePostData((prev) => ({
      ...prev,
      systemDefaultPayloadTemplate: {
        ...prev.systemDefaultPayloadTemplate,
        subjectTemplate: e.target.value,
      },
    }));
  }

  function handleMessageTemplateChange(e) {
    updatePostData((prev) => ({
      ...prev,
      editSystemDefaultPayloadTemplate: true,
    }));
    updatePostData((prev) => ({
      ...prev,
      systemDefaultPayloadTemplate: {
        ...prev.systemDefaultPayloadTemplate,
        messageTemplate: e.target.value,
      },
    }));
  }

  const handleCheckboxChange = (e) => {
    const { id, checked } = e.target;

    updatePostData((prev) => {
      const updatedConfig = { ...prev.config };

      switch (id) {
        case "providerEmail":
          updatedConfig.providerEmail.active = checked;
          break;
        case "patientEmail":
          updatedConfig.patientEmail.active = checked;
          break;
        case "patientSMS":
          updatedConfig.patientSMS.active = checked;
          break;
        case "providerSMS":
          updatedConfig.providerSMS.active = checked;
          break;
        case "internalProfileEmail":
          updatedConfig.internalProfileEmailNotifications = checked
            ? getInternalProfileRules(updatedConfig).length > 0
              ? getInternalProfileRules(updatedConfig)
              : [createDefaultInternalProfileRule()]
            : [];
          break;
        default:
          break;
      }

      return {
        ...prev,
        config: updatedConfig,
      };
    });
  };

  const handleInternalProfileFieldChange = (index, field, value) => {
    updateInternalProfileRules((prevRules) =>
      prevRules.map((rule, ruleIndex) =>
        ruleIndex === index ? { ...rule, [field]: value } : rule,
      ),
    );
  };

  const handleAddInternalProfileRule = () => {
    updateInternalProfileRules((prevRules) => [
      ...prevRules,
      createDefaultInternalProfileRule(),
    ]);
  };

  const handleRemoveInternalProfileRule = (index) => {
    updateInternalProfileRules((prevRules) =>
      prevRules.filter((_, ruleIndex) => ruleIndex !== index),
    );
  };

  const fetchUsersForProfessionalProfile = (profileCode) => {
    const normalizedCode = (profileCode || "").trim();
    if (!normalizedCode || profileUsersByCode[normalizedCode]) {
      return;
    }

    setLoadingProfileUsersByCode((prev) => ({
      ...prev,
      [normalizedCode]: true,
    }));

    getFromOpenElisServer(
      `/rest/users/professional-profile/${encodeURIComponent(
        normalizedCode,
      )}?activeOnly=true&labelMode=fullName`,
      (res) => {
        setProfileUsersByCode((prev) => ({
          ...prev,
          [normalizedCode]: Array.isArray(res) ? res : [],
        }));
        setLoadingProfileUsersByCode((prev) => ({
          ...prev,
          [normalizedCode]: false,
        }));
      },
    );
  };

  const handleInternalProfileProfessionalProfileChange = (index, profileCode) => {
    handleInternalProfileFieldChange(index, "professionalProfileCode", profileCode);
    handleInternalProfileFieldChange(index, "selectedUserIds", []);
    if (profileCode) {
      fetchUsersForProfessionalProfile(profileCode);
    }
  };

  const handleInternalProfileSelectedUsersChange = (
    index,
    selectedUserId,
    checked,
  ) => {
    updateInternalProfileRules((prevRules) =>
      prevRules.map((rule, ruleIndex) => {
        if (ruleIndex !== index) {
          return rule;
        }
        const currentSelectedUserIds = Array.isArray(rule.selectedUserIds)
          ? rule.selectedUserIds
          : [];
        const nextSelectedUserIds = checked
          ? [...new Set([...currentSelectedUserIds, selectedUserId])]
          : currentSelectedUserIds.filter((userId) => userId !== selectedUserId);
        return { ...rule, selectedUserIds: nextSelectedUserIds };
      }),
    );
  };

  useEffect(() => {
    getInternalProfileRules(testNotificationConfigEditDataPost?.config).forEach(
      (rule) => {
        if (rule?.professionalProfileCode) {
          fetchUsersForProfessionalProfile(rule.professionalProfileCode);
        }
      },
    );
  }, [testNotificationConfigEditDataPost?.config]);

  useEffect(() => {
    const rules = getInternalProfileRules(testNotificationConfigEditDataPost?.config);
    if (!rules.length) {
      return;
    }

    let hasChanges = false;
    const nextRules = rules.map((rule) => {
      const professionalProfileCode = (rule.professionalProfileCode || "").trim();
      if (!professionalProfileCode || !Array.isArray(rule.selectedUserIds)) {
        return rule;
      }

      const availableUsers = profileUsersByCode[professionalProfileCode];
      if (!availableUsers) {
        return rule;
      }

      const allowedIds = new Set(availableUsers.map((user) => user.id));
      const filteredSelectedUserIds = rule.selectedUserIds.filter((userId) =>
        allowedIds.has(userId),
      );

      if (filteredSelectedUserIds.length === rule.selectedUserIds.length) {
        return rule;
      }

      hasChanges = true;
      return { ...rule, selectedUserIds: filteredSelectedUserIds };
    });

    if (hasChanges) {
      updateInternalProfileRules(nextRules);
    }
  }, [profileUsersByCode]);

  const buildSavePayload = () => {
    const current =
      latestPostDataRef.current || testNotificationConfigEditDataPost || {};
    const currentConfig = current.config || {};
    const { options, internalProfileEmailNotifications, ...restConfig } =
      currentConfig;

    return {
      ...current,
      config: {
        ...restConfig,
        patientEmail: currentConfig.patientEmail
          ? { ...currentConfig.patientEmail }
          : undefined,
        patientSMS: currentConfig.patientSMS
          ? { ...currentConfig.patientSMS }
          : undefined,
        providerEmail: currentConfig.providerEmail
          ? { ...currentConfig.providerEmail }
          : undefined,
        providerSMS: currentConfig.providerSMS
          ? { ...currentConfig.providerSMS }
          : undefined,
        internalProfileEmailNotifications: getInternalProfileRules(
          currentConfig,
        ).map((rule) => ({
          id: rule.id,
          notificationMethod: rule.notificationMethod,
          notificationPersonType: rule.notificationPersonType,
          notificationNature: rule.notificationNature,
          active: rule.active,
          professionalProfileCode: rule.professionalProfileCode || "",
          selectedUserIds: Array.isArray(rule.selectedUserIds)
            ? rule.selectedUserIds
            : [],
          additionalContactsCsv: rule.additionalContactsCsv || "",
          subjectTemplate: rule.subjectTemplate || "",
          messageTemplate: rule.messageTemplate || "",
        })),
      },
    };
  };

  function testNotificationConfigEditSavePostCall() {
    setLoading(true);
    const payload = buildSavePayload();
    postToOpenElisServerJsonResponse(
      `/rest/TestNotificationConfig`,
      JSON.stringify(payload),
      (res) => {
        testNotificationConfigEditSavePostCallBack(res);
      },
    );
  }

  function testNotificationConfigEditSavePostCallBack(res) {
    if (res) {
      addNotification({
        title: intl.formatMessage({
          id: "notification.title",
        }),
        message: intl.formatMessage({
          id: "notification.user.post.save.success",
        }),
        kind: NotificationKinds.success,
      });
      setNotificationVisible(true);
    } else {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "server.error.msg" }),
      });
      setNotificationVisible(true);
    }
    setLoading(false);
  }

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}
      {loading && <Loading></Loading>}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <Grid fullWidth={true}>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <Section>
                <Heading>
                  <FormattedMessage id="testnotificationconfig.browse.title" />
                </Heading>
              </Section>
            </Section>
          </Column>
        </Grid>
        <div className="orderLegendBody">
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Section>
                  <Heading>
                    {testName && <FormattedMessage id={`${testName}`} />}
                  </Heading>
                </Section>
              </Section>
            </Column>
          </Grid>
          <hr />
          <br />
          {testNotificationConfigEditDataPost?.config && (
            <Grid fullWidth={true}>
              <Column lg={4} md={4} sm={2}>
                <Checkbox
                  id="patientEmail"
                  labelText={
                    <FormattedMessage id="testnotification.patient.email" />
                  }
                  checked={
                    testNotificationConfigEditDataPost.config.patientEmail
                      ?.active ?? false
                  }
                  onChange={handleCheckboxChange}
                />
              </Column>
              <Column lg={4} md={4} sm={2}>
                <Checkbox
                  id="patientSMS"
                  labelText={
                    <FormattedMessage id="testnotification.patient.sms" />
                  }
                  checked={
                    testNotificationConfigEditDataPost.config.patientSMS
                      ?.active ?? false
                  }
                  onChange={handleCheckboxChange}
                />
              </Column>
              <Column lg={4} md={4} sm={2}>
                <Checkbox
                  id="providerSMS"
                  labelText={
                    <FormattedMessage id="testnotification.provider.sms" />
                  }
                  checked={
                    testNotificationConfigEditDataPost.config.providerSMS
                      ?.active ?? false
                  }
                  onChange={handleCheckboxChange}
                />
              </Column>
              <Column lg={4} md={4} sm={2}>
                <Checkbox
                  // key={section.elementID}
                  // id={section.elementID}
                  // value={section.roleId}
                  // labelText={section.roleName}
                  // checked={selectedGlobalLabUnitRoles.includes(section.roleId)}
                  id="providerEmail"
                  labelText={
                    <FormattedMessage id="testnotification.provider.email" />
                  }
                  checked={
                    testNotificationConfigEditDataPost.config.providerEmail
                      ?.active ?? false
                  }
                  onChange={handleCheckboxChange}
                />
              </Column>
              <Column lg={4} md={4} sm={2}>
                <Checkbox
                  id="internalProfileEmail"
                  labelText={
                    <FormattedMessage id="testnotification.internalProfile.email" />
                  }
                  checked={
                    getInternalProfileRules(
                      testNotificationConfigEditDataPost?.config,
                    ).length > 0
                  }
                  onChange={handleCheckboxChange}
                />
              </Column>
            </Grid>
          )}
          <br />
          <hr />
          <br />
          {testNotificationConfigEditDataPost?.config &&
            getInternalProfileRules(testNotificationConfigEditDataPost?.config)
              .length > 0 && (
              <>
                <Grid fullWidth={true} condensed={true}>
                  <Column lg={12} md={6} sm={4}>
                    <Section>
                      <Heading>
                        <FormattedMessage id="testnotification.internalProfile.section" />
                      </Heading>
                    </Section>
                  </Column>
                  <Column lg={4} md={2} sm={4}>
                    <Button
                      kind="secondary"
                      size="sm"
                      onClick={handleAddInternalProfileRule}
                    >
                      {intl.formatMessage({
                        id: "testnotification.internalProfile.rule.add",
                      })}
                    </Button>
                  </Column>
                </Grid>
                <br />
                {getInternalProfileRules(
                  testNotificationConfigEditDataPost?.config,
                ).map((rule, index) => (
                  <React.Fragment key={`${rule.id || "new"}-${index}`}>
                    <Grid fullWidth={true} condensed={true}>
                      <Column lg={12} md={6} sm={4}>
                        <Heading>
                          {intl.formatMessage(
                            {
                              id: "testnotification.internalProfile.rule.title",
                            },
                            { index: index + 1 },
                          )}
                        </Heading>
                      </Column>
                      <Column lg={4} md={2} sm={4}>
                        <Button
                          kind="ghost"
                          size="sm"
                          onClick={() => handleRemoveInternalProfileRule(index)}
                        >
                          {intl.formatMessage({
                            id: "testnotification.internalProfile.rule.remove",
                          })}
                        </Button>
                      </Column>
                    </Grid>
                    <br />
                    <Grid fullWidth={true}>
                      <Column lg={4} md={4} sm={4}>
                        <Dropdown
                          id={`internal-profile-phase-${index}`}
                          titleText={intl.formatMessage({
                            id: "testnotification.phase.label",
                          })}
                          label={intl.formatMessage({
                            id: "testnotification.phase.select",
                          })}
                          items={internalProfilePhaseItems}
                          itemToString={(item) => item?.value || ""}
                          selectedItem={internalProfilePhaseItems.find(
                            (item) => item.id === rule.notificationNature,
                          )}
                          onChange={({ selectedItem }) =>
                            handleInternalProfileFieldChange(
                              index,
                              "notificationNature",
                              selectedItem?.id || "RESULT_PENDING_VALIDATION",
                            )
                          }
                        />
                      </Column>
                      <Column lg={4} md={4} sm={4}>
                        <Dropdown
                          id={`internal-profile-code-${index}`}
                          titleText={intl.formatMessage({
                            id: "unifiedSystemUser.professional.profile.label",
                          })}
                          label={intl.formatMessage({
                            id: "unifiedSystemUser.professional.profile.select",
                          })}
                          items={professionalProfileOptions}
                          itemToString={(item) => item?.label || ""}
                          selectedItem={professionalProfileOptions.find(
                            (item) =>
                              item.code === rule.professionalProfileCode,
                          )}
                          onChange={({ selectedItem }) =>
                            handleInternalProfileProfessionalProfileChange(
                              index,
                              selectedItem?.code || "",
                            )
                          }
                        />
                      </Column>
                      <Column lg={8} md={8} sm={4}>
                        <TextInput
                          id={`internal-profile-bcc-${index}`}
                          labelText={intl.formatMessage({
                            id: "testnotification.bcc",
                          })}
                          value={rule.additionalContactsCsv || ""}
                          onChange={(e) =>
                            handleInternalProfileFieldChange(
                              index,
                              "additionalContactsCsv",
                              e.target.value,
                            )
                          }
                        />
                      </Column>
                    </Grid>
                    <br />
                    {(rule.professionalProfileCode || "").trim() !== "" && (
                      <>
                        <Grid fullWidth={true}>
                          <Column lg={16} md={8} sm={4}>
                            <div
                              style={{
                                border: "1px solid #c6c6c6",
                                padding: "1rem",
                                backgroundColor: "#f4f4f4",
                              }}
                            >
                              <div style={{ marginBottom: "0.75rem", fontWeight: 600 }}>
                                {intl.formatMessage({
                                  id: "testnotification.internalProfile.users.label",
                                })}
                              </div>
                              {loadingProfileUsersByCode[
                                rule.professionalProfileCode
                              ] ? (
                                <div>
                                  <FormattedMessage id="testnotification.internalProfile.users.loading" />
                                </div>
                              ) : (profileUsersByCode[rule.professionalProfileCode] || [])
                                  .length > 0 ? (
                                <Grid condensed fullWidth={true}>
                                  {(profileUsersByCode[
                                    rule.professionalProfileCode
                                  ] || []).map((userOption) => (
                                    <Column
                                      key={`internal-profile-user-${index}-${userOption.id}`}
                                      lg={4}
                                      md={4}
                                      sm={4}
                                    >
                                      <Checkbox
                                        id={`internal-profile-user-${index}-${userOption.id}`}
                                        labelText={userOption.value}
                                        checked={(rule.selectedUserIds || []).includes(
                                          userOption.id,
                                        )}
                                        onChange={(_, { checked }) =>
                                          handleInternalProfileSelectedUsersChange(
                                            index,
                                            userOption.id,
                                            checked,
                                          )
                                        }
                                      />
                                    </Column>
                                  ))}
                                </Grid>
                              ) : (
                                <div>
                                  <FormattedMessage id="testnotification.internalProfile.users.empty" />
                                </div>
                              )}
                            </div>
                          </Column>
                        </Grid>
                        <br />
                      </>
                    )}
                    <Grid fullWidth={true}>
                      <Column lg={8} md={4} sm={4}>
                        <TextInput
                          id={`internal-profile-subject-${index}`}
                          labelText={intl.formatMessage({
                            id: "testnotification.subjecttemplate",
                          })}
                          value={rule.subjectTemplate || ""}
                          onChange={(e) =>
                            handleInternalProfileFieldChange(
                              index,
                              "subjectTemplate",
                              e.target.value,
                            )
                          }
                        />
                      </Column>
                    </Grid>
                    <br />
                    <Grid fullWidth={true}>
                      <Column lg={16} md={8} sm={4}>
                        <TextArea
                          id={`internal-profile-message-${index}`}
                          labelText={intl.formatMessage({
                            id: "testnotification.messagetemplate",
                          })}
                          value={rule.messageTemplate || ""}
                          onChange={(e) =>
                            handleInternalProfileFieldChange(
                              index,
                              "messageTemplate",
                              e.target.value,
                            )
                          }
                        />
                      </Column>
                    </Grid>
                    <br />
                    <hr />
                    <br />
                  </React.Fragment>
                ))}
                <hr />
                <br />
              </>
            )}
          <Grid fullWidth={true} className="gridBoundary">
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Section>
                  <Heading>
                    <FormattedMessage id="testnotification.instructions.header" />
                  </Heading>
                </Section>
              </Section>
              <br />
              <FormattedMessage id="testnotification.instructions.body" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructions.body.0" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructions.body.1" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructions.body.2" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructions.body.3" />
              <br />
              <br />
              <Section>
                <Section>
                  <Heading>
                    <FormattedMessage id="testnotification.instructionis.variables.header" />
                  </Heading>
                </Section>
              </Section>
              <br />
              <FormattedMessage id="testnotification.instructionis.variables.body" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructionis.variables.body.0" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructionis.variables.body.1" />
              <br />
              <br />
              <FormattedMessage id="testnotification.instructionis.variables.body.2" />
              <br />
              <br />
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <div>
            <Grid fullWidth={true}>
              <Column lg={14} md={8} sm={4}>
                <Section>
                  <Section>
                    <Section>
                      <Heading>
                        <FormattedMessage id="testnotification.systemdefault.template" />
                      </Heading>
                    </Section>
                  </Section>
                </Section>
              </Column>
              <Column lg={2} md={8} sm={4}>
                <Button
                  onClick={() => {
                    setSysDefaultMsg(!sysDefaultMsg);
                  }}
                >
                  Edit
                </Button>
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={8} md={4} sm={2}>
                <FormattedMessage id="testnotification.subjecttemplate" />
              </Column>
              <Column lg={8} md={4} sm={2}>
                <TextInput
                  id="subject"
                  type="text"
                  labelText=""
                  hideLabel={true}
                  disabled={sysDefaultMsg}
                  placeholder={intl.formatMessage({
                    id: "systemDefaultPayloadTemplate.subjectTemplate",
                  })}
                  // invalid={
                  //   userDataShow &&
                  //   userDataShow.userLoginName &&
                  //   !loginNameRegex.test(userDataShow.userLoginName)
                  // }
                  // // invalidText={errors.order}
                  // required={true}
                  value={
                    testNotificationConfigEditDataPost &&
                    testNotificationConfigEditDataPost.systemDefaultPayloadTemplate &&
                    testNotificationConfigEditDataPost
                      .systemDefaultPayloadTemplate.subjectTemplate
                      ? testNotificationConfigEditDataPost
                          .systemDefaultPayloadTemplate.subjectTemplate
                      : ""
                  }
                  onChange={(e) => handleSubjectTemplateChange(e)}
                />
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <FormattedMessage id="testnotification.messagetemplate" />
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <TextArea
                  id="message"
                  type="text"
                  labelText=""
                  hideLabel={true}
                  disabled={sysDefaultMsg}
                  placeholder={intl.formatMessage({
                    id: "systemDefaultPayloadTemplate.messageTemplate",
                  })}
                  // invalid={
                  //   userDataShow &&
                  //   userDataShow.userLoginName &&
                  //   !loginNameRegex.test(userDataShow.userLoginName)
                  // }
                  // // invalidText={errors.order}
                  // required={true}
                  value={
                    testNotificationConfigEditDataPost &&
                    testNotificationConfigEditDataPost.systemDefaultPayloadTemplate &&
                    testNotificationConfigEditDataPost
                      .systemDefaultPayloadTemplate.messageTemplate
                      ? testNotificationConfigEditDataPost
                          .systemDefaultPayloadTemplate.messageTemplate
                      : ""
                  }
                  onChange={(e) => handleMessageTemplateChange(e)}
                />
              </Column>
            </Grid>
          </div>
          <br />
          <hr />
          <br />
          <div>
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <Section>
                  <Section>
                    <Section>
                      <Heading>
                        <FormattedMessage id="testnotification.testdefault.template" />
                      </Heading>
                    </Section>
                  </Section>
                </Section>
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={8} md={4} sm={2}>
                <FormattedMessage id="testnotification.subjecttemplate" />
              </Column>
              <Column lg={8} md={4} sm={2}>
                <TextInput
                  id="subject"
                  type="text"
                  labelText=""
                  // invalid={
                  //   userDataShow &&
                  //   userDataShow.userLoginName &&
                  //   !loginNameRegex.test(userDataShow.userLoginName)
                  // }
                  // // invalidText={errors.order}
                  // required={true}
                  // value={
                  //   userDataShow && userDataShow.userLoginName
                  //     ? userDataShow.userLoginName
                  //     : ""
                  // }
                  // onChange={(e) => handleUserLoginNameChange(e)}
                />
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <FormattedMessage id="testnotification.messagetemplate" />
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <TextArea
                  id="message"
                  type="text"
                  labelText=""
                  // invalid={
                  //   userDataShow &&
                  //   userDataShow.userLoginName &&
                  //   !loginNameRegex.test(userDataShow.userLoginName)
                  // }
                  // // invalidText={errors.order}
                  // required={true}
                  // value={
                  //   userDataShow && userDataShow.userLoginName
                  //     ? userDataShow.userLoginName
                  //     : ""
                  // }
                  // onChange={(e) => handleUserLoginNameChange(e)}
                />
              </Column>
            </Grid>
          </div>
          <br />
          <hr />
          <br />
          <div>
            <Grid fullWidth={true}>
              <Column lg={14} md={8} sm={4}>
                <Section>
                  <Section>
                    <Section>
                      <Heading>
                        <FormattedMessage id="testnotification.options" />
                      </Heading>
                    </Section>
                  </Section>
                </Section>
              </Column>
            </Grid>
            <br />
            <Grid fullWidth={true}>
              <Column lg={4} md={8} sm={4}>
                <Button
                  onClick={() => {
                    setIndMsg("0");
                  }}
                  kind="tertiary"
                >
                  <FormattedMessage id="testnotification.provider.email" />
                </Button>
              </Column>{" "}
              <Column lg={4} md={8} sm={4}>
                <Button
                  onClick={() => {
                    setIndMsg("1");
                  }}
                  kind="tertiary"
                >
                  <FormattedMessage id="testnotification.provider.sms" />
                </Button>
              </Column>{" "}
              <Column lg={4} md={8} sm={4}>
                <Button
                  onClick={() => {
                    setIndMsg("2");
                  }}
                  kind="tertiary"
                >
                  <FormattedMessage id="testnotification.patient.email" />
                </Button>
              </Column>{" "}
              <Column lg={4} md={8} sm={4}>
                <Button
                  onClick={() => {
                    setIndMsg("3");
                  }}
                  kind="tertiary"
                >
                  <FormattedMessage id="testnotification.patient.sms" />
                </Button>
              </Column>
            </Grid>
            <br />
            <hr />
            <br />
            {indMsg === "0" || indMsg === "2" ? (
              <>
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <Section>
                      <Section>
                        <Section>
                          <Heading>
                            {indMsg === "0" ? (
                              <>
                                <FormattedMessage id="testnotification.provider.email" />
                              </>
                            ) : (
                              <>
                                <FormattedMessage id="testnotification.patient.email" />
                              </>
                            )}
                          </Heading>
                        </Section>
                      </Section>
                    </Section>
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={2}>
                    <FormattedMessage id="testnotification.bcc" />
                  </Column>
                  <Column lg={8} md={4} sm={2}>
                    <TextInput
                      id="subject"
                      type="text"
                      labelText=""
                      // invalid={
                      //   userDataShow &&
                      //   userDataShow.userLoginName &&
                      //   !loginNameRegex.test(userDataShow.userLoginName)
                      // }
                      // // invalidText={errors.order}
                      // required={true}
                      // value={
                      //   userDataShow && userDataShow.userLoginName
                      //     ? userDataShow.userLoginName
                      //     : ""
                      // }
                      // onChange={(e) => handleUserLoginNameChange(e)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={2}>
                    <FormattedMessage id="testnotification.subjecttemplate" />
                  </Column>
                  <Column lg={8} md={4} sm={2}>
                    <TextInput
                      id="subject"
                      type="text"
                      labelText=""
                      // invalid={
                      //   userDataShow &&
                      //   userDataShow.userLoginName &&
                      //   !loginNameRegex.test(userDataShow.userLoginName)
                      // }
                      // // invalidText={errors.order}
                      // required={true}
                      // value={
                      //   userDataShow && userDataShow.userLoginName
                      //     ? userDataShow.userLoginName
                      //     : ""
                      // }
                      // onChange={(e) => handleUserLoginNameChange(e)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <FormattedMessage id="testnotification.messagetemplate" />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <TextArea
                      id="message"
                      type="text"
                      labelText=""
                      // invalid={
                      //   userDataShow &&
                      //   userDataShow.userLoginName &&
                      //   !loginNameRegex.test(userDataShow.userLoginName)
                      // }
                      // // invalidText={errors.order}
                      // required={true}
                      // value={
                      //   userDataShow && userDataShow.userLoginName
                      //     ? userDataShow.userLoginName
                      //     : ""
                      // }
                      // onChange={(e) => handleUserLoginNameChange(e)}
                    />
                  </Column>
                </Grid>
              </>
            ) : (
              <>
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <Section>
                      <Section>
                        <Section>
                          <Heading>
                            {indMsg === "1" ? (
                              <>
                                <FormattedMessage id="testnotification.provider.sms" />
                              </>
                            ) : (
                              <>
                                <FormattedMessage id="testnotification.patient.sms" />
                              </>
                            )}
                          </Heading>
                        </Section>
                      </Section>
                    </Section>
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <FormattedMessage id="testnotification.messagetemplate" />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <TextArea
                      id="message"
                      type="text"
                      labelText=""
                      // invalid={
                      //   userDataShow &&
                      //   userDataShow.userLoginName &&
                      //   !loginNameRegex.test(userDataShow.userLoginName)
                      // }
                      // // invalidText={errors.order}
                      // required={true}
                      // value={
                      //   userDataShow && userDataShow.userLoginName
                      //     ? userDataShow.userLoginName
                      //     : ""
                      // }
                      // onChange={(e) => handleUserLoginNameChange(e)}
                    />
                  </Column>
                </Grid>
              </>
            )}
            <br />
            <hr />
            <br />
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <Button
                  disabled={saveButton}
                  onClick={testNotificationConfigEditSavePostCall}
                  type="button"
                >
                  <FormattedMessage id="label.button.save" />
                </Button>{" "}
                <Button
                  onClick={() =>
                    window.location.assign(
                      "/MasterListsPage/testNotificationConfigMenu",
                    )
                  }
                  kind="tertiary"
                  type="button"
                >
                  <FormattedMessage id="label.button.exit" />
                </Button>
              </Column>
            </Grid>
          </div>
        </div>
      </div>
    </>
  );
}

export default injectIntl(TestNotificationConfigEdit);
