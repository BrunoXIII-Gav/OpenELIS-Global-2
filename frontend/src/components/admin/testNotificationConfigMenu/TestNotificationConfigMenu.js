import React, { useContext, useState, useEffect, useMemo } from "react";
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
  TableContainer,
  Pagination,
  Search,
  Checkbox,
} from "@carbon/react";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils.js";
import {
  NotificationContext,
} from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { Settings } from "@carbon/icons-react";

let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "testnotificationconfig.browse.title",
    link: "/MasterListsPage/testNotificationConfigMenu",
  },
];

function TestNotificationConfigMenu() {
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const intl = useIntl();

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [loading, setLoading] = useState(true);
  const [saveButton, setSaveButton] = useState(true);
  const [testNamesList, setTestNamesList] = useState([]);
  const [testNotificationConfigMenuData, setTestNotificationConfigMenuData] =
    useState({});
  const [
    testNotificationConfigMenuDataPost,
    setTestNotificationConfigMenuDataPost,
  ] = useState({ menuList: [] });
  const [testNamesMap, setTestNamesMap] = useState({});
  const [searchValue, setSearchValue] = useState("");
  const [debouncedSearchValue, setDebouncedSearchValue] = useState("");

  const handleMenuItems = (res) => {
    if (res) {
      setTestNotificationConfigMenuData(res);
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
    getFromOpenElisServer(`/rest/TestNotificationConfigMenu`, handleMenuItems);
    getFromOpenElisServer(`/rest/test-list`, handleTestNamesList);
  }, []);

  useEffect(() => {
    if (
      testNotificationConfigMenuData &&
      testNotificationConfigMenuData.menuList
    ) {
      setTestNotificationConfigMenuDataPost((prevTestNotificationDataPost) => ({
        ...prevTestNotificationDataPost,
        formMethod: testNotificationConfigMenuData.formMethod,
        // formAction: testNotificationConfigMenuData.formAction,
        // formName: testNotificationConfigMenuData.formName,
        // config: testNotificationConfigMenuData.config,
        cancelAction: testNotificationConfigMenuData.cancelAction,
        submitOnCancel: testNotificationConfigMenuData.submitOnCancel,
        cancelMethod: testNotificationConfigMenuData.cancelMethod,
        adminMenuItems: testNotificationConfigMenuData.adminMenuItems,
        totalRecordCount: testNotificationConfigMenuData.totalRecordCount,
        fromRecordCount: testNotificationConfigMenuData.fromRecordCount,
        toRecordCount: testNotificationConfigMenuData.toRecordCount,
        selectedIDs: testNotificationConfigMenuData.selectedIDs,
        menuList: testNotificationConfigMenuData.menuList,
      }));
    }
  }, [testNotificationConfigMenuData]);

  useEffect(() => {
    const map = testNamesList.reduce((acc, item) => {
      acc[item.id] = item.value;
      return acc;
    }, {});
    setTestNamesMap(map);
  }, [testNamesList]);

  useEffect(() => {
    const debounceTimer = setTimeout(() => {
      setDebouncedSearchValue(searchValue);
    }, 250);

    return () => clearTimeout(debounceTimer);
  }, [searchValue]);

  const normalizeSearchText = (value) =>
    String(value || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, " ");

  const normalizedSearchTokens = useMemo(() => {
    const normalizedSearchTerm = normalizeSearchText(debouncedSearchValue);

    if (!normalizedSearchTerm) {
      return [];
    }

    return normalizedSearchTerm.split(/\s+/).filter(Boolean);
  }, [debouncedSearchValue]);

  const filteredMenuList = useMemo(() => {
    const menuList = testNotificationConfigMenuDataPost?.menuList || [];

    if (!normalizedSearchTokens.length) {
      return menuList;
    }

    return menuList.filter((item) => {
      const searchableText = normalizeSearchText(
        `${item.testId} ${testNamesMap[item.testId] || ""}`,
      );

      return normalizedSearchTokens.every((token) =>
        searchableText.includes(token),
      );
    });
  }, [
    normalizedSearchTokens,
    testNamesMap,
    testNotificationConfigMenuDataPost?.menuList,
  ]);

  const paginatedRows = useMemo(() => {
    return filteredMenuList
      .slice((page - 1) * pageSize, page * pageSize)
      .map((item) => ({
        id: item.testId,
        testId: item.testId,
        patientEmail: item.patientEmail.active ? "true" : "false",
        patientSMS: item.patientSMS.active ? "true" : "false",
        providerEmail: item.providerEmail.active ? "true" : "false",
        providerSMS: item.providerSMS.active ? "true" : "false",
        testName: testNamesMap[item.testId] || item.testId,
      }));
  }, [filteredMenuList, page, pageSize, testNamesMap]);

  useEffect(() => {
    setPage(1);
  }, [debouncedSearchValue]);

  const handleEditButtonClick = (id) => {
    window.location.assign(
      `/MasterListsPage/testNotificationConfig?testId=${id}`,
    );
  };

  function testNotificationConfigMenuSavePostCall() {
    setLoading(true);
    postToOpenElisServerJsonResponse(
      `/rest/TestNotificationConfigMenu`,
      JSON.stringify(testNotificationConfigMenuDataPost),
      (res) => {
        testNotificationConfigMenuSavePostCallBack(res);
      },
    );
  }

  function testNotificationConfigMenuSavePostCallBack(res) {
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

  const handleCheckboxChange = (e, rowId, header) => {
    const isChecked = e.target.checked;

    setTestNotificationConfigMenuDataPost((prevData) => {
      const updatedMenuList = prevData.menuList.map((item) => {
        if (item.testId === rowId) {
          switch (header) {
            case "patientEmail":
              return {
                ...item,
                patientEmail: { ...item.patientEmail, active: isChecked },
              };
            case "patientSMS":
              return {
                ...item,
                patientSMS: { ...item.patientSMS, active: isChecked },
              };
            case "providerEmail":
              return {
                ...item,
                providerEmail: { ...item.providerEmail, active: isChecked },
              };
            case "providerSMS":
              return {
                ...item,
                providerSMS: { ...item.providerSMS, active: isChecked },
              };
            default:
              return item;
          }
        }
        return item;
      });

      return {
        ...prevData,
        menuList: updatedMenuList,
      };
    });
  };

  const handlePageChange = ({ page, pageSize }) => {
    setPage(page);
    setPageSize(pageSize);
  };

  const renderCell = (cell, row) => {
    if (cell.info.header === "testId") {
      return <TableCell key={cell.id}>{cell.value}</TableCell>;
    } else if (cell.info.header === "testName") {
      return <TableCell key={cell.id}>{cell.value}</TableCell>;
    } else if (
      cell.info.header === "patientEmail" ||
      cell.info.header === "patientSMS" ||
      cell.info.header === "providerEmail" ||
      cell.info.header === "providerSMS"
    ) {
      return (
        <TableCell key={cell.id}>
          <Checkbox
            id={`checkbox-${row.id}-${cell.info.header}`}
            labelText=""
            checked={
              testNotificationConfigMenuDataPost?.menuList.find(
                (item) => item.testId === row.id,
              )?.[cell.info.header]?.active || false
            }
            onChange={(e) => {
              setSaveButton(false);
              handleCheckboxChange(e, row.id, cell.info.header);
            }}
          />
        </TableCell>
      );
    } else if (cell.info.header === "edit") {
      return (
        <TableCell key={cell.id}>
          <Button
            hasIconOnly
            iconDescription={intl.formatMessage({
              id: "testnotification.testdefault.editIcon",
            })}
            onClick={() => handleEditButtonClick(row.cells[0].value)}
            renderIcon={Settings}
            kind="tertiary"
          />
        </TableCell>
      );
    } else {
      return <TableCell key={cell.id}>{cell.value}</TableCell>;
    }
  };

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}
      {loading && <Loading></Loading>}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <Grid fullWidth={true}>
          <Column lg={16}>
            <Section>
              <Heading>
                <FormattedMessage id="testnotificationconfig.browse.title" />
              </Heading>
            </Section>
            <br />
            <Section>
              <Column
                lg={16}
                md={8}
                sm={4}
                style={{ display: "flex", gap: "10px" }}
              >
                <Button
                  disabled={saveButton}
                  onClick={testNotificationConfigMenuSavePostCall}
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
            </Section>
          </Column>
        </Grid>
        <div className="orderLegendBody">
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <br />
              <Section>
                <Search
                  size="lg"
                  id="test-notification-config-search"
                  labelText={intl.formatMessage({
                    id: "testnotificationconfig.search.label",
                    defaultMessage: "Search tests",
                  })}
                  aria-label={intl.formatMessage({
                    id: "testnotificationconfig.search.ariaLabel",
                    defaultMessage: "Search by Test ID or Test name",
                  })}
                  placeholder={intl.formatMessage({
                    id: "testnotificationconfig.search.placeholder",
                    defaultMessage: "Search by Test ID or Test name",
                  })}
                  onChange={(event) => setSearchValue(event.target.value)}
                  value={searchValue}
                  onClear={() => setSearchValue("")}
                />
              </Section>
              <br />
              <Section>
                {intl.formatMessage(
                  {
                    id: "testnotificationconfig.search.count",
                    defaultMessage: "Showing {shown} of {total} tests",
                  },
                  {
                    shown: filteredMenuList.length,
                    total: testNotificationConfigMenuDataPost?.menuList.length || 0,
                  },
                )}
              </Section>
              <br />
              <DataTable
                rows={paginatedRows}
                headers={[
                  {
                    key: "testId",
                    header: intl.formatMessage({
                      id: "column.name.testId",
                    }),
                  },
                  {
                    key: "testName",
                    header: intl.formatMessage({
                      id: "label.testName",
                    }),
                  },
                  {
                    key: "patientEmail",
                    header: intl.formatMessage({
                      id: "testnotification.patient.email",
                    }),
                  },
                  {
                    key: "patientSMS",
                    header: intl.formatMessage({
                      id: "testnotification.patient.sms",
                    }),
                  },
                  {
                    key: "providerEmail",
                    header: intl.formatMessage({
                      id: "testnotification.provider.email",
                    }),
                  },
                  {
                    key: "providerSMS",
                    header: intl.formatMessage({
                      id: "testnotification.provider.sms",
                    }),
                  },
                  {
                    key: "edit",
                    header: intl.formatMessage({
                      id: "banner.menu.patientEdit",
                    }),
                  },
                ]}
              >
                {({
                  rows,
                  headers,
                  getHeaderProps,
                  getTableProps,
                }) => (
                  <TableContainer>
                    <Table {...getTableProps()}>
                      <TableHead>
                        <TableRow>
                          {headers.map((header) => (
                            // header.key !== "id" &&
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
                        {rows.length ? (
                          rows.map((row) => (
                            <TableRow key={row.id}>
                              {row.cells.map((cell) => renderCell(cell, row))}
                            </TableRow>
                          ))
                        ) : (
                          <TableRow>
                            <TableCell colSpan={headers.length}>
                              {intl.formatMessage({
                                id: "testnotificationconfig.search.empty",
                                defaultMessage:
                                  "No tests found matching your search",
                              })}
                            </TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </DataTable>
              <Pagination
                onChange={handlePageChange}
                page={page}
                pageSize={pageSize}
                pageSizes={[25, 50]}
                totalItems={filteredMenuList.length}
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
              <br />
            </Column>
          </Grid>
          <br />
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <Button
                disabled={saveButton}
                onClick={testNotificationConfigMenuSavePostCall}
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
    </>
  );
}

export default injectIntl(TestNotificationConfigMenu);
