import React, { useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  Grid,
  Column,
  Section,
  Heading,
  Select,
  SelectItem,
  TextInput,
  Checkbox,
  Button,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableContainer,
} from "@carbon/react";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  deleteFromOpenElisServer,
} from "../../utils/Utils.js";
import { NotificationContext } from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "master.lists.page.test.management",
    link: "/MasterListsPage/testManagementConfigMenu",
  },
  {
    label: "configuration.test.parentchild",
    link: "/MasterListsPage/TestParentChildDependency",
  },
];

const defaultFormState = {
  id: "",
  parentTestId: "",
  childTestId: "",
  displayOrder: "",
  active: true,
};

function TestParentChildDependency() {
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const intl = useIntl();
  const componentMounted = useRef(false);

  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingTests, setIsLoadingTests] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [sampleTypes, setSampleTypes] = useState([]);
  const [selectedSampleType, setSelectedSampleType] = useState("");
  const [tests, setTests] = useState([]);
  const [dependencies, setDependencies] = useState([]);
  const [formState, setFormState] = useState(defaultFormState);

  const testNameById = useMemo(() => {
    const map = {};
    tests.forEach((test) => {
      map[String(test.id)] = test.value;
    });
    return map;
  }, [tests]);

  const loadDependencies = (onComplete) => {
    getFromOpenElisServer(
      "/rest/test-parent-child-dependencies",
      (dependencyResponse) => {
        if (!componentMounted.current) {
          return;
        }
        setDependencies(
          Array.isArray(dependencyResponse) ? dependencyResponse : [],
        );
        if (onComplete) {
          onComplete();
        }
      },
    );
  };

  const loadTestsBySampleType = (sampleTypeId) => {
    if (!sampleTypeId) {
      setTests([]);
      return;
    }

    setIsLoadingTests(true);
    getFromOpenElisServer(
      `/rest/tests-by-sample?sampleType=${sampleTypeId}`,
      (testResponse) => {
        if (!componentMounted.current) {
          return;
        }
        const loadedTests = Array.isArray(testResponse) ? testResponse : [];
        setTests(loadedTests);
        setIsLoadingTests(false);
      },
    );
  };

  const loadData = () => {
    setIsLoading(true);
    getFromOpenElisServer("/rest/samples", (sampleTypeResponse) => {
      if (!componentMounted.current) {
        return;
      }
      setSampleTypes(
        Array.isArray(sampleTypeResponse) ? sampleTypeResponse : [],
      );

      loadDependencies(() => {
        setIsLoading(false);
      });
    });
  };

  useEffect(() => {
    componentMounted.current = true;
    loadData();
    return () => {
      componentMounted.current = false;
    };
  }, []);

  const resetForm = () => {
    setFormState(defaultFormState);
  };

  const onSampleTypeChange = (sampleTypeId) => {
    setSelectedSampleType(sampleTypeId);
    resetForm();
    loadTestsBySampleType(sampleTypeId);
  };

  const notifySuccess = (messageId, defaultMessage) => {
    addNotification({
      kind: NotificationKinds.success,
      title: intl.formatMessage({ id: "notification.title" }),
      message: intl.formatMessage({
        id: messageId,
        defaultMessage,
      }),
    });
    setNotificationVisible(true);
  };

  const notifyError = (defaultMessage) => {
    addNotification({
      kind: NotificationKinds.error,
      title: intl.formatMessage({ id: "notification.title" }),
      message: defaultMessage
        ? defaultMessage
        : intl.formatMessage({ id: "server.error.msg" }),
    });
    setNotificationVisible(true);
  };

  const validateForm = () => {
    if (!selectedSampleType) {
      notifyError(
        intl.formatMessage({
          id: "test.dependency.validation.sampleType",
          defaultMessage: "Sample type is required.",
        }),
      );
      return false;
    }

    if (!formState.parentTestId || !formState.childTestId) {
      notifyError(
        intl.formatMessage({
          id: "test.dependency.validation.required",
          defaultMessage: "Parent and child tests are required.",
        }),
      );
      return false;
    }

    if (formState.parentTestId === formState.childTestId) {
      notifyError(
        intl.formatMessage({
          id: "test.dependency.validation.same",
          defaultMessage: "Parent and child tests must be different.",
        }),
      );
      return false;
    }

    return true;
  };

  const onSave = () => {
    if (!validateForm()) {
      return;
    }

    const displayOrderValue =
      formState.displayOrder === "" ? null : Number(formState.displayOrder);
    const payload = {
      id: formState.id || null,
      parentTestId: formState.parentTestId,
      childTestId: formState.childTestId,
      active: formState.active,
      displayOrder:
        Number.isFinite(displayOrderValue) && displayOrderValue >= 0
          ? displayOrderValue
          : null,
    };

    setIsSaving(true);
    postToOpenElisServerJsonResponse(
      "/rest/test-parent-child-dependencies",
      JSON.stringify(payload),
      (response) => {
        setIsSaving(false);
        if (response && !(response.status && response.status >= 400)) {
          notifySuccess(
            "test.dependency.save.success",
            "Parent-child test dependency saved.",
          );
          resetForm();
          loadDependencies();
          return;
        }

        notifyError(response?.message);
      },
    );
  };

  const onEdit = (dependency) => {
    setFormState({
      id: dependency.id || "",
      parentTestId: dependency.parentTestId || "",
      childTestId: dependency.childTestId || "",
      displayOrder:
        dependency.displayOrder === null ||
        dependency.displayOrder === undefined
          ? ""
          : String(dependency.displayOrder),
      active: dependency.active !== false,
    });
  };

  const onDelete = (dependency) => {
    const confirmed = window.confirm(
      intl.formatMessage(
        {
          id: "test.dependency.delete.confirm",
          defaultMessage:
            "Delete dependency between {parent} and {child}? This action cannot be undone.",
        },
        {
          parent:
            testNameById[String(dependency.parentTestId)] ||
            dependency.parentTestId,
          child:
            testNameById[String(dependency.childTestId)] ||
            dependency.childTestId,
        },
      ),
    );

    if (!confirmed) {
      return;
    }

    deleteFromOpenElisServer(
      `/rest/test-parent-child-dependencies/${dependency.id}`,
      (status) => {
        if (status === 204) {
          notifySuccess(
            "test.dependency.delete.success",
            "Parent-child test dependency removed.",
          );
          loadDependencies();
          return;
        }

        notifyError();
      },
    );
  };

  const filteredDependencies = useMemo(() => {
    if (!selectedSampleType || tests.length === 0) {
      return [];
    }
    const testIds = new Set(tests.map((test) => String(test.id)));
    return dependencies.filter(
      (dependency) =>
        testIds.has(String(dependency.parentTestId)) &&
        testIds.has(String(dependency.childTestId)),
    );
  }, [dependencies, selectedSampleType, tests]);

  const sortedDependencies = useMemo(() => {
    return [...filteredDependencies].sort((a, b) => {
      const parentA = testNameById[String(a.parentTestId)] || "";
      const parentB = testNameById[String(b.parentTestId)] || "";
      if (parentA !== parentB) {
        return parentA.localeCompare(parentB);
      }
      const orderA = a.displayOrder ?? Number.MAX_SAFE_INTEGER;
      const orderB = b.displayOrder ?? Number.MAX_SAFE_INTEGER;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      const childA = testNameById[String(a.childTestId)] || "";
      const childB = testNameById[String(b.childTestId)] || "";
      return childA.localeCompare(childB);
    });
  }, [filteredDependencies, testNameById]);

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
                  <FormattedMessage id="configuration.test.parentchild" />
                </Heading>
              </Section>
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <Grid fullWidth={true}>
            <Column lg={8} md={8} sm={4}>
              <Select
                id="sampleTypeFilter"
                labelText={
                  <FormattedMessage
                    id="field.sampleType"
                    defaultMessage="Sample Type"
                  />
                }
                value={selectedSampleType}
                onChange={(e) => onSampleTypeChange(e.target.value)}
                disabled={isLoading || isSaving}
              >
                <SelectItem
                  value=""
                  text={intl.formatMessage({
                    id: "test.dependency.select.sampleType",
                    defaultMessage: "-- Select sample type --",
                  })}
                />
                {sampleTypes.map((sampleType) => (
                  <SelectItem
                    key={sampleType.id}
                    value={sampleType.id}
                    text={sampleType.value}
                  />
                ))}
              </Select>
            </Column>
          </Grid>
          <br />
          <Grid fullWidth={true}>
            <Column lg={8} md={8} sm={4}>
              <Select
                id="parentTestId"
                labelText={
                  <FormattedMessage
                    id="test.dependency.parent"
                    defaultMessage="Parent Test"
                  />
                }
                value={formState.parentTestId}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    parentTestId: e.target.value,
                  }))
                }
                disabled={
                  isLoading || isSaving || isLoadingTests || !selectedSampleType
                }
              >
                <SelectItem
                  value=""
                  text={intl.formatMessage({
                    id: "test.dependency.select.parent",
                    defaultMessage: "-- Select parent test --",
                  })}
                />
                {tests.map((test) => (
                  <SelectItem key={test.id} value={test.id} text={test.value} />
                ))}
              </Select>
            </Column>
            <Column lg={8} md={8} sm={4}>
              <Select
                id="childTestId"
                labelText={
                  <FormattedMessage
                    id="test.dependency.child"
                    defaultMessage="Child Test"
                  />
                }
                value={formState.childTestId}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    childTestId: e.target.value,
                  }))
                }
                disabled={
                  isLoading || isSaving || isLoadingTests || !selectedSampleType
                }
              >
                <SelectItem
                  value=""
                  text={intl.formatMessage({
                    id: "test.dependency.select.child",
                    defaultMessage: "-- Select child test --",
                  })}
                />
                {tests.map((test) => (
                  <SelectItem key={test.id} value={test.id} text={test.value} />
                ))}
              </Select>
            </Column>
          </Grid>
          <br />
          <Grid fullWidth={true}>
            <Column lg={4} md={4} sm={4}>
              <TextInput
                id="displayOrder"
                type="number"
                min={0}
                labelText={
                  <FormattedMessage
                    id="test.dependency.displayOrder"
                    defaultMessage="Display Order"
                  />
                }
                value={formState.displayOrder}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    displayOrder: e.target.value,
                  }))
                }
                disabled={isLoading || isSaving || !selectedSampleType}
              />
            </Column>
            <Column lg={4} md={4} sm={4}>
              <div style={{ marginTop: "2rem" }}>
                <Checkbox
                  id="dependencyActive"
                  labelText={
                    <FormattedMessage
                      id="test.dependency.active"
                      defaultMessage="Active"
                    />
                  }
                  checked={formState.active}
                  onChange={(_, { checked }) =>
                    setFormState((prev) => ({ ...prev, active: checked }))
                  }
                  disabled={isLoading || isSaving || !selectedSampleType}
                />
              </div>
            </Column>
            <Column lg={8} md={8} sm={4}>
              <div
                style={{ marginTop: "1.9rem", display: "flex", gap: "1rem" }}
              >
                <Button
                  onClick={onSave}
                  disabled={
                    isLoading ||
                    isSaving ||
                    isLoadingTests ||
                    !selectedSampleType
                  }
                >
                  <FormattedMessage id="label.button.save" />
                </Button>
                <Button
                  kind="secondary"
                  onClick={resetForm}
                  disabled={isLoading || isSaving || !selectedSampleType}
                >
                  <FormattedMessage id="label.button.clear" />
                </Button>
              </div>
            </Column>
          </Grid>
          <br />
          <hr />
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <TableContainer
                title={intl.formatMessage({
                  id: "test.dependency.current",
                  defaultMessage: "Configured Parent-Child Dependencies",
                })}
              >
                <Table size="md">
                  <TableHead>
                    <TableRow>
                      <TableHeader>
                        <FormattedMessage
                          id="test.dependency.parent"
                          defaultMessage="Parent Test"
                        />
                      </TableHeader>
                      <TableHeader>
                        <FormattedMessage
                          id="test.dependency.child"
                          defaultMessage="Child Test"
                        />
                      </TableHeader>
                      <TableHeader>
                        <FormattedMessage
                          id="test.dependency.displayOrder"
                          defaultMessage="Display Order"
                        />
                      </TableHeader>
                      <TableHeader>
                        <FormattedMessage
                          id="test.dependency.active"
                          defaultMessage="Active"
                        />
                      </TableHeader>
                      <TableHeader>
                        <FormattedMessage
                          id="test.dependency.actions"
                          defaultMessage="Actions"
                        />
                      </TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {sortedDependencies.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={5}>
                          {selectedSampleType ? (
                            <FormattedMessage
                              id="test.dependency.empty"
                              defaultMessage="No parent-child dependencies configured."
                            />
                          ) : (
                            <FormattedMessage
                              id="test.dependency.select.sampleType.prompt"
                              defaultMessage="Select a sample type to view and manage dependencies."
                            />
                          )}
                        </TableCell>
                      </TableRow>
                    ) : (
                      sortedDependencies.map((dependency) => (
                        <TableRow key={dependency.id}>
                          <TableCell>
                            {testNameById[String(dependency.parentTestId)] ||
                              dependency.parentTestId}
                          </TableCell>
                          <TableCell>
                            {testNameById[String(dependency.childTestId)] ||
                              dependency.childTestId}
                          </TableCell>
                          <TableCell>
                            {dependency.displayOrder === null ||
                            dependency.displayOrder === undefined
                              ? "-"
                              : dependency.displayOrder}
                          </TableCell>
                          <TableCell>
                            {dependency.active ? (
                              <FormattedMessage
                                id="label.yes"
                                defaultMessage="Yes"
                              />
                            ) : (
                              <FormattedMessage
                                id="label.no"
                                defaultMessage="No"
                              />
                            )}
                          </TableCell>
                          <TableCell>
                            <div style={{ display: "flex", gap: "0.5rem" }}>
                              <Button
                                kind="ghost"
                                size="sm"
                                onClick={() => onEdit(dependency)}
                                disabled={isSaving}
                              >
                                <FormattedMessage id="label.button.edit" />
                              </Button>
                              <Button
                                kind="danger--tertiary"
                                size="sm"
                                onClick={() => onDelete(dependency)}
                                disabled={isSaving}
                              >
                                <FormattedMessage
                                  id="label.button.remove"
                                  defaultMessage="Remove"
                                />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </Column>
          </Grid>
        </div>
      </div>
    </>
  );
}

export default injectIntl(TestParentChildDependency);
