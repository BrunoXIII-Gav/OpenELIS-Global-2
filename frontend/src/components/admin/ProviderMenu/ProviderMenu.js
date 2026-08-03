import React, {
  useContext,
  useState,
  useEffect,
  useRef,
  useMemo,
  useCallback,
} from "react";
import {
  Heading,
  Loading,
  Grid,
  Column,
  Section,
  Button,
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableHeader,
  TableCell,
  TableSelectRow,
  TableContainer,
  Pagination,
  Search,
  Modal,
  TextInput,
  Dropdown,
  Select,
  SelectItem,
} from "@carbon/react";
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
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import ActionPaginationButtonType from "../../common/ActionPaginationButtonType.js";

let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "provider.browse.title",
    link: "/MasterListsPage/providerMenu",
  },
];
function ProviderMenu() {
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const { reloadConfiguration, configurationProperties } =
    useContext(ConfigurationContext);

  const intl = useIntl();
  const yesOrNo = useMemo(
    () => [
      {
        id: "yes",
        value: intl.formatMessage({ id: "label.yes", defaultMessage: "Yes" }),
      },
      {
        id: "no",
        value: intl.formatMessage({ id: "label.no", defaultMessage: "No" }),
      },
    ],
    [intl],
  );

  const componentMounted = useRef(false);
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
  const [lastName, setLastName] = useState("");
  const [firstName, setFirstName] = useState("");
  const [telephone, setTelephone] = useState("");
  const [fax, setFax] = useState("");
  const [email, setEmail] = useState("");
  const [cmp, setCmp] = useState("");
  const [rne, setRne] = useState("");
  const [dni, setDni] = useState("");
  const [specialty, setSpecialty] = useState("");
  const [professionalProfileCode, setProfessionalProfileCode] =
    useState("MEDICAL_DOCTOR");
  const [professionalInitials, setProfessionalInitials] = useState("");
  const [cbpCode, setCbpCode] = useState("");
  const [isActive, setIsActive] = useState(yesOrNo[0]);
  const [showValidationErrors, setShowValidationErrors] = useState(false);

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

  const professionalProfileOptions = useMemo(
    () =>
      (configurationProperties?.professionalProfileOptions || "")
        .split(",")
        .map((entry) => entry.trim())
        .filter(Boolean)
        .map((entry) => {
          const [codeRaw, labelRaw] = entry.split("|");
          const code = (codeRaw || "").trim();
          const label = (labelRaw || codeRaw || "").trim();
          return code ? { code, label } : null;
        })
        .filter(Boolean),
    [configurationProperties?.professionalProfileOptions],
  );
  const profileLabelByCode = useMemo(
    () =>
      professionalProfileOptions.reduce((acc, option) => {
        acc[option.code] = option.label;
        return acc;
      }, {}),
    [professionalProfileOptions],
  );
  const selectedProfessionalProfileItem = useMemo(
    () =>
      professionalProfileOptions.find(
        (option) => option.code === professionalProfileCode,
      ) || null,
    [professionalProfileOptions, professionalProfileCode],
  );

  const isBiologistProfile =
    (professionalProfileCode || "").toUpperCase() === "BIOLOGIST";
  const isAnyModalOpen = isAddModalOpen || isUpdateModalOpen;

  const handleMenuItems = (res) => {
    if (!res) {
      setLoading(true);
    } else {
      setProviderMenuList(res);
    }
  };

  useEffect(() => {
    componentMounted.current = true;
    setLoading(true);
    getFromOpenElisServer(
      `/rest/ProviderMenu?paging=${paging}&startingRecNo=${startingRecNo}`,
      handleMenuItems,
    );
    return () => {
      componentMounted.current = false;
      setLoading(false);
    };
  }, [paging, startingRecNo]);

  const handleSearchedProviderMenuList = (res) => {
    if (res) {
      setProviderMenuList(res);
    }
  };

  useEffect(() => {
    getFromOpenElisServer(
      `/rest/SearchProviderMenu?search=Y&startingRecNo=${startingRecNo}&searchString=${panelSearchTerm}`,
      handleSearchedProviderMenuList,
    );
  }, [panelSearchTerm]);

  useEffect(() => {
    if (providerMenuList.providers) {
      const newProviderMenuList = providerMenuList.providers.map((item) => {
        const person = item.person || {};
        return {
          id: item.id,
          fhirUuid: item.fhirUuid,
          lastName: person.lastName,
          firstName: person.firstName,
          active: item.active,
          telephone: person.workPhone,
          fax: person.fax,
          email: person.email,
          cmp: item.npi,
          rne: item.externalId,
          dni: item.dni,
          specialty: item.specialty,
          professionalProfileCode: item.professionalProfileCode || "",
          professionalProfileLabel:
            profileLabelByCode[item.professionalProfileCode] ||
            item.professionalProfileCode ||
            "",
          professionalInitials: item.professionalInitials,
          cbpCode: item.cbpCode,
        };
      });
      setFromRecordCount(providerMenuList.fromRecordCount);
      setToRecordCount(providerMenuList.toRecordCount);
      setTotalRecordCount(providerMenuList.totalRecordCount);
      setProviderMenuListShow(newProviderMenuList);
    }
  }, [providerMenuList, profileLabelByCode]);

  useEffect(() => {
    if (selectedRowIds.length === 1) {
      setModifyButton(false);
    } else {
      setModifyButton(true);
    }
    if (selectedRowIds.length === 0) {
      setDeactivateButton(true);
    } else {
      setDeactivateButton(false);
    }
  }, [selectedRowIds]);

  useEffect(() => {
    if (isSearching && panelSearchTerm === "") {
      setIsSearching(false);
      setPaging(1);
      setStartingRecNo(1);
    }
  }, [isSearching, panelSearchTerm]);

  async function displayStatus(res) {
    setNotificationVisible(true);
    if (res.status == "201" || res.status == "200") {
      addNotification({
        kind: NotificationKinds.success,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "save.config.success.msg" }),
      });
    } else {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "server.error.msg" }),
      });
    }
    reloadConfiguration();
  }

  function deleteDeactivateProvider(event) {
    event.preventDefault();
    setLoading(true);
    postToOpenElisServerFullResponse(
      `/rest/DeleteProvider?ID=${selectedRowIds.join(",")}&${startingRecNo}=1`,
      providerMenuListShow,
      setLoading(false),
      setTimeout(() => {
        window.location.reload();
      }, 1),
    );
  }

  const handlePageChange = ({ page, pageSize }) => {
    setPage(page);
    setPageSize(pageSize);
    setSelectedRowIds([]);
  };

  const handleNextPage = () => {
    setPaging((pager) => Math.max(pager, 2));
    setStartingRecNo(fromRecordCount);
    setSelectedRowIds([]);
  };

  const handlePreviousPage = () => {
    setPaging((pager) => Math.max(pager - 1, 1));
    setStartingRecNo(Math.max(fromRecordCount, 1));
    setSelectedRowIds([]);
  };

  const handlePanelSearchChange = (event) => {
    setIsSearching(true);
    setPaging(1);
    setStartingRecNo(1);
    const query = event.target.value.toLowerCase();
    setPanelSearchTerm(query);
    setSelectedRowIds([]);
  };

  const openAddModal = () => {
    setLastName("");
    setFirstName("");
    setTelephone("");
    setFax("");
    setEmail("");
    setCmp("");
    setRne("");
    setDni("");
    setSpecialty("");
    setProfessionalProfileCode("MEDICAL_DOCTOR");
    setProfessionalInitials("");
    setCbpCode("");
    setIsActive(yesOrNo[0]);
    setShowValidationErrors(false);
    setIsAddModalOpen(true);
  };

  const closeAddModal = () => {
    setShowValidationErrors(false);
    setIsAddModalOpen(false);
  };

  const openUpdateModal = (providerId) => {
    const provider = providerMenuListShow.find((p) => p.id === providerId);
    setCurrentProvider(provider);
    setLastName(provider.lastName);
    setFirstName(provider.firstName);
    setTelephone(provider.telephone);
    setFax(provider.fax);
    setEmail(provider.email || "");
    setCmp(provider.cmp || "");
    setRne(provider.rne || "");
    setDni(provider.dni || "");
    setSpecialty(provider.specialty || "");
    setProfessionalProfileCode(provider.professionalProfileCode || "");
    setProfessionalInitials(provider.professionalInitials || "");
    setCbpCode(provider.cbpCode || "");
    setIsActive(
      provider.active ? yesOrNo[0] : yesOrNo[1],
    );
    setShowValidationErrors(false);
    setIsUpdateModalOpen(true);
  };

  const closeUpdateModal = () => {
    setShowValidationErrors(false);
    setIsUpdateModalOpen(false);
  };

  const isDniValid = useMemo(() => /^\d{1,8}$/.test((dni || "").trim()), [dni]);

  const validateProviderForm = () => {
    const normalizedDni = (dni || "").trim();
    if (!normalizedDni) {
      return false;
    }
    return /^\d{1,8}$/.test(normalizedDni);
  };

  const handleAddProvider = () => {
    setShowValidationErrors(true);
    if (!validateProviderForm()) {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "provider.dni.required" }),
      });
      setNotificationVisible(true);
      return;
    }

    const newProvider = {
      person: {
        lastName,
        firstName,
        workPhone: telephone,
        fax,
        email,
      },
      npi: cmp,
      externalId: rne,
      dni,
      specialty,
      professionalProfileCode,
      professionalInitials,
      cbpCode,
      active: isActive.id === "yes",
    };
    postToOpenElisServerFullResponse(
      "/rest/Provider/FhirUuid?fhirUuid=",
      JSON.stringify(newProvider),
      displayStatus,
    );

    closeAddModal();
    window.location.reload();
  };

  const handleUpdateProvider = () => {
    setShowValidationErrors(true);
    if (!validateProviderForm()) {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "provider.dni.required" }),
      });
      setNotificationVisible(true);
      return;
    }

    const updatedProvider = {
      fhirUuid: currentProvider.fhirUuid,
      person: {
        lastName,
        firstName,
        workPhone: telephone,
        fax,
        email,
      },
      npi: cmp,
      externalId: rne,
      dni,
      specialty,
      professionalProfileCode,
      professionalInitials,
      cbpCode,
      active: isActive.id === "yes",
    };
    postToOpenElisServerFullResponse(
      "/rest/Provider/FhirUuid?fhirUuid=" + currentProvider.fhirUuid,
      JSON.stringify(updatedProvider),
      displayStatus,
    );

    closeUpdateModal();
    window.location.reload();
  };

  const handleLastNameChange = (event) => {
    const value = event.target.value;
    if (value === "" || /^[A-Za-z\s]+$/.test(value)) {
      setLastName(value);
    }
  };

  const handleFirstNameChange = (event) => {
    const value = event.target.value;
    if (value === "" || /^[A-Za-z\s]+$/.test(value)) {
      setFirstName(value);
    }
  };

  const handleTelephoneChange = (event) => {
    const value = event.target.value;
    if (value === "" || (/^\d+$/.test(value) && value.length <= 10)) {
      setTelephone(value);
    }
  };

  const handleProfessionalInitialsChange = (event) => {
    const value = (event.target.value || "").toUpperCase();
    if (/^[A-Z.\s-]*$/.test(value) && value.length <= 25) {
      setProfessionalInitials(value);
    }
  };

  const handleCbpCodeChange = (event) => {
    const value = (event.target.value || "").toUpperCase();
    if (/^[A-Z0-9-]*$/.test(value) && value.length <= 32) {
      setCbpCode(value);
    }
  };

  const handleDniChange = (event) => {
    const value = (event.target.value || "").trim();
    if (/^\d{0,8}$/.test(value)) {
      setDni(value);
    }
  };

  const renderSpecialtyInput = () => {
    if (providerSpecialtyOptions.length > 0) {
      return (
        <Select
          id="specialty"
          labelText={intl.formatMessage({
            id: "provider.specialty.label",
            defaultMessage: "Specialty",
          })}
          value={specialty || ""}
          onChange={(event) => setSpecialty(event.target.value)}
          disabled={isBiologistProfile}
        >
          <SelectItem value="" text="" />
          {providerSpecialtyOptions.map((option) => (
            <SelectItem
              key={`provider-specialty-option-${option.id}`}
              value={option.id}
              text={option.value}
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
        value={specialty}
        onChange={(event) => setSpecialty(event.target.value)}
        disabled={isBiologistProfile}
      />
    );
  };

  useEffect(() => {
    if (isBiologistProfile) {
      setCmp("");
      setRne("");
      setSpecialty("");
    } else {
      setProfessionalInitials("");
      setCbpCode("");
    }
  }, [isBiologistProfile]);

  const renderCell = useCallback(
    (cell, row) => {
      if (cell.info.header === "select") {
        return (
          <TableSelectRow
            key={cell.id}
            id={cell.id}
            checked={selectedRowIds.includes(row.id)}
            name="selectRowCheckbox"
            ariaLabel="selectRows"
            onSelect={(e) => {
              e.stopPropagation();
              if (selectedRowIds.includes(row.id)) {
                setSelectedRowIds(selectedRowIds.filter((id) => id !== row.id));
              } else {
                setSelectedRowIds([...selectedRowIds, row.id]);
              }
            }}
          />
        );
      } else if (cell.info.header === "active") {
        return <TableCell key={cell.id}>{cell.value.toString()}</TableCell>;
      } else {
        return <TableCell key={cell.id}>{cell.value}</TableCell>;
      }
    },
    [selectedRowIds],
  );

  const tableHeaders = useMemo(
    () => [
      {
        key: "select",
        header: intl.formatMessage({
          id: "provider.select",
        }),
      },
      {
        key: "lastName",
        header: intl.formatMessage({
          id: "provider.providerLastName",
        }),
      },
      {
        key: "firstName",
        header: intl.formatMessage({
          id: "provider.providerFirstName",
        }),
      },
      {
        key: "professionalProfileLabel",
        header: intl.formatMessage({
          id: "provider.professional.profile",
        }),
      },
      {
        key: "active",
        header: intl.formatMessage({
          id: "provider.isActive",
        }),
      },
      {
        key: "telephone",
        header: intl.formatMessage({
          id: "provider.telephone",
        }),
      },
      {
        key: "cmp",
        header: "CMP",
      },
      {
        key: "rne",
        header: "RNE",
      },
      {
        key: "dni",
        header: "DNI",
      },
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
          id: "provider.professional.initials",
        }),
      },
      {
        key: "cbpCode",
        header: intl.formatMessage({
          id: "provider.cbp.code",
        }),
      },
      {
        key: "fax",
        header: intl.formatMessage({
          id: "provider.fax",
        }),
      },
      {
        key: "email",
        header: intl.formatMessage({
          id: "provider.email",
        }),
      },
    ],
    [intl],
  );

  const pagedProviderRows = useMemo(
    () => providerMenuListShow.slice((page - 1) * pageSize, page * pageSize),
    [providerMenuListShow, page, pageSize],
  );

  if (!loading) {
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
        <Grid fullWidth={true}>
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
            <Dropdown
              id="professional-profile-code"
              titleText={intl.formatMessage({
                id: "provider.professional.profile",
              })}
              label={intl.formatMessage({ id: "provider.select" })}
              items={professionalProfileOptions}
              itemToString={(item) => (item ? item.label : "")}
              selectedItem={selectedProfessionalProfileItem}
              onChange={({ selectedItem }) =>
                setProfessionalProfileCode(selectedItem?.code || "")
              }
            />
            <TextInput
              id="lastName"
              labelText={intl.formatMessage({
                id: "provider.providerLastName",
              })}
              value={lastName}
              onChange={(e) => handleLastNameChange(e)}
              required
            />
            <TextInput
              id="firstName"
              labelText={intl.formatMessage({
                id: "provider.providerFirstName",
              })}
              value={firstName}
              onChange={(e) => handleFirstNameChange(e)}
              required
            />
            <TextInput
              id="telephone"
              labelText={intl.formatMessage({ id: "provider.telephone" })}
              value={telephone}
              onChange={(e) => handleTelephoneChange(e)}
            />
            <TextInput
              id="cmp"
              labelText="CMP"
              value={cmp}
              onChange={(e) => setCmp(e.target.value)}
              disabled={isBiologistProfile}
            />
            <TextInput
              id="rne"
              labelText="RNE"
              value={rne}
              onChange={(e) => setRne(e.target.value)}
              disabled={isBiologistProfile}
            />
            <TextInput
              id="dni"
              labelText={intl.formatMessage({ id: "patient.identifier.dni" })}
              value={dni}
              onChange={handleDniChange}
              required
              maxLength={8}
              invalid={showValidationErrors && !isDniValid}
              invalidText={intl.formatMessage({
                id: "provider.dni.required",
              })}
            />
            {renderSpecialtyInput()}
            <TextInput
              id="professionalInitials"
              labelText={intl.formatMessage({
                id: "provider.professional.initials",
              })}
              value={professionalInitials}
              onChange={handleProfessionalInitialsChange}
              disabled={!isBiologistProfile}
            />
            <TextInput
              id="cbpCode"
              labelText={intl.formatMessage({ id: "provider.cbp.code" })}
              value={cbpCode}
              onChange={handleCbpCodeChange}
              disabled={!isBiologistProfile}
            />

            <Dropdown
              className="dropdown-list"
              id="isActive"
              titleText={intl.formatMessage({
                id: "provider.isActive",
              })}
              label={intl.formatMessage({ id: "provider.select" })}
              items={yesOrNo}
              itemToString={(item) => (item ? item.value : "")}
              selectedItem={isActive}
              onChange={({ selectedItem }) => setIsActive(selectedItem)}
            />
            <TextInput
              id="fax"
              labelText={intl.formatMessage({ id: "provider.fax" })}
              value={fax}
              onChange={(e) => setFax(e.target.value)}
            />
            <TextInput
              id="email"
              type="email"
              labelText={intl.formatMessage({ id: "provider.email" })}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
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
            <Dropdown
              id="professional-profile-code-update"
              titleText={intl.formatMessage({
                id: "provider.professional.profile",
              })}
              label={intl.formatMessage({ id: "provider.select" })}
              items={professionalProfileOptions}
              itemToString={(item) => (item ? item.label : "")}
              selectedItem={selectedProfessionalProfileItem}
              onChange={({ selectedItem }) =>
                setProfessionalProfileCode(selectedItem?.code || "")
              }
            />
            <TextInput
              id="lastName"
              labelText={intl.formatMessage({
                id: "provider.providerLastName",
              })}
              value={lastName}
              onChange={(e) => handleLastNameChange(e)}
              required
            />
            <TextInput
              id="firstName"
              labelText={intl.formatMessage({
                id: "provider.providerFirstName",
              })}
              value={firstName}
              onChange={(e) => handleFirstNameChange(e)}
              required
            />
            <TextInput
              id="telephone"
              labelText={intl.formatMessage({ id: "provider.telephone" })}
              value={telephone}
              onChange={(e) => handleTelephoneChange(e)}
            />
            <TextInput
              id="cmp"
              labelText="CMP"
              value={cmp}
              onChange={(e) => setCmp(e.target.value)}
              disabled={isBiologistProfile}
            />
            <TextInput
              id="rne"
              labelText="RNE"
              value={rne}
              onChange={(e) => setRne(e.target.value)}
              disabled={isBiologistProfile}
            />
            <TextInput
              id="dni"
              labelText={intl.formatMessage({ id: "patient.identifier.dni" })}
              value={dni}
              onChange={handleDniChange}
              required
              maxLength={8}
              invalid={showValidationErrors && !isDniValid}
              invalidText={intl.formatMessage({
                id: "provider.dni.required",
              })}
            />
            {renderSpecialtyInput()}
            <TextInput
              id="professionalInitials-update"
              labelText={intl.formatMessage({
                id: "provider.professional.initials",
              })}
              value={professionalInitials}
              onChange={handleProfessionalInitialsChange}
              disabled={!isBiologistProfile}
            />
            <TextInput
              id="cbpCode-update"
              labelText={intl.formatMessage({ id: "provider.cbp.code" })}
              value={cbpCode}
              onChange={handleCbpCodeChange}
              disabled={!isBiologistProfile}
            />
            <Dropdown
              id="isActive"
              titleText={intl.formatMessage({
                id: "provider.isActive",
              })}
              label={intl.formatMessage({ id: "provider.select" })}
              items={yesOrNo}
              itemToString={(item) => (item ? item.value : "")}
              selectedItem={isActive}
              onChange={({ selectedItem }) => setIsActive(selectedItem)}
            />
            <TextInput
              id="fax"
              labelText={intl.formatMessage({ id: "provider.fax" })}
              value={fax}
              onChange={(e) => setFax(e.target.value)}
            />
            <TextInput
              id="email"
              type="email"
              labelText={intl.formatMessage({ id: "provider.email" })}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Modal>
        ) : null}

        <div className="orderLegendBody">
          <Grid>
            <Column lg={16} md={8} sm={4}>
              <Section>
                <Search
                  size="lg"
                  id="provider-search-bar"
                  labelText={<FormattedMessage id="provider.search" />}
                  placeholder={intl.formatMessage({
                    id: "provider.search.placeholder",
                  })}
                  onChange={handlePanelSearchChange}
                  value={(() => {
                    if (panelSearchTerm) {
                      return panelSearchTerm;
                    }
                    return "";
                  })()}
                ></Search>
              </Section>
            </Column>
          </Grid>
          <br />
          {!isAnyModalOpen ? (
            <Grid fullWidth={true} className="gridBoundary">
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
                          <>
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
                          </>
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
                  totalItems={providerMenuListShow.length}
                  forwardText={intl.formatMessage({
                    id: "pagination.forward",
                  })}
                  backwardText={intl.formatMessage({
                    id: "pagination.backward",
                  })}
                  itemRangeText={(min, max, total) =>
                    intl.formatMessage(
                      { id: "pagination.item-range" },
                      { min: min, max: max, total: total },
                    )
                  }
                  itemsPerPageText={intl.formatMessage({
                    id: "pagination.items-per-page",
                  })}
                  itemText={(min, max) =>
                    intl.formatMessage(
                      { id: "pagination.item" },
                      { min: min, max: max },
                    )
                  }
                  pageNumberText={intl.formatMessage({
                    id: "pagination.page-number",
                  })}
                  pageRangeText={(_current, total) =>
                    intl.formatMessage(
                      { id: "pagination.page-range" },
                      { total: total },
                    )
                  }
                  pageText={(page, pagesUnknown) =>
                    intl.formatMessage(
                      { id: "pagination.page" },
                      { page: pagesUnknown ? "" : page },
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

export default injectIntl(ProviderMenu);
