import React, { useContext, useState, useEffect, useRef } from "react";
import {
  Grid,
  Column,
  Section,
  Heading,
  Form,
  TextInput,
  UnorderedList,
  ListItem,
  RadioButton,
  Button,
  Loading,
  Select,
  SelectItem,
  PasswordInput,
  Checkbox,
  FormGroup,
} from "@carbon/react";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import { useLocation } from "react-router-dom";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import {
  ConfigurationContext,
  NotificationContext,
} from "../../layout/Layout.js";
import {
  getFromOpenElisServer,
  getFromOpenElisServerV2,
  postToOpenElisServerJsonResponse,
  toBase64,
} from "../../utils/Utils.js";
import CustomDatePicker from "../../common/CustomDatePicker.js";
import AutoComplete from "../../common/AutoComplete.js";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "unifiedSystemUser.browser.title",
    link: "/MasterListsPage/userManagement",
  },
];

const passwordPatternRegex = /^(?=.*[*$#!])(?=.*[a-zA-Z0-9]).{7,}$/;
const loginNameRegex = /^[a-zA-Z0-9_-]+(?: [a-zA-Z0-9_-]+)*$/;
const nameRegex = /^(?=.*[a-zA-Z])[a-zA-Z .'_@-]*$/;
const ALL_PERMISSIONS_ROLE_NAMES = new Set([
  "reception",
  "results",
  "reports",
  "validation",
  "aliquot",
  "storage",
]);

const normalizeRoleName = (roleName = "") =>
  String(roleName).trim().toLowerCase();

const normalizeRoleIds = (roleIds = []) =>
  roleIds.map((roleId) => String(roleId));

const getAllPermissionRoleIds = (labUnitRoles = []) =>
  labUnitRoles
    .filter(({ roleName }) =>
      ALL_PERMISSIONS_ROLE_NAMES.has(normalizeRoleName(roleName)),
    )
    .map(({ roleId }) => String(roleId));

const isAllPermissionsSelected = (
  selectedRolesByLabUnit,
  labUnitKey,
  labUnitRoles = [],
) => {
  const requiredRoleIds = getAllPermissionRoleIds(labUnitRoles);
  if (!requiredRoleIds.length) {
    return false;
  }

  const selectedRoleIds = normalizeRoleIds(
    selectedRolesByLabUnit[labUnitKey] || [],
  );
  return requiredRoleIds.every((roleId) => selectedRoleIds.includes(roleId));
};

const toggleAllPermissionsForLabUnit = (
  selectedRolesByLabUnit,
  labUnitKey,
  labUnitRoles = [],
) => {
  const requiredRoleIds = getAllPermissionRoleIds(labUnitRoles);
  const updatedRoles = normalizeRoleIds(
    selectedRolesByLabUnit[labUnitKey] || [],
  );

  if (!requiredRoleIds.length) {
    return updatedRoles;
  }

  const hasAll = requiredRoleIds.every((roleId) =>
    updatedRoles.includes(roleId),
  );
  if (hasAll) {
    return updatedRoles.filter((roleId) => !requiredRoleIds.includes(roleId));
  }

  requiredRoleIds.forEach((roleId) => {
    if (!updatedRoles.includes(roleId)) {
      updatedRoles.push(roleId);
    }
  });

  return updatedRoles;
};

const parseProfessionalProfileOptions = (rawOptions = "") =>
  String(rawOptions || "")
    .split(",")
    .map((token) => token.trim())
    .filter(Boolean)
    .map((token) => {
      const [codeRaw, labelRaw] = token.split("|");
      const code = String(codeRaw || "").trim();
      const label = String(labelRaw || codeRaw || "").trim();
      if (!code) {
        return null;
      }
      return { code, label: label || code };
    })
    .filter(Boolean);

function UserAddModify() {
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const { configurationProperties } = useContext(ConfigurationContext);

  const componentMounted = useRef(false);
  const intl = useIntl();

  const [, setSaveButton] = useState(true);
  const [validation, setValidation] = useState({
    validatepassword: false,
    password: false,
    password2: false,
    loginName: false,
    firstName: false,
    secondName: false,
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isLocked, setIsLocked] = useState("radio-2");
  const [isDisabled, setIsDisabled] = useState("radio-4");
  const [isActive, setIsActive] = useState("radio-6");
  const [copyUserPermission, setCopyUserPermission] = useState("0");
  const [copyUserPermissionList, setCopyUserPermissionList] = useState(null);
  const [userData, setUserData] = useState(null);
  const [userDataShow, setUserDataShow] = useState({});
  const [userDataPost, setUserDataPost] = useState(null);
  const [linkedProviderSuggestions, setLinkedProviderSuggestions] = useState(
    [],
  );
  const [selectedGlobalLabUnitRoles, setSelectedGlobalLabUnitRoles] = useState(
    [],
  );
  const [selectedCustomRoles, setSelectedCustomRoles] = useState([]);
  const [selectedTestSectionLabUnits, setSelectedTestSectionLabUnits] =
    useState({});
  const [selectedTestSectionList, setSelectedTestSectionList] = useState([]);
  const [showLegacyPermissions, setShowLegacyPermissions] = useState(false);
  const [passwordTouched, setPasswordTouched] = useState({
    userPassword: false,
    confirmPassword: false,
  });
  const [loginNameValidation, setLoginNameValidation] = useState({
    isChecking: false,
    isDuplicate: false,
    lastCheckedValue: "",
  });

  const location = useLocation();
  const ID = (() => {
    const search = location.search;
    if (search) {
      const urlParams = new URLSearchParams(search);
      return urlParams.get("ID");
    }
    return "0";
  })();

  const professionalProfileOptions = parseProfessionalProfileOptions(
    configurationProperties?.professionalProfileOptions,
  );
  const selectedProfessionalProfileCode = String(
    userDataShow?.professionalProfileCode || "",
  ).trim();
  const linkedProviderPersonId = String(
    userDataShow?.linkedProviderPersonId || "",
  ).trim();
  const isLinkedProfessionalSelected = Boolean(linkedProviderPersonId);

  useEffect(() => {
    if (!selectedProfessionalProfileCode) {
      setLinkedProviderSuggestions([]);
      return;
    }

    getFromOpenElisServer(
      `/rest/providers/professional-profile/${encodeURIComponent(selectedProfessionalProfileCode)}`,
      (providers) => {
        const normalizedProviders = Array.isArray(providers) ? providers : [];
        setLinkedProviderSuggestions(normalizedProviders);

        const currentLinkedProviderId = String(
          userDataShow?.linkedProviderPersonId || "",
        ).trim();
        if (
          currentLinkedProviderId &&
          !normalizedProviders.some(
            (provider) =>
              String(provider?.id || "").trim() === currentLinkedProviderId,
          )
        ) {
          setUserDataPost((prevUserDataPost) => ({
            ...prevUserDataPost,
            linkedProviderPersonId: "",
          }));
          setUserDataShow((prevUserDataShow) => ({
            ...prevUserDataShow,
            linkedProviderPersonId: "",
          }));
        }
      },
    );
  }, [
    selectedProfessionalProfileCode,
    userDataShow?.linkedProviderPersonId,
    setUserDataPost,
    setUserDataShow,
  ]);

  useEffect(() => {
    componentMounted.current = true;
    setIsLoading(true);
    if (ID) {
      getFromOpenElisServer(
        `/rest/UnifiedSystemUser?ID=${ID}&startingRecNo=1&roleFilter=`,
        handleUserData,
      );
    } else {
      setTimeout(() => {
        window.location.assign("/MasterListsPage/userManagement");
      }, 200);
    }
    return () => {
      componentMounted.current = false;
      setIsLoading(false);
    };
  }, [ID]);

  const handleUserData = (res) => {
    if (!res) {
      setIsLoading(true);
    } else {
      setUserData(res);
      if (res.loginUserId) {
        setValidation({
          validatepassword: true,
          password: true,
          password2: true,
          loginName: true,
          firstName: true,
          secondName: true,
        });
      }
      var KeyList = [];
      Object.keys(res.selectedTestSectionLabUnits).map((key) =>
        KeyList.push(key),
      );
      setSelectedTestSectionList(KeyList);
    }
  };

  useEffect(() => {
    componentMounted.current = true;
    setIsLoading(true);
    getFromOpenElisServer(`/rest/users`, handleCopyUserPermissionsList);
    return () => {
      componentMounted.current = false;
      setIsLoading(false);
    };
  }, []);

  const handleCopyUserPermissionsList = (res) => {
    if (!res) {
      setIsLoading(true);
    } else {
      setCopyUserPermissionList(res);
    }
  };

  useEffect(() => {
    if (userData) {
      const userManagementInfoToShow = {
        accountActive: userData.accountActive,
        accountDisabled: userData.accountDisabled,
        accountLocked: userData.accountLocked,
        allowCopyUserRoles: userData.allowCopyUserRoles,
        cancelAction: userData.cancelAction,
        cancelMethod: userData.cancelMethod,
        confirmPassword: userData.confirmPassword,
        expirationDate: userData.expirationDate,
        formAction: userData.formAction,
        formMethod: userData.formMethod,
        formName: userData.formName,
        customRoles: userData.customRoles,
        loginUserId: userData.loginUserId,
        selectedRoles: userData.selectedRoles || [],
        selectedCustomRoleIds: userData.selectedCustomRoleIds || [],
        selectedTestSectionLabUnits: userData.selectedTestSectionLabUnits || {},
        systemUserId: userData.systemUserId,
        systemUserIdToCopy: userData.systemUserIdToCopy,
        systemUserLastupdated: userData.systemUserLastupdated,
        timeout: userData.timeout,
        userFirstName: userData.userFirstName,
        userLastName: userData.userLastName,
        userLoginName: userData.userLoginName,
        userPassword: userData.userPassword,
        linkedProviderPersonId: userData.linkedProviderPersonId || "",
        professionalProfileCode: userData.professionalProfileCode || "",
        signatureImageData: userData.signatureImageData || "",
        signatureImageContentType: userData.signatureImageContentType || "",
        practitionerPersons: userData.practitionerPersons || [],
      };

      const userManagementInfoToPost = {
        accountActive: userData.accountActive,
        accountDisabled: userData.accountDisabled,
        accountLocked: userData.accountLocked,
        allowCopyUserRoles: userData.allowCopyUserRoles,
        cancelAction: userData.cancelAction,
        cancelMethod: userData.cancelMethod,
        confirmPassword: userData.confirmPassword,
        expirationDate: userData.expirationDate,
        formAction: userData.formAction,
        formMethod: userData.formMethod,
        formName: userData.formName,
        customRoles: userData.customRoles,
        globalRoles: userData.globalRoles,
        labUnitRoles: userData.labUnitRoles,
        loginUserId: userData.loginUserId,
        selectedRoles: userData.selectedRoles || [],
        selectedCustomRoleIds: userData.selectedCustomRoleIds || [],
        selectedTestSectionLabUnits: userData.selectedTestSectionLabUnits || {},
        systemUserId: userData.systemUserId,
        systemUserIdToCopy: userData.systemUserIdToCopy,
        systemUserLastupdated: userData.systemUserLastupdated,
        testSections: userData.testSections,
        timeout: userData.timeout,
        userFirstName: userData.userFirstName,
        userLastName: userData.userLastName,
        userLoginName: userData.userLoginName,
        userPassword: userData.userPassword,
        linkedProviderPersonId: userData.linkedProviderPersonId || "",
        professionalProfileCode: userData.professionalProfileCode || "",
        signatureImageData: userData.signatureImageData || "",
        signatureImageContentType: userData.signatureImageContentType || "",
        practitionerPersons: userData.practitionerPersons || [],
      };
      setUserDataShow(userManagementInfoToShow);
      setUserDataPost(userManagementInfoToPost);

      if (userData.globalRoles) {
        const globalRoles = userData.globalRoles.map((item) => {
          return {
            childrenID: item.childrenID,
            elementID: item.elementID,
            groupingRole: item.groupingRole,
            nestingLevel: item.nestingLevel,
            parentRole: item.parentRole,
            roleId: item.roleId,
            roleName: String(item.roleName || "").trim(),
          };
        });
        setUserDataShow((prevUserDataShow) => ({
          ...prevUserDataShow,
          globalRoles: globalRoles,
        }));
      }

      if (userData.customRoles) {
        const customRoles = userData.customRoles.map((item) => {
          return {
            childrenID: item.childrenID,
            elementID: item.elementID,
            groupingRole: item.groupingRole,
            nestingLevel: item.nestingLevel,
            parentRole: item.parentRole,
            roleId: item.roleId,
            roleName: String(item.roleName || "").trim(),
          };
        });
        setUserDataShow((prevUserDataShow) => ({
          ...prevUserDataShow,
          customRoles: customRoles,
        }));
      }

      if (userData.labUnitRoles) {
        const labUnitRoles = userData.labUnitRoles.map((item) => {
          return {
            childrenID: item.childrenID,
            elementID: item.elementID,
            groupingRole: item.groupingRole,
            nestingLevel: item.nestingLevel,
            parentRole: item.parentRole,
            roleId: item.roleId,
            roleName: String(item.roleName || "").trim(),
          };
        });
        setUserDataShow((prevUserDataShow) => ({
          ...prevUserDataShow,
          labUnitRoles: labUnitRoles,
        }));
      }

      if (userData.testSections) {
        const testSections = userData.testSections.map((item) => {
          return {
            id: item.id,
            value: item.value,
          };
        });
        const updatedTestSections = [
          { id: "AllLabUnits", value: "All Lab Units" },
          ...testSections,
        ];
        setUserDataShow((prevUserDataShow) => ({
          ...prevUserDataShow,
          testSections: updatedTestSections,
        }));
      }

      if (userData.selectedRoles !== undefined) {
        if (ID !== "0") {
          const selectedGlobalLabUnitRoles = userData.selectedRoles.map(
            (item) => item,
          );
          setSelectedGlobalLabUnitRoles(selectedGlobalLabUnitRoles);
        } else {
          setSelectedGlobalLabUnitRoles([]);
        }
      } else {
        setSelectedGlobalLabUnitRoles([]);
      }

      if (userData.selectedCustomRoleIds !== undefined) {
        if (ID !== "0") {
          setSelectedCustomRoles(userData.selectedCustomRoleIds.map((item) => item));
        } else {
          setSelectedCustomRoles([]);
        }
      } else {
        setSelectedCustomRoles([]);
      }

      if (userData.selectedTestSectionLabUnits) {
        if (ID !== "0") {
          setSelectedTestSectionLabUnits(userData.selectedTestSectionLabUnits);
        } else {
          setSelectedTestSectionLabUnits({});
          setSelectedTestSectionList([]);
        }
      }
    }
  }, [userData, ID]);

  useEffect(() => {
    if (userDataShow) {
      setIsLocked(userDataShow.accountLocked === "Y" ? "radio-1" : "radio-2");
      setIsDisabled(
        userDataShow.accountDisabled === "Y" ? "radio-3" : "radio-4",
      );
      setIsActive(userDataShow.accountActive === "Y" ? "radio-5" : "radio-6");
      if (
        userDataShow.userPassword &&
        userDataShow.userPassword === userDataShow.confirmPassword
      ) {
        setValidation((prevValidation) => ({
          ...prevValidation,
          validatepassword: true,
        }));
      } else {
        setValidation((prevValidation) => ({
          ...prevValidation,
          validatepassword: false,
        }));
      }
    }
  }, [userDataShow]);

  useEffect(() => {
    if (copyUserPermission) {
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        systemUserIdToCopy: copyUserPermission,
        allowCopyUserRoles: "Y",
      }));
      setUserDataShow((prevUserData) => ({
        ...prevUserData,
        systemUserIdToCopy: copyUserPermission,
        allowCopyUserRoles: "Y",
      }));
    }
  }, [copyUserPermission]);

  useEffect(() => {
    if (selectedTestSectionLabUnits) {
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        selectedTestSectionLabUnits: selectedTestSectionLabUnits,
      }));

      setUserDataShow((prevUserData) => ({
        ...prevUserData,
        selectedTestSectionLabUnits: selectedTestSectionLabUnits,
      }));
    }
  }, [selectedTestSectionLabUnits]);

  function userSavePostCall() {
    setIsLoading(true);
    postToOpenElisServerJsonResponse(
      `/rest/UnifiedSystemUser`,
      JSON.stringify(userDataPost),
      (res) => {
        userSavePostCallback(res);
      },
    );
  }

  function userSavePostCallback(res) {
    if (res?.forward === "redirect:/UnifiedSystemUser") {
      setIsLoading(false);
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
      setTimeout(() => {
        window.location.assign("/MasterListsPage/userManagement");
      }, 200);
    } else {
      setIsLoading(false);
      addNotification(
        loginNameValidation.isDuplicate
          ? {
              kind: NotificationKinds.error,
              title: intl.formatMessage({ id: "notification.title" }),
              message: intl.formatMessage({
                id: "notification.duplicate.loginName",
              }),
            }
          : {
              kind: NotificationKinds.error,
              title: intl.formatMessage({ id: "notification.title" }),
              message: intl.formatMessage({ id: "server.error.msg" }),
            },
      );
      setNotificationVisible(true);
    }
  }

  async function validateLoginNameUniqueness(loginName, notifyDuplicate = true) {
    const normalizedValue = String(loginName || "").trim();

    if (!normalizedValue || !loginNameRegex.test(normalizedValue)) {
      setLoginNameValidation({
        isChecking: false,
        isDuplicate: false,
        lastCheckedValue: "",
      });
      setValidation((prevValidation) => ({
        ...prevValidation,
        loginName: false,
      }));
      return false;
    }

    setLoginNameValidation((prevState) => ({
      ...prevState,
      isChecking: true,
    }));

    try {
      const response = await getFromOpenElisServerV2(
        `/rest/UnifiedSystemUser/login-name-status?loginName=${encodeURIComponent(
          normalizedValue,
        )}&loginUserId=${encodeURIComponent(
          String(userDataShow?.loginUserId || ""),
        )}`,
      );
      const isDuplicate = Boolean(response?.duplicate);
      setLoginNameValidation({
        isChecking: false,
        isDuplicate,
        lastCheckedValue: normalizedValue,
      });
      setValidation((prevValidation) => ({
        ...prevValidation,
        loginName: !isDuplicate,
      }));

      if (isDuplicate && notifyDuplicate) {
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "notification.duplicate.loginName",
          }),
          kind: NotificationKinds.info,
        });
        setNotificationVisible(true);
      }

      return !isDuplicate;
    } catch (error) {
      setLoginNameValidation((prevState) => ({
        ...prevState,
        isChecking: false,
      }));
      return true;
    }
  }

  async function handleUserSaveClick() {
    const loginName = userDataShow?.userLoginName || "";
    const loginNameIsAvailable = await validateLoginNameUniqueness(
      loginName,
      true,
    );
    if (!loginNameIsAvailable) {
      return;
    }

    userSavePostCall();
  }

  async function handleUserLoginNameBlur() {
    const loginName = String(userDataShow?.userLoginName || "").trim();
    if (!loginName || !loginNameRegex.test(loginName)) {
      return;
    }

    await validateLoginNameUniqueness(loginName, true);
  }

  function getLoginNameInvalidText() {
    if (loginNameValidation.isDuplicate) {
      return intl.formatMessage({
        id: "notification.duplicate.loginName",
      });
    }

    return intl.formatMessage({
      id: "notification.invalid.loginName",
    });
  }

  function handleUserLoginNameChange(e) {
    const value = e.target.value;
    const isValid = loginNameRegex.test(value);

    setLoginNameValidation((prevState) => ({
      ...prevState,
      isDuplicate: false,
      lastCheckedValue:
        prevState.lastCheckedValue === value ? prevState.lastCheckedValue : "",
    }));

    if (!value || (value && !isValid)) {
      if (!notificationVisible) {
        setNotificationVisible(true);
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "notification.invalid.loginName",
          }),
          kind: NotificationKinds.info,
        });
      }
      setSaveButton(true);
      setValidation((prevValidation) => ({
        ...prevValidation,
        loginName: false,
      }));
    } else {
      setNotificationVisible(false);
      setSaveButton(false);
      setValidation((prevValidation) => ({
        ...prevValidation,
        loginName: true,
      }));
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        userLoginName: value,
      }));
    }

    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      userLoginName: value,
    }));
  }

  function handleUserPasswordChange(e) {
    setPasswordTouched((prev) => ({
      ...prev,
      userPassword: true,
    }));
    const value = e.target.value.trim();
    const isValid = passwordPatternRegex.test(value);

    if (value && !isValid) {
      if (!notificationVisible) {
        setNotificationVisible(true);
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "notification.invalid.password",
          }),
          kind: NotificationKinds.info,
        });
      }
      setSaveButton(true);
      setValidation((prevValidation) => ({
        ...prevValidation,
        password: false,
      }));
    } else {
      setNotificationVisible(false);
      setSaveButton(false);
      setValidation((prevValidation) => ({
        ...prevValidation,
        password: true,
      }));
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        userPassword: value,
      }));
    }

    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      userPassword: value,
    }));
  }

  function handleConfirmPasswordChange(e) {
    setPasswordTouched((prev) => ({
      ...prev,
      confirmPassword: true,
    }));
    const value = e.target.value.trim();
    const isValid = passwordPatternRegex.test(value);

    if (value && !isValid) {
      if (!notificationVisible) {
        setNotificationVisible(true);
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "notification.invalid.confirm.password",
          }),
          kind: NotificationKinds.info,
        });
      }
      setSaveButton(true);
      setValidation((prevValidation) => ({
        ...prevValidation,
        password2: false,
      }));
    } else {
      setNotificationVisible(false);
      setSaveButton(false);
      setValidation((prevValidation) => ({
        ...prevValidation,
        password2: true,
      }));
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        confirmPassword: value,
      }));
    }

    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      confirmPassword: value,
    }));
  }

  function handleUserFirstNameChange(e) {
    const value = e.target.value;
    const isValid = nameRegex.test(value);

    if (!value || (value && !isValid)) {
      if (!notificationVisible) {
        setNotificationVisible(true);
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "notification.invalid.name",
          }),
          kind: NotificationKinds.info,
        });
      }
      setSaveButton(true);
      setValidation((prevValidation) => ({
        ...prevValidation,
        firstName: false,
      }));
    } else {
      setNotificationVisible(false);
      setSaveButton(false);
      setValidation((prevValidation) => ({
        ...prevValidation,
        firstName: true,
      }));
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        userFirstName: value,
      }));
    }

    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      userFirstName: value,
    }));
  }

  function handleUserLastNameChange(e) {
    const value = e.target.value;
    const isValid = nameRegex.test(value);

    if (!value || (value && !isValid)) {
      if (!notificationVisible) {
        setNotificationVisible(true);
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "notification.invalid.name",
          }),
          kind: NotificationKinds.info,
        });
      }
      setSaveButton(true);
      setValidation((prevValidation) => ({
        ...prevValidation,
        secondName: false,
      }));
    } else {
      setNotificationVisible(false);
      setUserDataPost((prevUserDataPost) => ({
        ...prevUserDataPost,
        userLastName: value,
      }));
      setSaveButton(false);
      setValidation((prevValidation) => ({
        ...prevValidation,
        secondName: true,
      }));
    }

    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      userLastName: value,
    }));
  }

  function syncLinkedProfessionalNames(providerPersonId) {
    const normalizedPersonId = String(providerPersonId || "").trim();
    if (!normalizedPersonId) {
      return;
    }

    getFromOpenElisServer(
      `/rest/Provider/Person/${encodeURIComponent(normalizedPersonId)}`,
      (person) => {
        if (!person) {
          return;
        }

        const firstName = String(person.firstName || "").trim();
        const lastName = String(person.lastName || "").trim();
        const firstNameIsValid = Boolean(firstName) && nameRegex.test(firstName);
        const lastNameIsValid = Boolean(lastName) && nameRegex.test(lastName);

        setUserDataPost((prevUserDataPost) => ({
          ...prevUserDataPost,
          userFirstName: firstName,
          userLastName: lastName,
        }));
        setUserDataShow((prevUserData) => ({
          ...prevUserData,
          userFirstName: firstName,
          userLastName: lastName,
        }));
        setValidation((prevValidation) => ({
          ...prevValidation,
          firstName: firstNameIsValid,
          secondName: lastNameIsValid,
        }));
        if (firstNameIsValid && lastNameIsValid) {
          setNotificationVisible(false);
        }
      },
    );
  }

  function handleLinkedProviderSelect(providerPersonId) {
    syncLinkedProfessionalNames(providerPersonId);
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      linkedProviderPersonId: providerPersonId || "",
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      linkedProviderPersonId: providerPersonId || "",
    }));
    setSaveButton(false);
  }

  useEffect(() => {
    if (!linkedProviderPersonId) {
      return;
    }

    syncLinkedProfessionalNames(linkedProviderPersonId);
  }, [linkedProviderPersonId]);

  function clearLinkedProvider() {
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      linkedProviderPersonId: "",
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      linkedProviderPersonId: "",
    }));
    setSaveButton(false);
  }

  function handleProfessionalProfileChange(e) {
    const profileCode = e?.target?.value || "";
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      professionalProfileCode: profileCode,
      linkedProviderPersonId: "",
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      professionalProfileCode: profileCode,
      linkedProviderPersonId: "",
    }));
    setSaveButton(false);
  }

  async function handleSignatureFileChange(event) {
    const file = event?.target?.files?.[0];
    if (!file) {
      return;
    }
    const dataUrl = await toBase64(file);
    const contentType = file.type || "image/png";
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      signatureImageData: dataUrl,
      signatureImageContentType: contentType,
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      signatureImageData: dataUrl,
      signatureImageContentType: contentType,
    }));
    setSaveButton(false);
  }

  function clearSignatureImage() {
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      signatureImageData: "",
      signatureImageContentType: "",
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      signatureImageData: "",
      signatureImageContentType: "",
    }));
    setSaveButton(false);
  }

  function handleExpirationDateChange(date) {
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      expDate: true,
    }));
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      expirationDate: date,
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      expirationDate: date,
    }));
  }

  function handleTimeoutChange(e) {
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      timeout: true,
    }));
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      timeout: e.target.value,
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      timeout: e.target.value,
    }));
  }

  function handleAccountActiveChange(e) {
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      active: true,
    }));
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      accountActive: e.target.value,
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      accountActive: e.target.value,
    }));
  }

  function handleAccountDisabledChange(e) {
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      disabled: true,
    }));
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      accountDisabled: e.target.value,
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      accountDisabled: e.target.value,
    }));
  }

  function handleAccountLockedChange(e) {
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      locked: true,
    }));
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      accountLocked: e.target.value,
    }));
    setUserDataShow((prevUserData) => ({
      ...prevUserData,
      accountLocked: e.target.value,
    }));
  }

  function handleCopyUserPermissionsChange() {
    if (copyUserPermission.length > 0) {
      setSaveButton(false);
      setValidation((prevValidation) => ({
        ...prevValidation,
        copy: true,
      }));
    }
  }

  function handleAutoCompleteCopyUserPermissionsChange(selectedUserId) {
    setCopyUserPermission(selectedUserId);
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      autoCopy: true,
    }));
  }

  function handleCopyUserPermissionsChangeClick() {
    setSelectedTestSectionLabUnits([]);
    setSelectedTestSectionList([]);
    userSavePostCall();
  }

  function handleCheckboxChange(roleId) {
    const numberToUpdate = userDataShow.globalRoles
      .filter((role) => role.roleName !== "Global Administrator")
      .map((role) => role.roleId);
    let updatedRoles = [...selectedGlobalLabUnitRoles];

    const globalAdminRoleId = userDataShow.globalRoles.find(
      (role) => role.roleName === "Global Administrator",
    )?.roleId;

    if (globalAdminRoleId && roleId === globalAdminRoleId) {
      if (selectedGlobalLabUnitRoles.includes(roleId)) {
        updatedRoles = updatedRoles.filter((role) => role !== roleId);
      } else {
        updatedRoles = Array.from(
          new Set([...updatedRoles, roleId, ...numberToUpdate]),
        );
      }
    } else {
      if (selectedGlobalLabUnitRoles.includes(roleId)) {
        updatedRoles = updatedRoles.filter((id) => id !== roleId);
      } else {
        updatedRoles = [...updatedRoles, roleId];
      }
    }

    setSelectedGlobalLabUnitRoles(updatedRoles);
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      selectedRoles: updatedRoles,
    }));
    setUserDataShow((prevUserDataPost) => ({
      ...prevUserDataPost,
      selectedRoles: updatedRoles,
    }));
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      checkBox: true,
    }));
  }

  function handleCustomRoleCheckboxChange(roleId) {
    const updatedCustomRoles = selectedCustomRoles.includes(roleId)
      ? selectedCustomRoles.filter((id) => id !== roleId)
      : [...selectedCustomRoles, roleId];

    setSelectedCustomRoles(updatedCustomRoles);
    setUserDataPost((prevUserDataPost) => ({
      ...prevUserDataPost,
      selectedCustomRoleIds: updatedCustomRoles,
    }));
    setUserDataShow((prevUserDataShow) => ({
      ...prevUserDataShow,
      selectedCustomRoleIds: updatedCustomRoles,
    }));
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      customRoles: true,
    }));
  }

  function handleTestSectionsSelectChange(e, key) {
    const selectedValue = e.target.value;
    const index = selectedTestSectionList.indexOf(key);
    if (index != -1) {
      const testSectionList = [...selectedTestSectionList];
      testSectionList[index] = selectedValue;
      setSelectedTestSectionList(testSectionList);
    }

    if (Object.keys(selectedTestSectionLabUnits).includes(selectedValue)) {
      alert(`Section ${selectedValue} is already selected.`);
      const updatedSelections = { ...selectedTestSectionLabUnits };
      delete updatedSelections[selectedValue];
      setSelectedTestSectionLabUnits(updatedSelections);
      return;
    }

    let updatedTestSectionLabUnits = { ...selectedTestSectionLabUnits };

    if (!Object.keys(updatedTestSectionLabUnits).includes(selectedValue)) {
      updatedTestSectionLabUnits[selectedValue] = [];
    } else {
      delete updatedTestSectionLabUnits[selectedValue];
    }

    if (key !== selectedValue) {
      delete updatedTestSectionLabUnits[key];
    }

    setSelectedTestSectionLabUnits(updatedTestSectionLabUnits);
    setSaveButton(false);
    setValidation((prevValidation) => ({
      ...prevValidation,
      testSection: true,
    }));
  }

  const addRoleToSelectedUnits = (key, roleIdToAdd) => {
    setSelectedTestSectionLabUnits((prevUnits) => {
      const updatedUnits = { ...prevUnits };
      const currentRoles = updatedUnits[key] || [];
      if (!currentRoles.includes(roleIdToAdd)) {
        updatedUnits[key] = [...currentRoles, roleIdToAdd];
        setSaveButton(false);
        setValidation((prevValidation) => ({
          ...prevValidation,
          role: true,
        }));
      }
      return updatedUnits;
    });
  };

  const removeRoleFromSelectedUnits = (key, roleIdToRemove) => {
    setSelectedTestSectionLabUnits((prevUnits) => {
      const updatedUnits = { ...prevUnits };
      if (updatedUnits[key]) {
        updatedUnits[key] = updatedUnits[key].filter(
          (roleId) => roleId !== roleIdToRemove,
        );
        setSaveButton(false);
        setValidation((prevValidation) => ({
          ...prevValidation,
          removeSelected: true,
        }));
      }
      return updatedUnits;
    });
  };

  const addNewSection = () => {
    const newSectionsToAdd = userDataShow.testSections.filter(
      (section) =>
        !Object.keys(selectedTestSectionLabUnits).includes(section.id),
    );

    if (newSectionsToAdd.length > 0) {
      const nextSectionToAdd = newSectionsToAdd[0];
      setSelectedTestSectionLabUnits((prev) => ({
        ...prev,
        [nextSectionToAdd.id]: [],
      }));
      const testSectionList = [...selectedTestSectionList];
      testSectionList.push(nextSectionToAdd.id);
      setSelectedTestSectionList(testSectionList);
    }
  };

  const removeSection = (keyToRemove) => {
    const updatedSections = { ...selectedTestSectionLabUnits };
    delete updatedSections[keyToRemove];
    setSelectedTestSectionLabUnits(updatedSections);
    const index = selectedTestSectionList.indexOf(keyToRemove);
    if (index != -1) {
      const testSectionList = [...selectedTestSectionList];
      testSectionList.splice(index, 1);
      setSelectedTestSectionList(testSectionList);
    }
  };

  if (!isLoading) {
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
        <Grid>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <Section>
                <Heading>
                  {ID === "0" ? (
                    <FormattedMessage id="unifiedSystemUser.add.user" />
                  ) : (
                    <FormattedMessage id="unifiedSystemUser.edit.user" />
                  )}
                </Heading>
              </Section>
            </Section>
          </Column>
        </Grid>
        <div className="orderLegendBody">
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Form
              // onSubmit={handleSubmit}
              // onChange={setSaveButton(false)}
              // onBlur={handleBlur}
              >
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.login.name" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="login-name"
                      className="defalut"
                      type="text"
                      labelText=""
                      placeholder={intl.formatMessage({
                        id: "login.login.name",
                      })}
                      invalid={
                        Boolean(
                          userDataShow?.userLoginName &&
                            (!loginNameRegex.test(userDataShow.userLoginName) ||
                              loginNameValidation.isDuplicate),
                        )
                      }
                      invalidText={getLoginNameInvalidText()}
                      required={true}
                      value={
                        userDataShow && userDataShow.userLoginName
                          ? userDataShow.userLoginName
                          : ""
                      }
                      onChange={(e) => handleUserLoginNameChange(e)}
                      onBlur={handleUserLoginNameBlur}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <h5>
                      <FormattedMessage id="login.complexity.message" />
                    </h5>
                    <br />

                    <h6>
                      <UnorderedList nested={true}>
                        <ListItem>
                          <FormattedMessage id="login.complexity.message.1" />
                        </ListItem>
                        <ListItem>
                          <FormattedMessage id="login.complexity.message.2" />
                        </ListItem>
                        <ListItem>
                          <FormattedMessage id="login.complexity.message.3" />
                        </ListItem>
                        <ListItem>
                          <FormattedMessage id="login.complexity.message.4" />
                        </ListItem>
                      </UnorderedList>
                    </h6>
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.login.password" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <PasswordInput
                      id="login-password"
                      className="defalut"
                      type="password"
                      labelText=""
                      placeholder={intl.formatMessage({
                        id: "login.login.password",
                      })}
                      required={true}
                      invalid={
                        passwordTouched.userPassword &&
                        userDataShow &&
                        userDataShow.userPassword &&
                        !passwordPatternRegex.test(userDataShow.userPassword)
                      }
                      // invalidText={errors.order}
                      value={
                        userDataShow && userDataShow.userPassword
                          ? userDataShow.userPassword
                          : ""
                      }
                      onChange={(e) => handleUserPasswordChange(e)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.login.repeat.password" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <PasswordInput
                      id="login-repeat-password"
                      className="defalut"
                      type="password"
                      labelText=""
                      placeholder={intl.formatMessage({
                        id: "login.login.repeat.password",
                      })}
                      required={true}
                      invalid={
                        (passwordTouched.confirmPassword &&
                          userDataShow &&
                          userDataShow.userPassword &&
                          userDataShow.confirmPassword &&
                          !passwordPatternRegex.test(
                            userDataShow.confirmPassword,
                          )) ||
                        (passwordTouched.confirmPassword &&
                          userDataShow.confirmPassword !==
                            userDataShow.userPassword)
                      }
                      // invalidText={errors.order}
                      value={
                        userDataShow && userDataShow.confirmPassword
                          ? userDataShow.confirmPassword
                          : ""
                      }
                      onChange={(e) => handleConfirmPasswordChange(e)}
                    />
                  </Column>
                </Grid>
                <br />

                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() =>
                        window.location.assign(
                          "/MasterListsPage/profileManagement",
                        )
                      }
                    >
                      <FormattedMessage
                        id="professionalProfile.actions.manage"
                        defaultMessage="Manage professional profiles"
                      />
                    </Button>
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <FormattedMessage id="unifiedSystemUser.professional.profile.label" />
                    {" :"}
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <Select
                      id="professional-profile-code"
                      labelText=""
                      value={userDataShow?.professionalProfileCode || ""}
                      onChange={handleProfessionalProfileChange}
                    >
                      <SelectItem
                        value=""
                        text={intl.formatMessage({
                          id: "unifiedSystemUser.professional.profile.select",
                        })}
                      />
                      {professionalProfileOptions.map((option) => (
                        <SelectItem
                          key={option.code}
                          value={option.code}
                          text={option.label}
                        />
                      ))}
                    </Select>
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <FormattedMessage id="unifiedSystemUser.linked.provider.label" />
                    {" :"}
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <AutoComplete
                      key={`linked-provider-${selectedProfessionalProfileCode}-${userDataShow?.linkedProviderPersonId || ""}`}
                      id="linked-provider-person"
                      name="professional-link-search"
                      autoComplete="one-time-code"
                      dataFormType="other"
                      allowFreeText={false}
                      maxSuggestions={6}
                      value={userDataShow?.linkedProviderPersonId || ""}
                      onSelect={handleLinkedProviderSelect}
                      onChange={clearLinkedProvider}
                      suggestions={linkedProviderSuggestions}
                      disabled={!selectedProfessionalProfileCode}
                      label=""
                    />
                    {isLinkedProfessionalSelected ? (
                      <div style={{ marginTop: "0.5rem", fontSize: "0.875rem" }}>
                        <FormattedMessage
                          id="unifiedSystemUser.linked.provider.sync.notice"
                          defaultMessage="User first and last name are synchronized from the linked professional."
                        />
                      </div>
                    ) : null}
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.login.first" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="first-name"
                      className="defalut"
                      type="text"
                      labelText=""
                      placeholder={intl.formatMessage({
                        id: "login.login.first",
                      })}
                      required={true}
                      invalid={
                        userDataShow &&
                        userDataShow.userFirstName &&
                        !nameRegex.test(userDataShow.userFirstName)
                      }
                      // invalidText={errors.order}
                      value={
                        userDataShow && userDataShow.userFirstName
                          ? userDataShow.userFirstName
                          : ""
                      }
                      disabled={isLinkedProfessionalSelected}
                      onChange={(e) => handleUserFirstNameChange(e)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.login.last" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="last-name"
                      className="defalut"
                      type="text"
                      labelText=""
                      placeholder={intl.formatMessage({
                        id: "login.login.last",
                      })}
                      required={true}
                      invalid={
                        userDataShow &&
                        userDataShow.userLastName &&
                        !nameRegex.test(userDataShow.userLastName)
                      }
                      // invalidText={errors.order}
                      value={
                        userDataShow && userDataShow.userLastName
                          ? userDataShow.userLastName
                          : ""
                      }
                      disabled={isLinkedProfessionalSelected}
                      onChange={(e) => handleUserLastNameChange(e)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <FormattedMessage id="unifiedSystemUser.signature.label" />
                    {" :"}
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <input
                      id="user-signature-image"
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      onChange={handleSignatureFileChange}
                    />
                    {userDataShow?.signatureImageData ? (
                      <>
                        <br />
                        <img
                          src={userDataShow.signatureImageData}
                          alt={intl.formatMessage({
                            id: "unifiedSystemUser.signature.preview.alt",
                          })}
                          style={{
                            maxWidth: "240px",
                            maxHeight: "120px",
                            border: "1px solid #c6c6c6",
                            marginTop: "8px",
                            marginBottom: "8px",
                          }}
                        />
                        <br />
                        <Button
                          kind="ghost"
                          size="sm"
                          onClick={clearSignatureImage}
                        >
                          <FormattedMessage id="unifiedSystemUser.signature.remove" />
                        </Button>
                      </>
                    ) : null}
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.password.expired.date" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <CustomDatePicker
                      id="password-expire-date"
                      className="defalut"
                      labelText=""
                      required={true}
                      disallowPastDate={true}
                      updateStateValue={true}
                      value={
                        userDataShow && userDataShow.expirationDate
                          ? userDataShow.expirationDate
                          : ""
                      }
                      onChange={(date) => handleExpirationDateChange(date)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.timeout" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="login-timeout"
                      className="defalut"
                      type="number"
                      placeholder={intl.formatMessage({
                        id: "login.timeout.placeholder",
                      })}
                      required={true}
                      labelText=""
                      min={0}
                      // invalid={errors.order && touched.order}
                      // invalidText={errors.order}
                      value={
                        userDataShow && userDataShow.timeout
                          ? userDataShow.timeout
                          : ""
                      }
                      onChange={(e) => handleTimeoutChange(e)}
                    />
                  </Column>
                </Grid>
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.account.locked" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <span style={{ display: "flex", alignItems: "center" }}>
                      <RadioButton
                        checked={isLocked === "radio-1"}
                        labelText="Y"
                        value="Y"
                        id="radio-1"
                        onClick={(e) => {
                          setIsLocked("radio-1");
                          handleAccountLockedChange(e);
                        }}
                      />
                      <RadioButton
                        checked={isLocked === "radio-2"}
                        labelText="N"
                        value="N"
                        id="radio-2"
                        onClick={(e) => {
                          setIsLocked("radio-2");
                          handleAccountLockedChange(e);
                        }}
                      />
                    </span>
                  </Column>
                </Grid>
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="login.account.disabled" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <span style={{ display: "flex", alignItems: "center" }}>
                      <RadioButton
                        checked={isDisabled === "radio-3"}
                        labelText="Y"
                        value="Y"
                        id="radio-3"
                        onClick={(e) => {
                          setIsDisabled("radio-3");
                          handleAccountDisabledChange(e);
                        }}
                      />
                      <RadioButton
                        checked={isDisabled === "radio-4"}
                        labelText="N"
                        value="N"
                        id="radio-4"
                        onClick={(e) => {
                          setIsDisabled("radio-4");
                          handleAccountDisabledChange(e);
                        }}
                      />
                    </span>
                  </Column>
                </Grid>
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="systemuser.isActive" />
                      <span className="requiredlabel">*</span> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <span style={{ display: "flex", alignItems: "center" }}>
                      <RadioButton
                        checked={isActive === "radio-5"}
                        labelText="Y"
                        value="Y"
                        id="radio-5"
                        onClick={(e) => {
                          setIsActive("radio-5");
                          handleAccountActiveChange(e);
                        }}
                      />
                      <RadioButton
                        checked={isActive === "radio-6"}
                        labelText="N"
                        value="N"
                        id="radio-6"
                        onClick={(e) => {
                          setIsActive("radio-6");
                          handleAccountActiveChange(e);
                        }}
                      />
                    </span>
                  </Column>
                </Grid>
                <br />
                <hr />
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <>
                      <FormattedMessage id="systemuserrole.copypermissions" /> :
                    </>
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <AutoComplete
                      name="copy-permissions"
                      id="copy-permissions"
                      allowFreeText={
                        !(
                          configurationProperties.restrictFreeTextProviderEntry ===
                          "true"
                        )
                      }
                      onChange={handleCopyUserPermissionsChange}
                      onSelect={handleAutoCompleteCopyUserPermissionsChange}
                      suggestions={
                        copyUserPermissionList?.length > 0
                          ? copyUserPermissionList
                          : []
                      }
                    />
                  </Column>
                  <br />
                  <Button
                    data-cy="apply-button"
                    disabled={copyUserPermission === "0"}
                    type="button"
                    onClick={() => {
                      handleCopyUserPermissionsChangeClick();
                    }}
                  >
                    <FormattedMessage id="systemuserrole.apply" />
                  </Button>
                </Grid>
                <hr />
                <br />
                <Grid fullWidth={true}>
                  <Column lg={8} md={4} sm={4}>
                    <FormattedMessage
                      id="customRole.assignment.title"
                      defaultMessage="Assigned Roles"
                    />
                    <br />
                    <br />
                    <FormGroup legendId="customRoles" legendText="">
                      {userDataShow &&
                      userDataShow.customRoles &&
                      userDataShow.customRoles.length > 0 ? (
                        userDataShow.customRoles.map((section) => (
                          <Checkbox
                            key={`custom-${section.elementID}`}
                            id={`custom-${section.elementID}`}
                            value={section.roleId}
                            labelText={section.roleName}
                            checked={selectedCustomRoles.includes(
                              section.roleId,
                            )}
                            onChange={() => {
                              handleCustomRoleCheckboxChange(section.roleId);
                            }}
                          />
                        ))
                      ) : (
                        <Checkbox
                          id="no-options-custom-roles"
                          value=""
                          labelText={intl.formatMessage({
                            id: "customRole.assignment.empty",
                            defaultMessage: "No custom roles available",
                          })}
                        />
                      )}
                    </FormGroup>
                    <Button
                      kind="ghost"
                      type="button"
                      onClick={() =>
                        window.location.assign("/MasterListsPage/roleManagement")
                      }
                    >
                      <FormattedMessage
                        id="customRole.assignment.manage"
                        defaultMessage="Manage custom roles"
                      />
                    </Button>
                    <br />
                    <br />
                    <Button
                      kind="tertiary"
                      type="button"
                      onClick={() =>
                        setShowLegacyPermissions(
                          (previousState) => !previousState,
                        )
                      }
                    >
                      <FormattedMessage
                        id={
                          showLegacyPermissions
                            ? "customRole.assignment.legacyToggle.hide"
                            : "customRole.assignment.legacyToggle"
                        }
                        defaultMessage={
                          showLegacyPermissions
                            ? "Hide legacy permissions"
                            : "Show legacy permissions"
                        }
                      />
                    </Button>
                  </Column>
                </Grid>
                <br />
                {showLegacyPermissions && (
                  <>
                    <Grid fullWidth={true}>
                      <Column lg={8} md={4} sm={4}>
                        <FormattedMessage id="systemuserrole.roles.global" />
                        <br />
                        <FormGroup legendId="globalRules" legendText="">
                          {userDataShow &&
                          userDataShow.globalRoles &&
                          userDataShow.globalRoles.length > 0 ? (
                            userDataShow.globalRoles.map((section) => (
                              <Checkbox
                                key={section.elementID}
                                id={section.elementID}
                                value={section.roleId}
                                labelText={section.roleName}
                                checked={selectedGlobalLabUnitRoles.includes(
                                  section.roleId,
                                )}
                                onChange={() => {
                                  handleCheckboxChange(section.roleId);
                                }}
                              />
                            ))
                          ) : (
                            <Checkbox
                              id="no-options-global-roles"
                              value=""
                              labelText="No options available"
                            />
                          )}
                        </FormGroup>
                        <br />
                      </Column>
                    </Grid>
                    <Grid fullWidth={true}>
                      <Column lg={8} md={4} sm={4}>
                        <FormattedMessage id="systemuserrole.roles.labunit" />
                      </Column>
                    </Grid>
                    <br />
                    <>
                      {selectedTestSectionList.map((key) => (
                        <Grid
                          fullWidth={true}
                          key={key}
                          style={{ paddingBottom: "10px" }}
                        >
                          <Column lg={4} md={4} sm={4}>
                            <Select
                              id={`select-${key}`}
                              noLabel={true}
                              defaultValue={
                                userDataShow &&
                                userDataShow.testSections &&
                                userDataShow.testSections.length > 0
                                  ? userDataShow.testSections.find(
                                      (section) => section.id === key,
                                    )?.id || userDataShow.testSections[0].id
                                  : ""
                              }
                              onChange={(e) =>
                                handleTestSectionsSelectChange(e, key)
                              }
                            >
                              {userDataShow &&
                              userDataShow.testSections &&
                              userDataShow.testSections.length > 0 ? (
                                userDataShow.testSections
                                  .filter(
                                    (section) =>
                                      !Object.keys(
                                        selectedTestSectionLabUnits,
                                      ).includes(section.id) ||
                                      section.id === key,
                                  )
                                  .map((section) => (
                                    <SelectItem
                                      key={`${section.id}-${key}`}
                                      value={section.id}
                                      text={section.value}
                                    />
                                  ))
                              ) : (
                                <SelectItem
                                  key="no-option-test-section"
                                  value=""
                                  text="No options available"
                                />
                              )}
                            </Select>
                            <br />
                            <Checkbox
                              id={`all-permissions-${key}`}
                              labelText={"All Permissions"}
                              checked={isAllPermissionsSelected(
                                selectedTestSectionLabUnits,
                                key,
                                userDataShow?.labUnitRoles,
                              )}
                              onChange={() => {
                                const updatedRoles =
                                  toggleAllPermissionsForLabUnit(
                                    selectedTestSectionLabUnits,
                                    key,
                                    userDataShow?.labUnitRoles,
                                  );
                                setSelectedTestSectionLabUnits((prev) => ({
                                  ...prev,
                                  [key]: updatedRoles,
                                }));
                                setSaveButton(false);
                                setValidation({
                                  ...validation,
                                  selectedLab: true,
                                });
                              }}
                            />
                            <FormGroup
                              key={key}
                              legendId={`labUnitRoles-${key}`}
                              legendText=""
                            >
                              {userDataShow &&
                              userDataShow.labUnitRoles &&
                              userDataShow.labUnitRoles.length > 0 ? (
                                userDataShow.labUnitRoles.map((section) => (
                                  <Checkbox
                                    key={`${section.elementID}-${key}`}
                                    id={`${section.elementID}-${key}`}
                                    value={section.roleId}
                                    labelText={section.roleName}
                                    checked={
                                      selectedTestSectionLabUnits[key] &&
                                      selectedTestSectionLabUnits[
                                        key
                                      ].includes(section.roleId)
                                    }
                                    onChange={() => {
                                      if (
                                        selectedTestSectionLabUnits[
                                          key
                                        ]?.includes(section.roleId)
                                      ) {
                                        removeRoleFromSelectedUnits(
                                          key,
                                          section.roleId,
                                        );
                                      } else {
                                        addRoleToSelectedUnits(
                                          key,
                                          section.roleId,
                                        );
                                      }
                                    }}
                                  />
                                ))
                              ) : (
                                <Checkbox
                                  id="no-options-lab-units"
                                  value=""
                                  labelText="No options available"
                                />
                              )}
                            </FormGroup>
                          </Column>
                          <Column lg={4} md={4} sm={4}>
                            <Button
                              data-cy="removePermission"
                              onClick={() => removeSection(key)}
                              kind="tertiary"
                              type="button"
                            >
                              <FormattedMessage id="systemuserrole.rmpermissions" />
                            </Button>
                          </Column>
                        </Grid>
                      ))}
                    </>
                    <Grid fullWidth={true}>
                      <Column lg={16} md={8} sm={4}>
                        <Button
                          data-cy="addNewPermission"
                          onClick={addNewSection}
                          type="button"
                        >
                          <FormattedMessage id="systemuserrole.newpermissions" />
                        </Button>
                      </Column>
                    </Grid>
                  </>
                )}
                <hr />
                <br />
                <Grid fullWidth={true}>
                  <Column lg={16} md={8} sm={4}>
                    <Button
                      disabled={
                        Object.values(validation).some((value) => !value) ||
                        loginNameValidation.isChecking ||
                        loginNameValidation.isDuplicate
                      }
                      data-cy="saveButton"
                      onClick={handleUserSaveClick}
                      type="button"
                    >
                      <FormattedMessage id="label.button.save" />
                    </Button>{" "}
                    <Button
                      onClick={() =>
                        window.location.assign(
                          "/MasterListsPage/userManagement",
                        )
                      }
                      data-cy="exitButton"
                      kind="tertiary"
                      type="button"
                    >
                      <FormattedMessage id="label.button.exit" />
                    </Button>
                  </Column>
                </Grid>
              </Form>
            </Column>
          </Grid>
        </div>
      </div>
    </>
  );
}

export default injectIntl(UserAddModify);
