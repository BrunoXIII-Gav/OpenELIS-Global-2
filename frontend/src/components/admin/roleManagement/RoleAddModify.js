import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Accordion,
  AccordionItem,
  Button,
  Checkbox,
  Column,
  Form,
  FormGroup,
  Grid,
  Heading,
  Loading,
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

const HIDDEN_GLOBAL_PERMISSION_NAMES = new Set([
  "Audit Trail",
  "User Account Administrator",
  "Analyser Import",
  "Cytopathologist",
  "Pathologist",
]);

const HIDDEN_LAB_PERMISSION_NAMES = new Set(["Aliquot", "Reports"]);
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

const LAB_PERMISSION_GROUPS = [
  { parentName: "Generic Sample", childNames: ["Sample Management"] },
  { parentName: "Order", childNames: ["Order Add", "Order Edit"] },
  {
    parentName: "Patient",
    childNames: ["Patient Management", "Patient History"],
  },
  { parentName: "Storage", childNames: ["Storage Management"] },
  {
    parentName: "Results",
    childNames: ["Results By Unit", "Results By Patient", "Results By Order"],
  },
  {
    parentName: "Validation",
    childNames: ["Validation Routine", "Validation By Order"],
  },
  { parentName: "Reports", childNames: [] },
];

const LAB_PERMISSION_LABELS = {
  Administration: {
    id: "customRole.globalPermission.administration",
    defaultMessage: "Administration",
  },
  "Generic Sample": {
    id: "customRole.labPermission.genericSample",
    defaultMessage: "Generic Sample",
  },
  "Sample Management": {
    id: "customRole.labPermission.sampleManagement",
    defaultMessage: "Sample Management",
  },
  Order: {
    id: "customRole.labPermission.order",
    defaultMessage: "Order",
  },
  "Order Add": {
    id: "customRole.labPermission.orderAdd",
    defaultMessage: "Order Add",
  },
  "Order Edit": {
    id: "customRole.labPermission.orderEdit",
    defaultMessage: "Order Edit",
  },
  Patient: {
    id: "customRole.labPermission.patient",
    defaultMessage: "Patient",
  },
  "Patient Management": {
    id: "customRole.labPermission.patientManagement",
    defaultMessage: "Patient Management",
  },
  "Patient History": {
    id: "customRole.labPermission.patientHistory",
    defaultMessage: "Patient History",
  },
  Storage: {
    id: "customRole.labPermission.storage",
    defaultMessage: "Storage",
  },
  "Storage Management": {
    id: "customRole.labPermission.storageManagement",
    defaultMessage: "Storage Management",
  },
  Results: {
    id: "customRole.labPermission.results",
    defaultMessage: "Results",
  },
  "Results By Unit": {
    id: "customRole.labPermission.resultsByUnit",
    defaultMessage: "Results By Unit",
  },
  "Results By Patient": {
    id: "customRole.labPermission.resultsByPatient",
    defaultMessage: "Results By Patient",
  },
  "Results By Order": {
    id: "customRole.labPermission.resultsByOrder",
    defaultMessage: "Results By Order",
  },
  Validation: {
    id: "customRole.labPermission.validation",
    defaultMessage: "Validation",
  },
  "Validation Routine": {
    id: "customRole.labPermission.validationRoutine",
    defaultMessage: "Validation Routine",
  },
  "Validation By Order": {
    id: "customRole.labPermission.validationByOrder",
    defaultMessage: "Validation By Order",
  },
  Reports: {
    id: "customRole.labPermission.reports",
    defaultMessage: "Reports",
  },
};

function RoleAddModify() {
  const history = useHistory();
  const location = useLocation();
  const intl = useIntl();
  const { addNotification, notificationVisible, setNotificationVisible } =
    useContext(NotificationContext);
  const [loading, setLoading] = useState(true);
  const [catalog, setCatalog] = useState({
    globalPermissionRoles: [],
    labPermissionRoles: [],
    labUnits: [],
  });
  const [formData, setFormData] = useState({
    id: "",
    name: "",
    description: "",
    permissionRoleIds: [],
    applicableLabUnitIds: [],
  });

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

  const getLabPermissionLabel = (roleName) => {
    const normalizedRoleName = String(roleName || "").trim();
    const translation = LAB_PERMISSION_LABELS[normalizedRoleName];
    return translation
      ? intl.formatMessage(translation)
      : normalizedRoleName || roleName;
  };

  const isParentGroupChecked = (parentRole, childRoles = []) => {
    if (!parentRole) {
      return false;
    }

    return (
      formData.permissionRoleIds.includes(parentRole.id) ||
      childRoles.some((childRole) => formData.permissionRoleIds.includes(childRole.id))
    );
  };

  useEffect(() => {
    setLoading(true);
    getFromOpenElisServer("/rest/custom-roles/catalog", (response) => {
      setCatalog({
        globalPermissionRoles: Array.isArray(response?.globalPermissionRoles)
          ? response.globalPermissionRoles
          : [],
        labPermissionRoles: Array.isArray(response?.labPermissionRoles)
          ? response.labPermissionRoles
          : [],
        labUnits: Array.isArray(response?.labUnits) ? response.labUnits : [],
      });

      if (roleId === "0") {
        setLoading(false);
        return;
      }

      getFromOpenElisServer(`/rest/custom-roles/${roleId}`, (roleResponse) => {
        setFormData({
          id: roleResponse?.id || roleId,
          name: roleResponse?.name || "",
          description: roleResponse?.description || "",
          permissionRoleIds: Array.isArray(roleResponse?.permissionRoleIds)
            ? roleResponse.permissionRoleIds
            : [],
          applicableLabUnitIds: Array.isArray(roleResponse?.applicableLabUnitIds)
            ? roleResponse.applicableLabUnitIds
            : [],
        });
        setLoading(false);
      });
    });
  }, [roleId]);

  const updatePermissionRole = (permissionRoleId) => {
    setFormData((previousFormData) => {
      const selectedRoleIds = new Set(previousFormData.permissionRoleIds);
      const exists = selectedRoleIds.has(permissionRoleId);
      const clickedRole = visibleLabPermissionRoles.find(
        (permissionRole) => permissionRole.id === permissionRoleId,
      );

      if (!clickedRole) {
        return {
          ...previousFormData,
          permissionRoleIds: exists
            ? previousFormData.permissionRoleIds.filter(
                (role) => role !== permissionRoleId,
              )
            : [...previousFormData.permissionRoleIds, permissionRoleId],
        };
      }

      const clickedRoleName = String(clickedRole?.name || "").trim();
      const parentGroup = LAB_PERMISSION_GROUPS.find(
        ({ parentName }) => parentName === clickedRoleName,
      );

      if (parentGroup) {
        const isParentGroupSelected =
          selectedRoleIds.has(permissionRoleId) ||
          parentGroup.childNames
            .map((roleName) =>
              visibleLabPermissionRoles.find(
                (permissionRole) =>
                  String(permissionRole?.name || "").trim() === roleName,
              ),
            )
            .filter(Boolean)
            .some((permissionRole) => selectedRoleIds.has(permissionRole.id));
        const relatedRoles = [parentGroup.parentName, ...parentGroup.childNames]
          .map((roleName) =>
            visibleLabPermissionRoles.find(
              (permissionRole) =>
                String(permissionRole?.name || "").trim() === roleName,
            ),
          )
          .filter(Boolean);

        if (isParentGroupSelected) {
          relatedRoles.forEach((permissionRole) =>
            selectedRoleIds.delete(permissionRole.id),
          );
        } else {
          relatedRoles.forEach((permissionRole) =>
            selectedRoleIds.add(permissionRole.id),
          );
        }

        return {
          ...previousFormData,
          permissionRoleIds: Array.from(selectedRoleIds),
        };
      }

      const childGroup = LAB_PERMISSION_GROUPS.find(({ childNames }) =>
        childNames.includes(clickedRoleName),
      );

      if (childGroup) {
        const parentRole = visibleLabPermissionRoles.find(
          (permissionRole) =>
            String(permissionRole?.name || "").trim() === childGroup.parentName,
        );
        const siblingRoles = childGroup.childNames
          .map((roleName) =>
            visibleLabPermissionRoles.find(
              (permissionRole) =>
                String(permissionRole?.name || "").trim() === roleName,
            ),
          )
          .filter(Boolean);

        if (exists) {
          selectedRoleIds.delete(permissionRoleId);
          const hasAnotherSelectedSibling = siblingRoles.some(
            (permissionRole) =>
              permissionRole.id !== permissionRoleId &&
              selectedRoleIds.has(permissionRole.id),
          );

          if (!hasAnotherSelectedSibling && parentRole) {
            selectedRoleIds.delete(parentRole.id);
          }
        } else {
          if (parentRole) {
            selectedRoleIds.add(parentRole.id);
          }
          selectedRoleIds.add(permissionRoleId);
        }

        return {
          ...previousFormData,
          permissionRoleIds: Array.from(selectedRoleIds),
        };
      }

      return {
        ...previousFormData,
        permissionRoleIds: exists
          ? previousFormData.permissionRoleIds.filter(
              (role) => role !== permissionRoleId,
            )
          : [...previousFormData.permissionRoleIds, permissionRoleId],
      };
    });
  };

  const updateApplicableLabUnit = (labUnitId) => {
    setFormData((previousFormData) => {
      const exists = previousFormData.applicableLabUnitIds.includes(labUnitId);
      return {
        ...previousFormData,
        applicableLabUnitIds: exists
          ? previousFormData.applicableLabUnitIds.filter((id) => id !== labUnitId)
          : [...previousFormData.applicableLabUnitIds, labUnitId],
      };
    });
  };

  const hasSelectedLabPermissions = catalog.labPermissionRoles.some((permissionRole) =>
    formData.permissionRoleIds.includes(permissionRole.id),
  );

  const visibleGlobalPermissionRoles = catalog.globalPermissionRoles.filter(
    (permissionRole) =>
      !HIDDEN_GLOBAL_PERMISSION_NAMES.has(String(permissionRole?.name || "").trim()),
  );
  const visibleLabPermissionRoles = catalog.labPermissionRoles.filter(
    (permissionRole) =>
      !HIDDEN_LAB_PERMISSION_NAMES.has(String(permissionRole?.name || "").trim()),
  );
  const visibleApplicableLabUnits = catalog.labUnits.filter(
    (labUnit) =>
      !HIDDEN_APPLICABLE_LAB_UNIT_NAMES.has(String(labUnit?.name || "").trim()),
  );
  const groupedLabPermissionRoles = useMemo(() => {
    const roleByName = new Map(
      visibleLabPermissionRoles.map((permissionRole) => [
        String(permissionRole?.name || "").trim(),
        permissionRole,
      ]),
    );
    const consumedRoleIds = new Set();

    const groupedRoles = LAB_PERMISSION_GROUPS.map(
      ({ parentName, childNames }) => {
        const parentRole = roleByName.get(parentName) || null;
        const childRoles = childNames
          .map((childName) => roleByName.get(childName) || null)
          .filter(Boolean);

        if (!parentRole && childRoles.length === 0) {
          return null;
        }

        if (parentRole) {
          consumedRoleIds.add(parentRole.id);
        }
        childRoles.forEach((childRole) => consumedRoleIds.add(childRole.id));

        return {
          key: parentName,
          parentRole,
          childRoles,
        };
      },
    ).filter(Boolean);

    const ungroupedRoles = visibleLabPermissionRoles.filter(
      (permissionRole) => !consumedRoleIds.has(permissionRole.id),
    );

    return {
      groupedRoles,
      ungroupedRoles,
    };
  }, [visibleLabPermissionRoles]);

  const showError = async (response) => {
    let message = intl.formatMessage({ id: "server.error.msg" });
    if (response && typeof response.json === "function") {
      try {
        const payload = await response.json();
        if (payload?.message) {
          message = payload.message;
        } else if (payload?.errors && typeof payload.errors === "object") {
          message = Object.values(payload.errors).filter(Boolean).join(", ") || message;
        } else if (Array.isArray(payload?.globalErrors) && payload.globalErrors.length) {
          message = payload.globalErrors.join(", ");
        }
      } catch (_error) {
        // ignore parse failure
      }
    } else if (response && typeof response === "object") {
      if (response?.message) {
        message = response.message;
      } else if (response?.errors && typeof response.errors === "object") {
        message = Object.values(response.errors).filter(Boolean).join(", ") || message;
      } else if (
        Array.isArray(response?.globalErrors) &&
        response.globalErrors.length
      ) {
        message = response.globalErrors.join(", ");
      } else {
        message = response?.error || response?.statusText || message;
      }
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
      permissionRoleIds: formData.permissionRoleIds,
      applicableLabUnitIds: formData.applicableLabUnitIds,
    });

    const handleSuccess = () => {
      addNotification({
        kind: NotificationKinds.success,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "customRole.save.success",
          defaultMessage: "Custom role saved successfully",
        }),
      });
      setNotificationVisible(true);
      history.push("/MasterListsPage/roleManagement");
    };

    if (roleId === "0") {
      postToOpenElisServerJsonResponse("/rest/custom-roles", payload, (response) => {
        if (response?.id) {
          handleSuccess();
          return;
        }
        showError(response);
      });
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
              {roleId === "0" ? (
                <FormattedMessage
                  id="customRole.add.title"
                  defaultMessage="Create Custom Role"
                />
              ) : (
                <FormattedMessage
                  id="customRole.edit.title"
                  defaultMessage="Edit Custom Role"
                />
              )}
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
                    defaultMessage: "Role name",
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
                    defaultMessage: "Description",
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
            <Grid fullWidth>
              {visibleGlobalPermissionRoles.length > 0 ? (
                <Column lg={8} md={4} sm={4}>
                  <Heading>
                    <FormattedMessage
                      id="customRole.permissions.global"
                      defaultMessage="Global permissions"
                    />
                  </Heading>
                  <FormGroup
                    legendId="custom-role-global-permissions"
                    legendText=""
                  >
                    {visibleGlobalPermissionRoles.map((permissionRole) => (
                      <Checkbox
                        key={`global-${permissionRole.id}`}
                        id={`global-${permissionRole.id}`}
                        labelText={getLabPermissionLabel(permissionRole.name)}
                        checked={formData.permissionRoleIds.includes(
                          permissionRole.id,
                        )}
                        onChange={() => updatePermissionRole(permissionRole.id)}
                      />
                    ))}
                  </FormGroup>
                </Column>
              ) : null}
              <Column
                lg={visibleGlobalPermissionRoles.length > 0 ? 8 : 16}
                md={8}
                sm={4}
              >
                <Heading>
                  <FormattedMessage
                    id="customRole.permissions.lab"
                    defaultMessage="Lab unit permissions"
                  />
                </Heading>
                <p>
                  <FormattedMessage
                    id="customRole.permissions.lab.help"
                    defaultMessage="These permissions are granted only on the selected lab units when the role is assigned to a user."
                  />
                </p>
                <FormGroup legendId="custom-role-lab-permissions" legendText="">
                  <Accordion align="start">
                    {groupedLabPermissionRoles.groupedRoles.map((permissionGroup) => {
                      const { parentRole, childRoles, key } = permissionGroup;

                      if (!parentRole) {
                        return null;
                      }

                      if (childRoles.length === 0) {
                        return (
                          <Checkbox
                            key={`lab-${parentRole.id}`}
                            id={`lab-${parentRole.id}`}
                            labelText={getLabPermissionLabel(parentRole.name)}
                            checked={formData.permissionRoleIds.includes(
                              parentRole.id,
                            )}
                            onChange={() => updatePermissionRole(parentRole.id)}
                          />
                        );
                      }

                      return (
                        <AccordionItem
                          key={`lab-group-${key}`}
                          title={getLabPermissionLabel(parentRole.name)}
                        >
                          <div>
                            <Checkbox
                              id={`lab-${parentRole.id}`}
                              labelText={getLabPermissionLabel(parentRole.name)}
                              checked={isParentGroupChecked(
                                parentRole,
                                childRoles,
                              )}
                              onChange={() => updatePermissionRole(parentRole.id)}
                            />
                          </div>
                          <div style={{ paddingLeft: "1.5rem" }}>
                            {childRoles.map((permissionRole) => (
                              <Checkbox
                                key={`lab-${permissionRole.id}`}
                                id={`lab-${permissionRole.id}`}
                                labelText={getLabPermissionLabel(
                                  permissionRole.name,
                                )}
                                checked={formData.permissionRoleIds.includes(
                                  permissionRole.id,
                                )}
                                onChange={() =>
                                  updatePermissionRole(permissionRole.id)
                                }
                              />
                            ))}
                          </div>
                        </AccordionItem>
                      );
                    })}
                  </Accordion>
                  {groupedLabPermissionRoles.ungroupedRoles.map((permissionRole) => (
                    <Checkbox
                      key={`lab-${permissionRole.id}`}
                      id={`lab-${permissionRole.id}`}
                      labelText={getLabPermissionLabel(permissionRole.name)}
                      checked={formData.permissionRoleIds.includes(
                        permissionRole.id,
                      )}
                      onChange={() => updatePermissionRole(permissionRole.id)}
                    />
                  ))}
                </FormGroup>
                <br />
                <Heading>
                  <FormattedMessage
                    id="customRole.permissions.labUnits"
                    defaultMessage="Applicable lab units"
                  />
                </Heading>
                <p>
                  <FormattedMessage
                    id="customRole.permissions.labUnits.help"
                    defaultMessage="Choose one or more lab units for the selected lab permissions."
                  />
                </p>
                <FormGroup legendId="custom-role-lab-units" legendText="">
                  {visibleApplicableLabUnits.map((labUnit) => (
                    <Checkbox
                      key={`lab-unit-${labUnit.id}`}
                      id={`lab-unit-${labUnit.id}`}
                      labelText={labUnit.name}
                      checked={formData.applicableLabUnitIds.includes(labUnit.id)}
                      disabled={!hasSelectedLabPermissions}
                      onChange={() => updateApplicableLabUnit(labUnit.id)}
                    />
                  ))}
                </FormGroup>
              </Column>
            </Grid>
            <br />
            <Button
              type="submit"
              disabled={!formData.name.trim()}
            >
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
