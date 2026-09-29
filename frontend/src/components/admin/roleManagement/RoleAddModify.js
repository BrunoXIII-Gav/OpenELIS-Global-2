import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Button,
  Checkbox,
  Column,
  Form,
  FormGroup,
  Grid,
  Heading,
  Loading,
  MultiSelect,
  RadioButton,
  RadioButtonGroup,
  Section,
  TextArea,
  TextInput,
} from "@carbon/react";
import { useHistory, useLocation } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { NotificationContext } from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils.js";

const HIDDEN_APPLICABLE_LAB_UNIT_NAMES = new Set([
  "Biochemistry",
  "Cytology",
  "Hematology",
  "Hemato-Immunology",
  "Histopathology",
  "Immunohistochemistry",
  "Immunology",
  "Microbiology",
  "Molecular Biology",
  "Parasitology",
  "Pathology",
  "Serology",
  "Serology-Immunology",
  "Urinalysis",
  "Virology",
]);

// These actions do not yet control a distinct user-facing operation.
// Keep their backend keys for existing roles, but do not offer them in the editor.
const HIDDEN_OPERATION_ACTIONS_BY_MODULE = {
  "sample-management": new Set(["aliquot", "print", "export"]),
  orders: new Set(["cancel"]),
  patients: new Set(["merge", "export"]),
  results: new Set(["export"]),
  validation: new Set(["revoke"]),
};

function RoleAddModify() {
  const history = useHistory();
  const location = useLocation();
  const intl = useIntl();
  const { addNotification, notificationVisible, setNotificationVisible } =
    useContext(NotificationContext);
  const [loading, setLoading] = useState(true);
  const [catalog, setCatalog] = useState({
    modules: [],
    labUnits: [],
    fieldTagsByModule: {},
    patientSearchCriteria: [],
  });
  const [formData, setFormData] = useState({
    id: "",
    name: "",
    description: "",
    modulePermissions: [],
    restrictedPatientSearchCriteria: [],
  });
  const [selectedModuleKey, setSelectedModuleKey] = useState("");

  const roleId = useMemo(() => {
    const search = new URLSearchParams(location.search);
    return search.get("ID") || "0";
  }, [location.search]);

  const breadcrumbs = [
    { label: "home.label", link: "/" },
    { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
    {
      label: "customRole.page.title",
      link: "/MasterListsPage/roleManagement",
    },
    {
      label: roleId === "0" ? "customRole.add.title" : "customRole.edit.title",
      link: `/MasterListsPage/roleEdit?ID=${roleId}`,
    },
  ];

  useEffect(() => {
    setLoading(true);
    getFromOpenElisServer("/rest/custom-roles/catalog", (response) => {
      const modules = Array.isArray(response?.moduleCatalog?.modules)
        ? response.moduleCatalog.modules
        : [];
      setCatalog({
        modules,
        labUnits: Array.isArray(response?.labUnits) ? response.labUnits : [],
        fieldTagsByModule: response?.fieldTagsByModule || {},
        patientSearchCriteria: Array.isArray(response?.patientSearchCriteria)
          ? response.patientSearchCriteria
          : [],
      });
      setSelectedModuleKey((previousModuleKey) =>
        modules.some((module) => module.key === previousModuleKey)
          ? previousModuleKey
          : modules[0]?.key || "",
      );

      if (roleId === "0") {
        setLoading(false);
        return;
      }

      getFromOpenElisServer(`/rest/custom-roles/${roleId}`, (roleResponse) => {
        setFormData({
          id: roleResponse?.id || roleId,
          name: roleResponse?.name || "",
          description: roleResponse?.description || "",
          modulePermissions: Array.isArray(roleResponse?.modulePermissions)
            ? roleResponse.modulePermissions
            : [],
          restrictedPatientSearchCriteria: Array.isArray(
            roleResponse?.restrictedPatientSearchCriteria,
          )
            ? roleResponse.restrictedPatientSearchCriteria
            : [],
        });
        setLoading(false);
      });
    });
  }, [roleId]);

  const visibleLabUnits = catalog.labUnits.filter(
    (labUnit) =>
      !HIDDEN_APPLICABLE_LAB_UNIT_NAMES.has(String(labUnit?.name || "").trim()),
  );

  const getModulePermission = (moduleKey) =>
    formData.modulePermissions.find(
      (modulePermission) => modulePermission.moduleKey === moduleKey,
    );

  const toggleAction = (module, actionKey) => {
    setFormData((previousFormData) => {
      const existingPermission = previousFormData.modulePermissions.find(
        (modulePermission) => modulePermission.moduleKey === module.key,
      );
      const actionKeys = existingPermission?.actionKeys || [];
      const nextActionKeys = actionKeys.includes(actionKey)
        ? actionKeys.filter((selectedAction) => selectedAction !== actionKey)
        : [...actionKeys, actionKey];

      if (nextActionKeys.length === 0) {
        return {
          ...previousFormData,
          modulePermissions: previousFormData.modulePermissions.filter(
            (modulePermission) => modulePermission.moduleKey !== module.key,
          ),
        };
      }

      const nextModulePermission = {
        ...existingPermission,
        moduleKey: module.key,
        actionKeys: nextActionKeys,
        allLabUnits:
          module.key === "administration" ||
          existingPermission?.allLabUnits ||
          false,
        labUnitIds: existingPermission?.labUnitIds || [],
      };
      return {
        ...previousFormData,
        modulePermissions: existingPermission
          ? previousFormData.modulePermissions.map((modulePermission) =>
              modulePermission.moduleKey === module.key
                ? nextModulePermission
                : modulePermission,
            )
          : [...previousFormData.modulePermissions, nextModulePermission],
      };
    });
  };

  const setLabUnits = (moduleKey, labUnitIds) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      modulePermissions: previousFormData.modulePermissions.map(
        (modulePermission) => {
          if (modulePermission.moduleKey !== moduleKey) {
            return modulePermission;
          }
          return {
            ...modulePermission,
            labUnitIds,
          };
        },
      ),
    }));
  };

  const setFieldRestrictions = (moduleKey, restrictedFieldGroupKeys) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      modulePermissions: previousFormData.modulePermissions.map(
        (modulePermission) => {
          if (modulePermission.moduleKey !== moduleKey) {
            return modulePermission;
          }
          return {
            ...modulePermission,
            restrictedFieldGroupKeys,
          };
        },
      ),
    }));
  };

  const setFieldTagRestrictions = (moduleKey, restrictedFieldTagKeys) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      modulePermissions: previousFormData.modulePermissions.map(
        (modulePermission) => {
          if (modulePermission.moduleKey !== moduleKey) {
            return modulePermission;
          }
          return {
            ...modulePermission,
            restrictedFieldTagKeys,
          };
        },
      ),
    }));
  };

  const setPatientSearchRestrictions = (selectedItems) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      restrictedPatientSearchCriteria: selectedItems.map((criterion) =>
        typeof criterion === "string" ? criterion : criterion.key,
      ),
    }));
  };

  const formatCatalogLabel = (item, fallback) =>
    item?.labelKey
      ? intl.formatMessage({ id: item.labelKey, defaultMessage: fallback })
      : fallback;

  const formatFieldTagLabel = (fieldTag) => {
    const label = formatCatalogLabel(fieldTag, fieldTag.label || fieldTag.key);
    return fieldTag.key?.startsWith("additional-field-")
      ? `${intl.formatMessage({ id: "customRole.modules.additionalField" })}: ${label}`
      : label;
  };

  const showError = async (response) => {
    let message = intl.formatMessage({ id: "server.error.msg" });
    if (response && typeof response.json === "function") {
      try {
        const payload = await response.json();
        message = payload?.message || message;
      } catch (_error) {
        // Ignore a non-JSON response and retain the generic message.
      }
    } else if (response?.message) {
      message = response.message;
    }
    addNotification({
      kind: NotificationKinds.error,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
    setNotificationVisible(true);
    setLoading(false);
  };

  const handleSave = () => {
    setLoading(true);
    const payload = JSON.stringify({
      id: formData.id || undefined,
      name: formData.name,
      description: formData.description,
      modulePermissions: formData.modulePermissions,
      restrictedPatientSearchCriteria: formData.restrictedPatientSearchCriteria,
    });
    const handleSuccess = () => {
      addNotification({
        kind: NotificationKinds.success,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "customRole.save.success" }),
      });
      setNotificationVisible(true);
      history.push("/MasterListsPage/roleManagement");
    };

    if (roleId === "0") {
      postToOpenElisServerJsonResponse(
        "/rest/custom-roles",
        payload,
        (response) => {
          if (response?.id) {
            handleSuccess();
            return;
          }
          showError(response);
        },
      );
      return;
    }

    putToOpenElisServerFullResponse(
      `/rest/custom-roles/${roleId}`,
      payload,
      async (response) => {
        if (response.ok) {
          handleSuccess();
          return;
        }
        showError(response);
      },
    );
  };

  if (loading) {
    return <Loading />;
  }

  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              <FormattedMessage
                id={
                  roleId === "0"
                    ? "customRole.add.title"
                    : "customRole.edit.title"
                }
              />
            </Heading>
          </Section>
          <br />
          {notificationVisible === true ? <AlertDialog /> : ""}
          <Form
            onSubmit={(event) => {
              event.preventDefault();
              handleSave();
            }}
          >
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="custom-role-name"
                  labelText={intl.formatMessage({
                    id: "customRole.fields.name",
                  })}
                  value={formData.name}
                  onChange={(event) =>
                    setFormData((previousFormData) => ({
                      ...previousFormData,
                      name: event.target.value,
                    }))
                  }
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextArea
                  id="custom-role-description"
                  labelText={intl.formatMessage({
                    id: "customRole.fields.description",
                  })}
                  value={formData.description}
                  onChange={(event) =>
                    setFormData((previousFormData) => ({
                      ...previousFormData,
                      description: event.target.value,
                    }))
                  }
                />
              </Column>
            </Grid>
            <br />
            <Heading>
              <FormattedMessage id="customRole.modules.title" />
            </Heading>
            <p>
              <FormattedMessage id="customRole.modules.help" />
            </p>
            <Grid fullWidth narrow>
              <Column lg={4} md={2} sm={4}>
                <RadioButtonGroup
                  legendText={intl.formatMessage({
                    id: "customRole.modules.selector",
                  })}
                  name="selected-module"
                  orientation="vertical"
                  valueSelected={selectedModuleKey}
                  onChange={setSelectedModuleKey}
                >
                  {catalog.modules.map((module) => (
                    <RadioButton
                      key={module.key}
                      id={`module-selector-${module.key}`}
                      value={module.key}
                      labelText={formatCatalogLabel(module, module.key)}
                    />
                  ))}
                </RadioButtonGroup>
              </Column>
              <Column lg={12} md={6} sm={4}>
                {catalog.modules
                  .filter((module) => module.key === selectedModuleKey)
                  .map((module) => {
                    const modulePermission = getModulePermission(module.key);
                    const hasActions = Boolean(
                      modulePermission?.actionKeys?.length,
                    );
                    const requiresLabUnits = module.key !== "administration";
                    const readAction = module.actions.find(
                      (action) => action.key === "read",
                    );
                    const hiddenOperationActions =
                      HIDDEN_OPERATION_ACTIONS_BY_MODULE[module.key] ||
                      new Set();
                    const operationActions = module.actions.filter(
                      (action) =>
                        action.key !== "read" &&
                        !hiddenOperationActions.has(action.key),
                    );
                    const fieldGroups = module.fieldGroups || [];
                    const fieldTags =
                      catalog.fieldTagsByModule[module.key] || [];
                    const selectedGroups = fieldGroups.filter((fieldGroup) =>
                      modulePermission?.restrictedFieldGroupKeys?.includes(
                        fieldGroup.key,
                      ),
                    );
                    const selectedTags = fieldTags.filter((fieldTag) =>
                      modulePermission?.restrictedFieldTagKeys?.includes(
                        fieldTag.key,
                      ),
                    );
                    const restrictionCount =
                      selectedGroups.length + selectedTags.length;
                    const selectedPatientSearchCriteria =
                      catalog.patientSearchCriteria.filter((criterion) =>
                        formData.restrictedPatientSearchCriteria.includes(
                          criterion.key,
                        ),
                      );
                    return (
                      <Section key={module.key}>
                        <Heading>
                          {formatCatalogLabel(module, module.key)}
                        </Heading>
                        {readAction ? (
                          <FormGroup
                            legendId={`module-access-${module.key}`}
                            legendText={intl.formatMessage({
                              id: "customRole.modules.access",
                            })}
                          >
                            <Checkbox
                              id={`${module.key}-${readAction.key}`}
                              labelText={formatCatalogLabel(
                                readAction,
                                readAction.key,
                              )}
                              checked={
                                modulePermission?.actionKeys?.includes(
                                  readAction.key,
                                ) || false
                              }
                              onChange={() =>
                                toggleAction(module, readAction.key)
                              }
                            />
                          </FormGroup>
                        ) : null}
                        {operationActions.length > 0 ? (
                          <FormGroup
                            legendId={`module-operations-${module.key}`}
                            legendText={intl.formatMessage({
                              id: "customRole.modules.operations",
                            })}
                          >
                            {operationActions.map((action) => (
                              <Checkbox
                                key={`${module.key}-${action.key}`}
                                id={`${module.key}-${action.key}`}
                                labelText={formatCatalogLabel(
                                  action,
                                  action.key,
                                )}
                                checked={
                                  modulePermission?.actionKeys?.includes(
                                    action.key,
                                  ) || false
                                }
                                onChange={() =>
                                  toggleAction(module, action.key)
                                }
                              />
                            ))}
                          </FormGroup>
                        ) : null}
                        {requiresLabUnits && hasActions ? (
                          <MultiSelect
                            id={`module-lab-units-${module.key}`}
                            titleText={intl.formatMessage({
                              id: "customRole.modules.scope",
                            })}
                            label={intl.formatMessage({
                              id: "customRole.modules.labUnits.placeholder",
                            })}
                            items={visibleLabUnits}
                            itemToString={(labUnit) => labUnit?.name || ""}
                            selectedItems={visibleLabUnits.filter((labUnit) =>
                              modulePermission?.labUnitIds?.includes(
                                labUnit.id,
                              ),
                            )}
                            onChange={({ selectedItems }) =>
                              setLabUnits(
                                module.key,
                                selectedItems.map((labUnit) => labUnit.id),
                              )
                            }
                          />
                        ) : null}
                        {!requiresLabUnits && hasActions ? (
                          <FormGroup
                            legendId={`module-scope-${module.key}`}
                            legendText={intl.formatMessage({
                              id: "customRole.modules.scope",
                            })}
                          >
                            <p className="cds--form__helper-text">
                              <FormattedMessage id="customRole.modules.globalScope" />
                            </p>
                          </FormGroup>
                        ) : null}
                        <FormGroup
                          legendId={`module-data-${module.key}`}
                          legendText={intl.formatMessage({
                            id: "customRole.modules.data",
                          })}
                        >
                          {fieldGroups.length > 0 ? (
                            <MultiSelect
                              id={`module-field-groups-${module.key}`}
                              titleText={intl.formatMessage({
                                id: "customRole.modules.fieldRestrictions",
                              })}
                              label={intl.formatMessage({
                                id: "customRole.modules.fieldRestrictions.placeholder",
                              })}
                              items={fieldGroups}
                              itemToString={(fieldGroup) =>
                                formatCatalogLabel(
                                  fieldGroup,
                                  fieldGroup?.key || "",
                                )
                              }
                              selectedItems={selectedGroups}
                              disabled={!hasActions}
                              onChange={({ selectedItems }) =>
                                setFieldRestrictions(
                                  module.key,
                                  selectedItems.map(
                                    (fieldGroup) => fieldGroup.key,
                                  ),
                                )
                              }
                            />
                          ) : null}
                          {fieldTags.length > 0 ? (
                            <MultiSelect
                              id={`module-field-tags-${module.key}`}
                              titleText={intl.formatMessage({
                                id: "customRole.modules.fieldTagRestrictions",
                              })}
                              label={intl.formatMessage({
                                id: "customRole.modules.fieldTagRestrictions.placeholder",
                              })}
                              items={fieldTags}
                              itemToString={formatFieldTagLabel}
                              selectedItems={selectedTags}
                              disabled={!hasActions}
                              onChange={({ selectedItems }) =>
                                setFieldTagRestrictions(
                                  module.key,
                                  selectedItems.map((fieldTag) => fieldTag.key),
                                )
                              }
                            />
                          ) : null}
                          {fieldGroups.length === 0 &&
                          fieldTags.length === 0 ? (
                            <p>
                              <FormattedMessage id="customRole.modules.data.empty" />
                            </p>
                          ) : (
                            <p className="cds--form__helper-text">
                              {intl.formatMessage(
                                { id: "customRole.modules.data.summary" },
                                { count: restrictionCount },
                              )}
                            </p>
                          )}
                        </FormGroup>
                        {module.key === "patients" ? (
                          <FormGroup
                            legendId="patient-search-data"
                            legendText={intl.formatMessage({
                              id: "customRole.patientSearch.title",
                            })}
                          >
                            <MultiSelect
                              id="patient-search-restrictions"
                              titleText={intl.formatMessage({
                                id: "customRole.patientSearch.restrictions",
                              })}
                              label={intl.formatMessage({
                                id: "customRole.patientSearch.placeholder",
                              })}
                              items={catalog.patientSearchCriteria}
                              itemToString={(criterion) =>
                                formatCatalogLabel(
                                  criterion,
                                  criterion?.key || "",
                                )
                              }
                              selectedItems={selectedPatientSearchCriteria}
                              onChange={({ selectedItems }) =>
                                setPatientSearchRestrictions(selectedItems)
                              }
                            />
                            <p className="cds--form__helper-text">
                              <FormattedMessage id="customRole.patientSearch.help" />
                            </p>
                          </FormGroup>
                        ) : null}
                      </Section>
                    );
                  })}
              </Column>
            </Grid>
            <br />
            <Button type="submit" disabled={!formData.name.trim()}>
              <FormattedMessage id="label.button.save" />
            </Button>
            <Button
              type="button"
              kind="tertiary"
              onClick={() => history.push("/MasterListsPage/roleManagement")}
            >
              <FormattedMessage id="label.button.exit" />
            </Button>
          </Form>
        </Column>
      </Grid>
    </div>
  );
}

export default RoleAddModify;
