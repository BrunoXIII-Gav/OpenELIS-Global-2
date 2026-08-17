import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Button,
  Checkbox,
  Column,
  DataTable,
  Grid,
  Heading,
  Loading,
  Modal,
  MultiSelect,
  Pagination,
  Search,
  Section,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TableSelectRow,
  TextInput,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  getFromOpenElisServer,
  postToOpenElisServerFullResponse,
} from "../../utils/Utils.js";
import {
  ConfigurationContext,
  NotificationContext,
} from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import ActionPaginationButtonType from "../../common/ActionPaginationButtonType.js";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "provider.browse.title",
    link: "/MasterListsPage/providerMenu",
  },
];

const createEmptyFormState = (profileCode = "") => ({
  providerId: "",
  fhirUuid: "",
  professionalProfileCode: profileCode,
  active: true,
  lastName: "",
  firstName: "",
  telephone: "",
  fax: "",
  email: "",
  dni: "",
  specialty: "",
  professionalInitials: "",
});

const normalizeDynamicFields = (fields = []) =>
  fields.map((field) => ({
    ...field,
    options: Array.isArray(field.options) ? field.options : [],
  }));

const normalizeProfileCode = (value = "") => value.trim().toUpperCase();
const normalizeFieldType = (fieldType = "") => fieldType.trim().toUpperCase();

const formatDynamicFieldValue = (field, rawValue) => {
  if (rawValue == null || rawValue === "") {
    return "";
  }

  const fieldType = normalizeFieldType(field.fieldType);

  if (fieldType === "BOOLEAN") {
    return rawValue ? "true" : "false";
  }

  if (fieldType === "MULTISELECT") {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];
    const labelsByKey = (field.options || []).reduce((accumulator, option) => {
      accumulator[option.optionKey] = option.optionLabel;
      return accumulator;
    }, {});

    return values
      .map((value) => labelsByKey[value] || value)
      .filter(Boolean)
      .join(", ");
  }

  if (fieldType === "SELECT") {
    const selectedOption = (field.options || []).find(
      (option) => option.optionKey === rawValue,
    );
    return selectedOption?.optionLabel || rawValue;
  }

  return String(rawValue);
};

const isNumericFieldValueValid = (value) => {
  if (value == null) {
    return true;
  }

  const normalizedValue = String(value).trim();
  if (normalizedValue === "") {
    return true;
  }

  return /^\d*([.,]\d*)?$/.test(normalizedValue);
};

const isDynamicFieldEmpty = (field, value) => {
  switch (normalizeFieldType(field.fieldType)) {
    case "MULTISELECT":
      return !Array.isArray(value) || value.length === 0;
    case "BOOLEAN":
      return value !== true;
    default:
      return value == null || String(value).trim() === "";
  }
};

const REQUIRED_FIXED_FIELDS = [
  "professionalProfileCode",
  "lastName",
  "firstName",
  "dni",
  "professionalInitials",
];

function ProviderMenu() {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const { reloadConfiguration, configurationProperties } =
    useContext(ConfigurationContext);

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [modifyButton, setModifyButton] = useState(true);
  const [deactivateButton, setDeactivateButton] = useState(true);
  const [selectedRowIds, setSelectedRowIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isSearching, setIsSearching] = useState(false);
  const [panelSearchTerm, setPanelSearchTerm] = useState("");
  const [startingRecNo, setStartingRecNo] = useState(1);
  const [providerMenuList, setProviderMenuList] = useState({});
  const [providerMenuListShow, setProviderMenuListShow] = useState([]);
  const [fromRecordCount, setFromRecordCount] = useState("");
  const [toRecordCount, setToRecordCount] = useState("");
  const [totalRecordCount, setTotalRecordCount] = useState("");
  const [paging, setPaging] = useState(1);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isUpdateModalOpen, setIsUpdateModalOpen] = useState(false);
  const [currentProvider, setCurrentProvider] = useState(null);
  const [formState, setFormState] = useState(createEmptyFormState());
  const [dynamicFields, setDynamicFields] = useState([]);
  const [dynamicFieldValues, setDynamicFieldValues] = useState({});
  const [specialtyOptions, setSpecialtyOptions] = useState([]);
  const [selectedProfileFilters, setSelectedProfileFilters] = useState([]);
  const [selectedProfileTableFields, setSelectedProfileTableFields] = useState([]);
  const [professionalProfileOptions, setProfessionalProfileOptions] = useState([]);
  const [hasAttemptedDynamicSave, setHasAttemptedDynamicSave] = useState(false);

  const providerSpecialtyOptions = useMemo(
    () =>
      (configurationProperties?.providerSpecialtyOptions || "")
        .split(/[\n,]+/)
        .map((entry) => entry.trim())
        .filter(Boolean)
        .map((entry) => {
          const parts = entry.split("|");
          const id = (parts[0] || "").trim();
          const value = (parts[1] || parts[0] || "").trim();
          return id ? { id, value } : null;
        })
        .filter(Boolean),
    [configurationProperties?.providerSpecialtyOptions],
  );

  const profileFilterItems = useMemo(
    () =>
      professionalProfileOptions.map((option) => ({
        id: option.code,
        label: option.label,
      })),
    [professionalProfileOptions],
  );

  const profileLabelByCode = useMemo(
    () =>
      professionalProfileOptions.reduce((accumulator, option) => {
        accumulator[option.code] = option.label;
        return accumulator;
      }, {}),
    [professionalProfileOptions],
  );

  const selectedProfessionalProfileCode = formState.professionalProfileCode || "";

  const availableSpecialtyOptions = useMemo(() => {
    if (specialtyOptions.length > 0) {
      return specialtyOptions;
    }
    return providerSpecialtyOptions.map((option) => ({
      optionKey: option.id,
      optionLabel: option.value,
    }));
  }, [providerSpecialtyOptions, specialtyOptions]);

  const handleMenuItems = (response) => {
    if (!response) {
      setProviderMenuList({});
      setProviderMenuListShow([]);
      setLoading(false);
      return;
    }
    setProviderMenuList(response);
  };

  useEffect(() => {
    setLoading(true);
    getFromOpenElisServer(
      `/rest/ProviderMenu?paging=${paging}&startingRecNo=${startingRecNo}`,
      handleMenuItems,
    );
  }, [paging, startingRecNo]);

  useEffect(() => {
    getFromOpenElisServer("/rest/professional-profiles/catalog", (response) => {
      const profileOptions = Array.isArray(response?.profiles)
        ? response.profiles
            .map((profile) => ({
              code: (profile?.code || "").trim(),
              label: (profile?.name || profile?.code || "").trim(),
            }))
            .filter((profile) => profile.code)
        : [];

      setProfessionalProfileOptions(profileOptions);
    });
  }, []);

  useEffect(() => {
    if (!isSearching) {
      return;
    }
    getFromOpenElisServer(
      `/rest/SearchProviderMenu?search=Y&startingRecNo=${startingRecNo}&searchString=${panelSearchTerm}`,
      handleMenuItems,
    );
  }, [panelSearchTerm, startingRecNo, isSearching]);

  useEffect(() => {
    if (providerMenuList.providers) {
      const newProviderMenuList = providerMenuList.providers.map((item) => {
        const person = item.person || {};
        return {
          id: item.id,
          fhirUuid: item.fhirUuid,
          lastName: person.lastName || "",
          firstName: person.firstName || "",
          active: !!item.active,
          telephone: person.workPhone || "",
          fax: person.fax || "",
          email: person.email || "",
          dni: item.dni || "",
          specialty: item.specialty || "",
          npi: item.npi || "",
          externalId: item.externalId || "",
          professionalInitials: item.professionalInitials || "",
          cbpCode: item.cbpCode || "",
          profileFieldValues:
            item.profileFieldValues &&
            typeof item.profileFieldValues === "object"
              ? item.profileFieldValues
              : {},
          professionalProfileCode: item.professionalProfileCode || "",
          professionalProfileLabel:
            profileLabelByCode[item.professionalProfileCode] ||
            item.professionalProfileCode ||
            "",
        };
      });
      setFromRecordCount(providerMenuList.fromRecordCount);
      setToRecordCount(providerMenuList.toRecordCount);
      setTotalRecordCount(providerMenuList.totalRecordCount);
      setProviderMenuListShow(newProviderMenuList);
    } else {
      setProviderMenuListShow([]);
    }
    setLoading(false);
  }, [providerMenuList, profileLabelByCode]);

  useEffect(() => {
    setModifyButton(selectedRowIds.length !== 1);
    setDeactivateButton(selectedRowIds.length === 0);
  }, [selectedRowIds]);

  useEffect(() => {
    if (isSearching && panelSearchTerm === "") {
      setIsSearching(false);
      setPaging(1);
      setStartingRecNo(1);
      setLoading(true);
      getFromOpenElisServer(
        `/rest/ProviderMenu?paging=1&startingRecNo=1`,
        handleMenuItems,
      );
    }
  }, [isSearching, panelSearchTerm]);

  useEffect(() => {
    const isAnyModalOpen = isAddModalOpen || isUpdateModalOpen;
    if (!isAnyModalOpen || !selectedProfessionalProfileCode) {
      if (isAnyModalOpen) {
        setDynamicFields([]);
        setDynamicFieldValues({});
        setSpecialtyOptions([]);
      }
      return;
    }

    const providerId = currentProvider?.id || "";
    getFromOpenElisServer(
      `/rest/providers/form-config?profileCode=${encodeURIComponent(
        selectedProfessionalProfileCode,
      )}&providerId=${encodeURIComponent(providerId)}`,
      (response) => {
        const nextFields = normalizeDynamicFields(response?.fields || []).filter(
          (field) => field.fieldKey !== "PROFESSIONAL_INITIALS",
        );
        const nextValues = nextFields.reduce((accumulator, field) => {
          const fieldType = normalizeFieldType(field.fieldType);
          if (fieldType === "MULTISELECT") {
            accumulator[field.fieldKey] = Array.isArray(field.currentValue)
              ? field.currentValue
              : [];
          } else if (fieldType === "BOOLEAN") {
            accumulator[field.fieldKey] = !!field.currentValue;
          } else {
            accumulator[field.fieldKey] = field.currentValue ?? "";
          }
          return accumulator;
        }, {});
        setSpecialtyOptions(
          Array.isArray(response?.specialtyOptions)
            ? response.specialtyOptions.filter(
                (option) => option?.optionKey && option?.optionLabel,
              )
            : [],
        );
        setDynamicFields(nextFields);
        setDynamicFieldValues(nextValues);
      },
    );
  }, [
    isAddModalOpen,
    isUpdateModalOpen,
    selectedProfessionalProfileCode,
    currentProvider?.id,
  ]);

  const openAddModal = () => {
    const defaultProfileCode = professionalProfileOptions[0]?.code || "";
    setCurrentProvider(null);
    setFormState(createEmptyFormState(defaultProfileCode));
    setDynamicFields([]);
    setDynamicFieldValues({});
    setSpecialtyOptions([]);
    setHasAttemptedDynamicSave(false);
    setIsAddModalOpen(true);
  };

  const closeAddModal = () => {
    setIsAddModalOpen(false);
    setCurrentProvider(null);
    setSpecialtyOptions([]);
    setHasAttemptedDynamicSave(false);
  };

  const openUpdateModal = (providerId) => {
    const provider = providerMenuListShow.find((current) => current.id === providerId);
    if (!provider) {
      return;
    }
    setCurrentProvider(provider);
    setFormState({
      providerId: provider.id,
      fhirUuid: provider.fhirUuid,
      professionalProfileCode: provider.professionalProfileCode || professionalProfileOptions[0]?.code || "",
      active: !!provider.active,
      lastName: provider.lastName || "",
      firstName: provider.firstName || "",
      telephone: provider.telephone || "",
      fax: provider.fax || "",
      email: provider.email || "",
      dni: provider.dni || "",
      specialty: provider.specialty || "",
      professionalInitials: provider.professionalInitials || "",
    });
    setDynamicFields([]);
    setDynamicFieldValues({});
    setSpecialtyOptions([]);
    setHasAttemptedDynamicSave(false);
    setIsUpdateModalOpen(true);
  };

  const closeUpdateModal = () => {
    setIsUpdateModalOpen(false);
    setCurrentProvider(null);
    setSpecialtyOptions([]);
    setHasAttemptedDynamicSave(false);
  };

  const getResponseMessage = async (response) => {
    if (!response || response.ok) {
      return "";
    }

    try {
      const contentType = response.headers?.get?.("content-type") || "";
      if (contentType.includes("application/json")) {
        const json = await response.clone().json();
        return json?.message || json?.error || "";
      }

      return (await response.clone().text()) || "";
    } catch (error) {
      return "";
    }
  };

  const displayStatus = async (response) => {
    setNotificationVisible(true);
    if (response.status === 201 || response.status === 200) {
      addNotification({
        kind: NotificationKinds.success,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "save.config.success.msg" }),
      });
    } else {
      const backendMessage = await getResponseMessage(response);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message:
          backendMessage ||
          intl.formatMessage({ id: "server.error.msg" }),
      });
    }
    reloadConfiguration();
  };

  const deleteDeactivateProvider = (event) => {
    event.preventDefault();
    setLoading(true);
    postToOpenElisServerFullResponse(
      `/rest/DeleteProvider?ID=${selectedRowIds.join(",")}&${startingRecNo}=1`,
      providerMenuListShow,
      (response) => {
        displayStatus(response);
        window.location.reload();
      },
    );
  };

  const handlePageChange = ({ page: nextPage, pageSize: nextPageSize }) => {
    setPage(nextPage);
    setPageSize(nextPageSize);
    setSelectedRowIds([]);
  };

  const handleNextPage = () => {
    setPaging((currentPaging) => Math.max(currentPaging, 2));
    setStartingRecNo(fromRecordCount);
    setSelectedRowIds([]);
  };

  const handlePreviousPage = () => {
    setPaging((currentPaging) => Math.max(currentPaging - 1, 1));
    setStartingRecNo(Math.max(fromRecordCount, 1));
    setSelectedRowIds([]);
  };

  const handlePanelSearchChange = (event) => {
    setIsSearching(true);
    setPaging(1);
    setStartingRecNo(1);
    setPanelSearchTerm(event.target.value.toLowerCase());
    setSelectedRowIds([]);
  };

  const updateFormState = (key, value) => {
    setFormState((previousFormState) => ({
      ...previousFormState,
      [key]: value,
    }));
  };

  const handleProfileCodeChange = (value) => {
    setDynamicFields([]);
    setDynamicFieldValues({});
    setSpecialtyOptions([]);
    setFormState((previousFormState) => ({
      ...previousFormState,
      professionalProfileCode: value,
      specialty: "",
    }));
  };

  const updateDynamicFieldValue = (fieldKey, value) => {
    setDynamicFieldValues((previousValues) => ({
      ...previousValues,
      [fieldKey]: value,
    }));
  };

  const handleProviderSaveResponse = (response, onSuccess) => {
    displayStatus(response);
    if (response?.ok) {
      onSuccess?.();
      window.location.reload();
    }
  };

  const validateDynamicFieldsBeforeSave = () => {
    const missingFixedField = REQUIRED_FIXED_FIELDS.find((fieldKey) => {
      const value = formState[fieldKey];
      return value == null || String(value).trim() === "";
    });

    if (missingFixedField) {
      const labelsByField = {
        professionalProfileCode: intl.formatMessage({
          id: "provider.professional.profile",
          defaultMessage: "Professional profile",
        }),
        lastName: intl.formatMessage({
          id: "provider.providerLastName",
          defaultMessage: "Professional last name",
        }),
        firstName: intl.formatMessage({
          id: "provider.providerFirstName",
          defaultMessage: "Professional first name",
        }),
        dni: "DNI",
        specialty: intl.formatMessage({
          id: "provider.specialty.label",
          defaultMessage: "Specialty",
        }),
        professionalInitials: intl.formatMessage({
          id: "provider.initials.label",
          defaultMessage: "Initials",
        }),
      };

      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage(
          {
            id: "provider.fixedFields.required.invalid",
            defaultMessage: "The field {fieldName} is required.",
          },
          {
            fieldName: labelsByField[missingFixedField] || missingFixedField,
          },
        ),
      });
      return false;
    }

    const missingRequiredField = dynamicFields.find(
      (field) =>
        field.required === true &&
        field.active !== false &&
        isDynamicFieldEmpty(field, dynamicFieldValues[field.fieldKey]),
    );

    if (missingRequiredField) {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage(
          {
            id: "professionalProfile.fields.required.invalid",
            defaultMessage: "The field {fieldName} is required.",
          },
          {
            fieldName:
              missingRequiredField.displayName || missingRequiredField.fieldKey,
          },
        ),
      });
      return false;
    }

    const invalidNumberField = dynamicFields.find(
      (field) =>
        normalizeFieldType(field.fieldType) === "NUMBER" &&
        !isNumericFieldValueValid(dynamicFieldValues[field.fieldKey]),
    );

    if (!invalidNumberField) {
      return true;
    }

    setNotificationVisible(true);
    addNotification({
      kind: NotificationKinds.error,
      title: intl.formatMessage({ id: "notification.title" }),
      message: intl.formatMessage(
        {
          id: "professionalProfile.fields.number.invalid",
          defaultMessage:
            "The field {fieldName} only accepts numbers and optional decimals.",
        },
        {
          fieldName:
            invalidNumberField.displayName || invalidNumberField.fieldKey,
        },
      ),
    });
    return false;
  };

  const buildProviderPayload = () =>
    JSON.stringify({
      providerId: formState.providerId,
      fhirUuid: formState.fhirUuid,
      professionalProfileCode: formState.professionalProfileCode,
      active: !!formState.active,
      lastName: formState.lastName,
      firstName: formState.firstName,
      telephone: formState.telephone,
      fax: formState.fax,
      email: formState.email,
      dni: formState.dni,
      specialty: formState.specialty,
      professionalInitials: formState.professionalInitials,
      profileFieldValues: dynamicFieldValues,
    });

  const handleAddProvider = () => {
    setHasAttemptedDynamicSave(true);
    if (!validateDynamicFieldsBeforeSave()) {
      return;
    }

    postToOpenElisServerFullResponse(
      "/rest/Provider/FhirUuid?fhirUuid=",
      buildProviderPayload(),
      (response) =>
        handleProviderSaveResponse(response, () => {
          closeAddModal();
        }),
    );
  };

  const handleUpdateProvider = () => {
    setHasAttemptedDynamicSave(true);
    if (!validateDynamicFieldsBeforeSave()) {
      return;
    }

    postToOpenElisServerFullResponse(
      `/rest/Provider/FhirUuid?fhirUuid=${currentProvider?.fhirUuid || ""}`,
      buildProviderPayload(),
      (response) =>
        handleProviderSaveResponse(response, () => {
          closeUpdateModal();
        }),
    );
  };

  const isFixedFieldInvalid = (fieldKey) => {
    if (!REQUIRED_FIXED_FIELDS.includes(fieldKey)) {
      return false;
    }
    const value = formState[fieldKey];
    return hasAttemptedDynamicSave && (value == null || String(value).trim() === "");
  };

  const getFixedFieldInvalidText = () =>
    intl.formatMessage({
      id: "provider.fixedField.required.inline",
      defaultMessage: "This field is required.",
    });

  const renderSpecialtyInput = () => {
    if (availableSpecialtyOptions.length > 0) {
      return (
        <Select
          id="specialty"
          labelText={intl.formatMessage({
            id: "provider.specialty.label",
            defaultMessage: "Specialty",
          })}
          value={formState.specialty || ""}
          invalid={isFixedFieldInvalid("specialty")}
          invalidText={getFixedFieldInvalidText()}
          onChange={(event) => updateFormState("specialty", event.target.value)}
        >
          <SelectItem value="" text="" />
          {availableSpecialtyOptions.map((option) => (
            <SelectItem
              key={`provider-specialty-option-${option.optionKey}`}
              value={option.optionKey}
              text={option.optionLabel}
            />
          ))}
        </Select>
      );
    }

    return (
      <TextInput
        id="specialty"
        labelText={intl.formatMessage({
          id: "provider.specialty.label",
          defaultMessage: "Specialty",
        })}
        value={formState.specialty}
        invalid={isFixedFieldInvalid("specialty")}
        invalidText={getFixedFieldInvalidText()}
        onChange={(event) => updateFormState("specialty", event.target.value)}
      />
    );
  };

  const renderDynamicField = (field) => {
    const currentValue = dynamicFieldValues[field.fieldKey];
    const labelText = field.displayName || field.fieldKey;
    const fieldType = normalizeFieldType(field.fieldType);
    const isRequiredInvalid =
      hasAttemptedDynamicSave &&
      field.required === true &&
      field.active !== false &&
      isDynamicFieldEmpty(field, currentValue);
    const numberInvalid =
      fieldType === "NUMBER" && !isNumericFieldValueValid(currentValue);
    const invalid = isRequiredInvalid || numberInvalid;
    const invalidText = isRequiredInvalid
      ? intl.formatMessage({
          id: "professionalProfile.fields.required.invalid.inline",
          defaultMessage: "This field is required.",
        })
      : intl.formatMessage({
          id: "professionalProfile.fields.number.invalid.inline",
          defaultMessage: "Only numbers and decimals are allowed.",
        });

    switch (fieldType) {
      case "NUMBER":
        return (
          <TextInput
            key={field.fieldKey}
            id={`dynamic-field-${field.fieldKey}`}
            type="text"
            inputMode="decimal"
            labelText={labelText}
            value={currentValue ?? ""}
            invalid={invalid}
            invalidText={invalidText}
            onKeyDown={(event) => {
              if (["-", "+", "e", "E"].includes(event.key)) {
                event.preventDefault();
              }
            }}
            onChange={(event) =>
              updateDynamicFieldValue(field.fieldKey, event.target.value)
            }
          />
        );
      case "DATE":
        return (
          <TextInput
            key={field.fieldKey}
            id={`dynamic-field-${field.fieldKey}`}
            type="date"
            labelText={labelText}
            value={currentValue ?? ""}
            invalid={invalid}
            invalidText={invalidText}
            onChange={(event) =>
              updateDynamicFieldValue(field.fieldKey, event.target.value)
            }
          />
        );
      case "SELECT":
        return (
          <Select
            key={field.fieldKey}
            id={`dynamic-field-${field.fieldKey}`}
            labelText={labelText}
            value={currentValue ?? ""}
            invalid={invalid}
            invalidText={invalidText}
            onChange={(event) =>
              updateDynamicFieldValue(field.fieldKey, event.target.value)
            }
          >
            <SelectItem value="" text="" />
            {field.options.map((option) => (
              <SelectItem
                key={`${field.fieldKey}-${option.optionKey}`}
                value={option.optionKey}
                text={option.optionLabel}
              />
            ))}
          </Select>
        );
      case "MULTISELECT": {
        const items = field.options.map((option) => ({
          id: option.optionKey,
          label: option.optionLabel,
        }));
        const selectedItems = items.filter((item) =>
          Array.isArray(currentValue) ? currentValue.includes(item.id) : false,
        );
        return (
          <div key={field.fieldKey} style={{ marginBottom: "1rem" }}>
            <label
              htmlFor={`dynamic-field-${field.fieldKey}`}
              style={{ display: "block", marginBottom: "0.5rem" }}
            >
              {labelText}
            </label>
            <MultiSelect
              id={`dynamic-field-${field.fieldKey}`}
              items={items}
              itemToString={(item) => item?.label || ""}
              selectedItems={selectedItems}
              onChange={({ selectedItems: nextSelectedItems }) =>
                updateDynamicFieldValue(
                  field.fieldKey,
                  nextSelectedItems.map((item) => item.id),
                )
              }
              label=""
              titleText=""
              selectionFeedback="top-after-reopen"
              invalid={invalid}
              invalidText={invalidText}
            />
          </div>
        );
      }
      case "BOOLEAN":
        return (
          <Checkbox
            key={field.fieldKey}
            id={`dynamic-field-${field.fieldKey}`}
            labelText={labelText}
            checked={!!currentValue}
            invalid={invalid}
            invalidText={invalidText}
            onChange={(_event, { checked }) =>
              updateDynamicFieldValue(field.fieldKey, !!checked)
            }
          />
        );
      case "TEXT":
      default:
        return (
          <TextInput
            key={field.fieldKey}
            id={`dynamic-field-${field.fieldKey}`}
            labelText={labelText}
            value={currentValue ?? ""}
            invalid={invalid}
            invalidText={invalidText}
            onChange={(event) =>
              updateDynamicFieldValue(field.fieldKey, event.target.value)
            }
          />
        );
    }
  };

  const renderCell = (cell, row) => {
    if (cell.info.header === "select") {
      return (
        <TableSelectRow
          key={cell.id}
          id={cell.id}
          checked={selectedRowIds.includes(row.id)}
          name="selectRowCheckbox"
          ariaLabel="selectRows"
          onSelect={(event) => {
            event.stopPropagation();
            if (selectedRowIds.includes(row.id)) {
              setSelectedRowIds(
                selectedRowIds.filter((selectedId) => selectedId !== row.id),
              );
            } else {
              setSelectedRowIds([...selectedRowIds, row.id]);
            }
          }}
        />
      );
    }
    if (cell.info.header === "active") {
      return <TableCell key={cell.id}>{String(cell.value)}</TableCell>;
    }
    return <TableCell key={cell.id}>{cell.value}</TableCell>;
  };

  const tableHeaders = useMemo(() => {
    const baseHeaders = [
      { key: "select", header: intl.formatMessage({ id: "provider.select" }) },
      {
        key: "lastName",
        header: intl.formatMessage({ id: "provider.providerLastName" }),
      },
      {
        key: "firstName",
        header: intl.formatMessage({ id: "provider.providerFirstName" }),
      },
      {
        key: "professionalProfileLabel",
        header: intl.formatMessage({ id: "provider.professional.profile" }),
      },
      {
        key: "active",
        header: intl.formatMessage({ id: "provider.isActive" }),
      },
      {
        key: "telephone",
        header: intl.formatMessage({ id: "provider.telephone" }),
      },
      { key: "dni", header: "DNI" },
      {
        key: "specialty",
        header: intl.formatMessage({
          id: "provider.specialty.label",
          defaultMessage: "Specialty",
        }),
      },
      {
        key: "professionalInitials",
        header: intl.formatMessage({
          id: "provider.initials.label",
          defaultMessage: "Initials",
        }),
      },
      {
        key: "email",
        header: intl.formatMessage({ id: "provider.email" }),
      },
    ];

    if (selectedProfileFilters.length !== 1) {
      return baseHeaders;
    }

    return [
      ...baseHeaders,
      ...selectedProfileTableFields.map((field) => ({
        key: `dynamic__${field.fieldKey}`,
        header: field.displayName || field.fieldKey,
      })),
    ];
  }, [intl, selectedProfileFilters, selectedProfileTableFields]);

  const filteredProviderRows = useMemo(() => {
    const selectedCodes = selectedProfileFilters.map((item) => item.id);
    if (selectedCodes.length === 0) {
      return providerMenuListShow;
    }
    const filteredRows = providerMenuListShow.filter((provider) =>
      selectedCodes.includes(provider.professionalProfileCode),
    );

    if (selectedCodes.length !== 1) {
      return filteredRows;
    }

    const selectedProfileCode = selectedCodes[0];
    return filteredRows.map((provider) => {
      const profileValues =
        provider.professionalProfileCode === selectedProfileCode
          ? provider.profileFieldValues || {}
          : {};

      const dynamicColumns = selectedProfileTableFields.reduce(
        (accumulator, field) => {
          const rawValue = profileValues[field.fieldKey];
          accumulator[`dynamic__${field.fieldKey}`] = formatDynamicFieldValue(
            field,
            rawValue,
          );
          return accumulator;
        },
        {},
      );

      return {
        ...provider,
        ...dynamicColumns,
      };
    });
  }, [providerMenuListShow, selectedProfileFilters, selectedProfileTableFields]);

  const pagedProviderRows = useMemo(
    () => filteredProviderRows.slice((page - 1) * pageSize, page * pageSize),
    [filteredProviderRows, page, pageSize],
  );

  useEffect(() => {
    setPage(1);
  }, [selectedProfileFilters]);

  useEffect(() => {
    if (selectedProfileFilters.length !== 1) {
      setSelectedProfileTableFields([]);
      return;
    }

    const profileCode = selectedProfileFilters[0]?.id || "";
    if (!profileCode) {
      setSelectedProfileTableFields([]);
      return;
    }

    getFromOpenElisServer(
      `/rest/providers/form-config?profileCode=${encodeURIComponent(profileCode)}`,
      (response) => {
        setSelectedProfileTableFields(
          normalizeDynamicFields(response?.fields || []).filter(
            (field) => field.fieldKey !== "PROFESSIONAL_INITIALS",
          ),
        );
      },
    );
  }, [selectedProfileFilters]);

  const isAnyModalOpen = isAddModalOpen || isUpdateModalOpen;

  if (loading) {
    return <Loading />;
  }

  return (
    <>
      {notificationVisible ? <AlertDialog /> : null}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <Grid fullWidth>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <Heading>
                <FormattedMessage id="provider.browse.title" />
              </Heading>
            </Section>
          </Column>
        </Grid>
        <br />

        <ActionPaginationButtonType
          selectedRowIds={selectedRowIds}
          modifyButton={modifyButton}
          deactivateButton={deactivateButton}
          deleteDeactivate={deleteDeactivateProvider}
          openUpdateModal={openUpdateModal}
          openAddModal={openAddModal}
          handlePreviousPage={handlePreviousPage}
          handleNextPage={handleNextPage}
          fromRecordCount={fromRecordCount}
          toRecordCount={toRecordCount}
          totalRecordCount={totalRecordCount}
          type="type1"
        />

        <br />
        <Button
          kind="ghost"
          size="sm"
          onClick={() =>
            window.location.assign("/MasterListsPage/profileManagement")
          }
        >
          <FormattedMessage
            id="professionalProfile.actions.manage"
            defaultMessage="Manage professional profiles"
          />
        </Button>
        <br />
        <br />

        {isAddModalOpen ? (
          <Modal
            open={isAddModalOpen}
            modalHeading={intl.formatMessage({
              id: "provider.modal.add",
              defaultMessage: "Add professional",
            })}
            primaryButtonText={intl.formatMessage({
              id: "provider.modal.add.action",
              defaultMessage: "Add",
            })}
            secondaryButtonText={intl.formatMessage({
              id: "provider.modal.cancel",
              defaultMessage: "Cancel",
            })}
            onRequestSubmit={handleAddProvider}
            onRequestClose={closeAddModal}
          >
            <Select
              id="professional-profile-code"
              labelText={intl.formatMessage({
                id: "provider.professional.profile",
              })}
              value={formState.professionalProfileCode}
              invalid={isFixedFieldInvalid("professionalProfileCode")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => handleProfileCodeChange(event.target.value)}
            >
              <SelectItem value="" text="" />
              {professionalProfileOptions.map((option) => (
                <SelectItem
                  key={`provider-profile-option-${option.code}`}
                  value={option.code}
                  text={option.label}
                />
              ))}
            </Select>
            <TextInput
              id="lastName"
              labelText={intl.formatMessage({ id: "provider.providerLastName" })}
              value={formState.lastName}
              invalid={isFixedFieldInvalid("lastName")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => updateFormState("lastName", event.target.value)}
              required
            />
            <TextInput
              id="firstName"
              labelText={intl.formatMessage({ id: "provider.providerFirstName" })}
              value={formState.firstName}
              invalid={isFixedFieldInvalid("firstName")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => updateFormState("firstName", event.target.value)}
              required
            />
            <TextInput
              id="telephone"
              labelText={intl.formatMessage({ id: "provider.telephone" })}
              value={formState.telephone}
              onChange={(event) => updateFormState("telephone", event.target.value)}
            />
            <TextInput
              id="dni"
              labelText="DNI"
              value={formState.dni}
              invalid={isFixedFieldInvalid("dni")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => updateFormState("dni", event.target.value)}
            />
            <TextInput
              id="professionalInitials"
              labelText={intl.formatMessage({
                id: "provider.initials.label",
                defaultMessage: "Initials",
              })}
              value={formState.professionalInitials}
              invalid={isFixedFieldInvalid("professionalInitials")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) =>
                updateFormState("professionalInitials", event.target.value)
              }
            />
            {renderSpecialtyInput()}
            <TextInput
              id="fax"
              labelText={intl.formatMessage({ id: "provider.fax" })}
              value={formState.fax}
              onChange={(event) => updateFormState("fax", event.target.value)}
            />
            <TextInput
              id="email"
              type="email"
              labelText={intl.formatMessage({ id: "provider.email" })}
              value={formState.email}
              onChange={(event) => updateFormState("email", event.target.value)}
            />
            <Select
              id="isActive"
              labelText={intl.formatMessage({ id: "provider.isActive" })}
              value={formState.active ? "yes" : "no"}
              onChange={(event) =>
                updateFormState("active", event.target.value === "yes")
              }
            >
              <SelectItem
                value="yes"
                text={intl.formatMessage({ id: "label.yes", defaultMessage: "Yes" })}
              />
              <SelectItem
                value="no"
                text={intl.formatMessage({ id: "label.no", defaultMessage: "No" })}
              />
            </Select>
            {dynamicFields.map(renderDynamicField)}
          </Modal>
        ) : null}

        {isUpdateModalOpen ? (
          <Modal
            open={isUpdateModalOpen}
            modalHeading={intl.formatMessage({
              id: "provider.modal.update",
              defaultMessage: "Update professional",
            })}
            primaryButtonText={intl.formatMessage({
              id: "provider.modal.update.action",
              defaultMessage: "Update",
            })}
            secondaryButtonText={intl.formatMessage({
              id: "provider.modal.cancel",
              defaultMessage: "Cancel",
            })}
            onRequestSubmit={handleUpdateProvider}
            onRequestClose={closeUpdateModal}
          >
            <Select
              id="professional-profile-code-update"
              labelText={intl.formatMessage({
                id: "provider.professional.profile",
              })}
              value={formState.professionalProfileCode}
              invalid={isFixedFieldInvalid("professionalProfileCode")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => handleProfileCodeChange(event.target.value)}
            >
              <SelectItem value="" text="" />
              {professionalProfileOptions.map((option) => (
                <SelectItem
                  key={`provider-profile-update-option-${option.code}`}
                  value={option.code}
                  text={option.label}
                />
              ))}
            </Select>
            <TextInput
              id="lastName-update"
              labelText={intl.formatMessage({ id: "provider.providerLastName" })}
              value={formState.lastName}
              invalid={isFixedFieldInvalid("lastName")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => updateFormState("lastName", event.target.value)}
              required
            />
            <TextInput
              id="firstName-update"
              labelText={intl.formatMessage({ id: "provider.providerFirstName" })}
              value={formState.firstName}
              invalid={isFixedFieldInvalid("firstName")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => updateFormState("firstName", event.target.value)}
              required
            />
            <TextInput
              id="telephone-update"
              labelText={intl.formatMessage({ id: "provider.telephone" })}
              value={formState.telephone}
              onChange={(event) => updateFormState("telephone", event.target.value)}
            />
            <TextInput
              id="dni-update"
              labelText="DNI"
              value={formState.dni}
              invalid={isFixedFieldInvalid("dni")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) => updateFormState("dni", event.target.value)}
            />
            <TextInput
              id="professionalInitials-update"
              labelText={intl.formatMessage({
                id: "provider.initials.label",
                defaultMessage: "Initials",
              })}
              value={formState.professionalInitials}
              invalid={isFixedFieldInvalid("professionalInitials")}
              invalidText={getFixedFieldInvalidText()}
              onChange={(event) =>
                updateFormState("professionalInitials", event.target.value)
              }
            />
            {renderSpecialtyInput()}
            <TextInput
              id="fax-update"
              labelText={intl.formatMessage({ id: "provider.fax" })}
              value={formState.fax}
              onChange={(event) => updateFormState("fax", event.target.value)}
            />
            <TextInput
              id="email-update"
              type="email"
              labelText={intl.formatMessage({ id: "provider.email" })}
              value={formState.email}
              onChange={(event) => updateFormState("email", event.target.value)}
            />
            <Select
              id="isActive-update"
              labelText={intl.formatMessage({ id: "provider.isActive" })}
              value={formState.active ? "yes" : "no"}
              onChange={(event) =>
                updateFormState("active", event.target.value === "yes")
              }
            >
              <SelectItem
                value="yes"
                text={intl.formatMessage({ id: "label.yes", defaultMessage: "Yes" })}
              />
              <SelectItem
                value="no"
                text={intl.formatMessage({ id: "label.no", defaultMessage: "No" })}
              />
            </Select>
            {dynamicFields.map(renderDynamicField)}
          </Modal>
        ) : null}

        <div className="orderLegendBody">
          <Grid>
            <Column lg={10} md={8} sm={4}>
              <Section>
                <Search
                  size="lg"
                  id="provider-search-bar"
                  labelText={<FormattedMessage id="provider.search" />}
                  placeholder={intl.formatMessage({
                    id: "provider.search.placeholder",
                  })}
                  onChange={handlePanelSearchChange}
                  value={panelSearchTerm || ""}
                />
              </Section>
            </Column>
            <Column lg={6} md={8} sm={4}>
              <div style={{ marginTop: "1rem" }}>
                <label
                  htmlFor="provider-profile-filter"
                  style={{ display: "block", marginBottom: "0.5rem" }}
                >
                  {intl.formatMessage({
                    id: "provider.profile.filter",
                    defaultMessage: "Professional profile filter",
                  })}
                </label>
                <MultiSelect
                  id="provider-profile-filter"
                  items={profileFilterItems}
                  itemToString={(item) => item?.label || ""}
                  selectedItems={selectedProfileFilters}
                  onChange={({ selectedItems }) =>
                    setSelectedProfileFilters(selectedItems)
                  }
                  label={intl.formatMessage({
                    id: "provider.profile.filter.placeholder",
                    defaultMessage: "All professional profiles",
                  })}
                  titleText=""
                  selectionFeedback="top-after-reopen"
                />
              </div>
            </Column>
          </Grid>
          <br />

          {!isAnyModalOpen ? (
            <Grid fullWidth className="gridBoundary">
              <Column lg={16} md={8} sm={4}>
                <DataTable rows={pagedProviderRows} headers={tableHeaders}>
                  {({ rows, headers, getHeaderProps, getTableProps }) => (
                    <TableContainer>
                      <Table {...getTableProps()}>
                        <TableHead>
                          <TableRow>
                            {headers.map((header) => (
                              <TableHeader
                                key={header.key}
                                {...getHeaderProps({ header })}
                              >
                                {header.header}
                              </TableHeader>
                            ))}
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {rows.map((row) => (
                            <TableRow
                              key={row.id}
                              onClick={() => {
                                const id = row.id;
                                setSelectedRowIds(
                                  selectedRowIds.includes(id)
                                    ? selectedRowIds.filter(
                                        (selectedId) => selectedId !== id,
                                      )
                                    : [...selectedRowIds, id],
                                );
                              }}
                            >
                              {row.cells.map((cell) => renderCell(cell, row))}
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                </DataTable>
                <Pagination
                  onChange={handlePageChange}
                  page={page}
                  pageSize={pageSize}
                  pageSizes={[10, 20]}
                  totalItems={filteredProviderRows.length}
                  forwardText={intl.formatMessage({ id: "pagination.forward" })}
                  backwardText={intl.formatMessage({ id: "pagination.backward" })}
                  itemRangeText={(min, max, total) =>
                    intl.formatMessage(
                      { id: "pagination.item-range" },
                      { min, max, total },
                    )
                  }
                  itemsPerPageText={intl.formatMessage({
                    id: "pagination.items-per-page",
                  })}
                  itemText={(min, max) =>
                    intl.formatMessage(
                      { id: "pagination.item" },
                      { min, max },
                    )
                  }
                  pageNumberText={intl.formatMessage({
                    id: "pagination.page-number",
                  })}
                  pageRangeText={(_current, total) =>
                    intl.formatMessage(
                      { id: "pagination.page-range" },
                      { total },
                    )
                  }
                  pageText={(currentPage, pagesUnknown) =>
                    intl.formatMessage(
                      { id: "pagination.page" },
                      { page: pagesUnknown ? "" : currentPage },
                    )
                  }
                />
              </Column>
            </Grid>
          ) : null}
        </div>
      </div>
    </>
  );
}

export default ProviderMenu;
