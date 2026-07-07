import React from "react";
import {
  Tile,
  ClickableTile,
  Loading,
  Grid,
  Button,
  Column,
  DataTable,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  Pagination,
  Link,
  Tab,
  Tabs,
  TabList,
  Tag,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
} from "@carbon/react";
import "./Dashboard.css";
import { Minimize, Maximize, ArrowLeft, ArrowRight } from "@carbon/react/icons";
import { Copy } from "@carbon/icons-react";
import { useState, useEffect, useRef, useContext } from "react";
import {
  getFromOpenElisServer,
  convertAlphaNumLabNumForDisplay,
  hasRole,
} from "../utils/Utils.js";
import { FormattedMessage, useIntl } from "react-intl";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { NotificationContext } from "../layout/Layout";
import { AlertDialog, NotificationKinds } from "../common/CustomNotification";

interface DashBoardProps {}

interface Tile {
  title: string | JSX.Element;
  subTitle: string | JSX.Element;
  type: MetricType;
  value: number;
  id?: number;
}
type MetricType =
  | "ORDERS_IN_PROGRESS"
  | "AWAITING_SAMPLE"
  | "AWAITING_RESULTS"
  | "ORDERS_READY_FOR_VALIDATION"
  | "ORDERS_COMPLETED_TODAY"
  | "ORDERS_PATIALLY_COMPLETED_TODAY"
  | "ORDERS_ENTERED_BY_USER_TODAY"
  | "ORDERS_REJECTED_TODAY"
  | "UN_PRINTED_RESULTS"
  | "INCOMING_ORDERS"
  | "AVERAGE_TURN_AROUND_TIME"
  | "DELAYED_TURN_AROUND"
  | "ORDERS_FOR_USER";

interface UserSessionDetails {
  userSessionDetails: any;
}

interface Notification {
  notificationVisible: any;
  setNotificationVisible: any;
  addNotification: any;
}

const HomeDashBoard: React.FC<DashBoardProps> = () => {
  const intl = useIntl();

  const [counts, setCounts] = useState({
    ordersInProgress: 0,
    awaitingSample: 0,
    awaitingResults: 0,
    ordersReadyForValidation: 0,
    ordersCompletedToday: 0,
    patiallyCompletedToday: 0,
    orderEnterdByUserToday: 0,
    ordersRejectedToday: 0,
    unPritendResults: 0,
    incomigOrders: 0,
    averageTurnAroudTime: 0,
    delayedTurnAround: 0,
  });

  const [tileVisibility, setTileVisibility] = useState({
    ORDERS_PATIALLY_COMPLETED_TODAY: true,
    ORDERS_ENTERED_BY_USER_TODAY: true,
    ORDERS_REJECTED_TODAY: true,
    UN_PRINTED_RESULTS: true,
    INCOMING_ORDERS: true,
  });

  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [selectedTestType, setSelectedTestType] = useState("all");
  const [waitingTimeSort, setWaitingTimeSort] = useState("mostUrgent");

  const handleDateChange = (dates) => {
    if (!dates || dates.length === 0) {
      setStartDate("");
      setEndDate("");
      return;
    }

    const formatSafeDate = (d) => {
      const year = d.getFullYear();
      const month = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    };

    if (dates.length >= 1) {
      const start = formatSafeDate(dates[0]);
      const end =
        dates.length === 2 && dates[1] ? formatSafeDate(dates[1]) : start;

      setStartDate(start);
      setEndDate(end);
    }
  };

  const [timeMetrics, setTimeMetrics] = useState({
    receptionToResult: 0,
    resultToValidation: 0,
    receptionToValidation: 0,
  });

  const [data, setData] = useState([]);
  const [testSections, setTestSections] = useState([]);
  const [selectedTestSection, setSelectedTestSection] = useState("");
  const [loading, setLoading] = useState(true);
  const componentMounted = useRef(true);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(100);
  const [selectedTile, setSelectedTile] = useState<Tile>(null);
  const [nextPage, setNextPage] = useState(null);
  const [previousPage, setPreviousPage] = useState(null);
  const [pagination, setPagination] = useState(false);
  const [currentApiPage, setCurrentApiPage] = useState(null);
  const [totalApiPages, setTotalApiPages] = useState(null);
  const [url, setUrl] = useState("");
  const { userSessionDetails } = useContext(
    UserSessionDetailsContext,
  ) as UserSessionDetails;
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext) as Notification;

  const supportsTestTypeFilter =
    selectedTile?.type === "AWAITING_RESULTS" ||
    selectedTile?.type === "ORDERS_READY_FOR_VALIDATION";

  const supportsWaitingCounter =
    selectedTile?.type === "AWAITING_SAMPLE" ||
    selectedTile?.type === "AWAITING_RESULTS" ||
    selectedTile?.type === "ORDERS_READY_FOR_VALIDATION";

  const buildTestTypeParam = () => {
    if (!supportsTestTypeFilter || selectedTestType === "all") {
      return "";
    }
    return `&testType=${encodeURIComponent(selectedTestType)}`;
  };

  useEffect(() => {
    setNextPage(null);
    setPreviousPage(null);
    setPagination(false);
  }, []);

  useEffect(() => {
    getFromOpenElisServer(
      `/rest/home-dashboard/metrics?startDate=${startDate}&endDate=${endDate}`,
      loadCount,
    );

    getFromOpenElisServer(`/rest/home-dashboard/visibility-config`, (data) => {
      if (data) {
        setTileVisibility(data);
      }
    });

    return () => {
      componentMounted.current = false;
    };
  }, [startDate, endDate]);

  useEffect(() => {
    if (selectedTile != null) {
      setNextPage(null);
      setPreviousPage(null);
      setPagination(false);
      setLoading(true);
      if (selectedTile.type == "AVERAGE_TURN_AROUND_TIME") {
        getFromOpenElisServer(
          `/rest/home-dashboard/turn-around-time-metrics`,
          loadTimeMetrics,
        );
      } else if (selectedTile.type == "ORDERS_FOR_USER") {
        getFromOpenElisServer(
          "/rest/home-dashboard/" +
            selectedTile.type +
            "?systemUserId=" +
            selectedTile.id +
            `&startDate=${startDate}&endDate=${endDate}${buildTestTypeParam()}`,
          loadData,
        );
      } else {
        getFromOpenElisServer(
          "/rest/home-dashboard/" +
            selectedTile.type +
            `?startDate=${startDate}&endDate=${endDate}${buildTestTypeParam()}`,
          loadData,
        );
      }
    }

    return () => {
      componentMounted.current = false;
    };
  }, [selectedTile, startDate, endDate, selectedTestType]);

  useEffect(() => {
    getFromOpenElisServer(
      "/rest/user-test-sections/ALL",
      (fetchedTestSections) => {
        fetchTestSections(fetchedTestSections);
      },
    );
    return () => {
      componentMounted.current = false;
    };
  }, []);

  const fetchTestSections = (res) => {
    setTestSections(res);
    hasRole(userSessionDetails, "Global Administrator")
      ? setSelectedTestSection("all")
      : setSelectedTestSection(
          res && res.length > 0 && res[0]?.id != null ? String(res[0].id) : "",
        );
  };

  const loadNextResultsPage = () => {
    setLoading(true);
    getFromOpenElisServer(
      "/rest/home-dashboard/" +
        selectedTile.type +
        "?page=" +
        nextPage +
        `&startDate=${startDate}&endDate=${endDate}${buildTestTypeParam()}`,
      loadData,
    );
  };

  const loadPreviousResultsPage = () => {
    setLoading(true);
    getFromOpenElisServer(
      "/rest/home-dashboard/" +
        selectedTile.type +
        "?page=" +
        previousPage +
        `&startDate=${startDate}&endDate=${endDate}${buildTestTypeParam()}`,
      loadData,
    );
  };

  const loadCount = (data) => {
    if (componentMounted.current) {
      if (data) {
        setCounts(data);
      }
      setLoading(false);
    }
  };

  const loadData = (res) => {
    // If the response object is not null and has displayItems array with length greater than 0 then set it as data.
    if (res && res.displayItems && res.displayItems.length > 0) {
      setData(res.displayItems);
    } else {
      setData([]);
    }

    // Sets next and previous page numbers based on the total pages and current page number.
    if (res && res.paging) {
      const { totalPages, currentPage } = res.paging;
      if (totalPages > 1) {
        setPagination(true);
        setCurrentApiPage(currentPage);
        setTotalApiPages(totalPages);
        if (parseInt(currentPage) < parseInt(totalPages)) {
          setNextPage(parseInt(currentPage) + 1);
        } else {
          setNextPage(null);
        }

        if (parseInt(currentPage) > 1) {
          setPreviousPage(parseInt(currentPage) - 1);
        } else {
          setPreviousPage(null);
        }
      }
    }

    setLoading(false);
  };

  const loadTimeMetrics = (data) => {
    setTimeMetrics(data);
    setLoading(false);
  };

  const allTiles: Array<Tile> = [
    {
      title: <FormattedMessage id="dashboard.in.progress.label" />,
      subTitle: <FormattedMessage id="dashboard.in.progress.subtitle.label" />,
      type: "ORDERS_IN_PROGRESS",
      value: counts.ordersInProgress,
    },
    {
      title: <FormattedMessage id="dashboard.awaiting.sample.label" />,
      subTitle: (
        <FormattedMessage id="dashboard.awaiting.sample.subtitle.label" />
      ),
      type: "AWAITING_SAMPLE",
      value: counts.awaitingSample,
    },
    {
      title: <FormattedMessage id="dashboard.awaiting.results.label" />,
      subTitle: (
        <FormattedMessage id="dashboard.awaiting.results.subtitle.label" />
      ),
      type: "AWAITING_RESULTS",
      value: counts.awaitingResults,
    },
    {
      title: <FormattedMessage id="dashboard.validation.ready.label" />,
      subTitle: (
        <FormattedMessage id="dashboard.validation.ready.subtitle.label" />
      ),
      type: "ORDERS_READY_FOR_VALIDATION",
      value: counts.ordersReadyForValidation,
    },
    {
      title: <FormattedMessage id="dashboard.complete.orders.label" />,
      subTitle: <FormattedMessage id="dashboard.orders.subtitle.label" />,
      type: "ORDERS_COMPLETED_TODAY",
      value: counts.ordersCompletedToday,
    },
    {
      title: <FormattedMessage id="dashboard.partially.completed.label" />,
      subTitle: (
        <FormattedMessage id="dashboard.partially.completed..subtitle.label" />
      ),
      type: "ORDERS_PATIALLY_COMPLETED_TODAY",
      value: counts.patiallyCompletedToday,
    },
    {
      title: <FormattedMessage id="dashboard.user.orders.label" />,
      subTitle: <FormattedMessage id="dashboard.user.orders.subtitle.label" />,
      type: "ORDERS_ENTERED_BY_USER_TODAY",
      value: counts.orderEnterdByUserToday,
    },
    {
      title: <FormattedMessage id="dashboard.rejected.orders" />,
      subTitle: <FormattedMessage id="dashboard.rejected.orders.subtitle" />,
      type: "ORDERS_REJECTED_TODAY",
      value: counts.ordersRejectedToday,
    },
    {
      title: <FormattedMessage id="dashboard.unprints.results.label" />,
      subTitle: (
        <FormattedMessage id="dashboard.unprints.results.subtitle.label" />
      ),
      type: "UN_PRINTED_RESULTS",
      value: counts.unPritendResults,
    },
    {
      title: <FormattedMessage id="sidenav.label.incomingorder" />,
      subTitle: <FormattedMessage id="label.electronic.orders" />,
      type: "INCOMING_ORDERS",
      value: counts.incomigOrders,
    },
    {
      title: <FormattedMessage id="dashboard.avg.turn.around.label" />,
      subTitle: (
        <FormattedMessage id="dashboard.avg.turn.around.subtitle.label" />
      ),
      type: "AVERAGE_TURN_AROUND_TIME",
      value: counts.averageTurnAroudTime,
    },
    {
      title: <FormattedMessage id="dashboard.turn.around.label" />,
      subTitle: <FormattedMessage id="dashboard.turn.around.subtitle.label" />,
      type: "DELAYED_TURN_AROUND",
      value: counts.delayedTurnAround,
    },
  ];

  const tileList = allTiles.filter((tile) => {
    if (tileVisibility[tile.type] !== undefined) {
      return tileVisibility[tile.type];
    }
    return true;
  });

  const averageTimeTileList: Array<Tile> = [
    {
      title: (
        <FormattedMessage id="dashboard.avg.turn.around.reception.validation" />
      ),
      subTitle: (
        <FormattedMessage id="dashboard.avg.turn.around.reception.validation" />
      ),
      type: "AVERAGE_TURN_AROUND_TIME",
      value: timeMetrics.receptionToValidation,
    },
    {
      title: (
        <FormattedMessage id="dashboard.avg.turn.around.reception.result" />
      ),
      subTitle: (
        <FormattedMessage id="dashboard.avg.turn.around.reception.result" />
      ),
      type: "AVERAGE_TURN_AROUND_TIME",
      value: timeMetrics.receptionToResult,
    },
    {
      title: (
        <FormattedMessage id="dashboard.avg.turn.around.result.validation" />
      ),
      subTitle: (
        <FormattedMessage id="dashboard.avg.turn.around.result.validation" />
      ),
      type: "AVERAGE_TURN_AROUND_TIME",
      value: timeMetrics.resultToValidation,
    },
  ];

  const tilesWithTabs = [
    "ORDERS_IN_PROGRESS",
    "AWAITING_SAMPLE",
    "AWAITING_RESULTS",
    "ORDERS_READY_FOR_VALIDATION",
    "ORDERS_COMPLETED_TODAY",
    "ORDERS_REJECTED_TODAY",
    "UN_PRINTED_RESULTS",
    "DELAYED_TURN_AROUND",
    "ORDERS_FOR_USER",
    "ORDERS_PATIALLY_COMPLETED_TODAY",
  ];

  const handleMinimizeClick = () => {
    console.log("Icon clicked!");

    setStartDate("");
    setEndDate("");
    setSelectedTestType("all");

    if (selectedTile.type == "ORDERS_FOR_USER") {
      const tile: Tile = {
        title: <FormattedMessage id="dashboard.user.orders.label" />,
        subTitle: (
          <FormattedMessage id="dashboard.user.orders.subtitle.label" />
        ),
        type: "ORDERS_ENTERED_BY_USER_TODAY",
        value: counts.orderEnterdByUserToday,
      };
      setSelectedTile(tile);
      setSelectedTestType("all");
    } else {
      setSelectedTile(null);
      hasRole(userSessionDetails, "Global Administrator")
        ? setSelectedTestSection("all")
        : setSelectedTestSection(
            testSections &&
              testSections.length > 0 &&
              testSections[0]?.id != null
              ? String(testSections[0].id)
              : "",
          );
    }
  };

  const handleMaximizeClick = (tile) => {
    if (
      testSections?.length > 0 ||
      hasRole(userSessionDetails, "Global Administrator")
    ) {
      setSelectedTile(tile);
    } else {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "accessDenied.title" }),
        message: intl.formatMessage({ id: "accessDenied.message" }),
      });
    }
  };

  const viewUserOrders = (row) => {
    console.log("Icon clicked!");
    const rowCells = Array.isArray(row?.cells) ? row.cells : [];
    const firstName = rowCells.find(
      (e) => e.info.header === "userFirstName",
    )?.value;
    const lastName = rowCells.find(
      (e) => e.info.header === "userLastName",
    )?.value;
    const value = rowCells.find(
      (e) => e.info.header === "countOfOrdersEntered",
    )?.value;

    const tile: Tile = {
      title: <FormattedMessage id="dashboard.user.orders.today.label" />,
      subTitle: `${firstName || ""} ${lastName || ""}`.trim(),
      type: "ORDERS_FOR_USER",
      value: value || 0,
      id: row.id,
    };
    setSelectedTile(tile);
  };

  const handlePageChange = (pageInfo) => {
    if (page != pageInfo.page) {
      setPage(pageInfo.page);
    }

    if (pageSize != pageInfo.pageSize) {
      setPageSize(pageInfo.pageSize);
    }
  };

  const buildTileHref = (
    tileType: MetricType,
    searchValue: string,
  ): string | null => {
    if (!searchValue) {
      return null;
    }
    if (tileType === "ORDERS_IN_PROGRESS") {
      return "/ModifyOrder?accessionNumber=" + searchValue;
    }
    if (tileType === "AWAITING_SAMPLE") {
      return "/SampleManagement?accessionNumber=" + searchValue;
    }
    if (tileType === "AWAITING_RESULTS") {
      return "/result?type=order&doRange=false&accessionNumber=" + searchValue;
    }
    if (tileType === "ORDERS_READY_FOR_VALIDATION") {
      return "validation?type=order&accessionNumber=" + searchValue;
    }
    return null;
  };

  const renderCell = (cell, row) => {
    if (cell.info.header === "labNumber" && cell.value) {
      const accessionHref = buildTileHref(selectedTile.type, cell.value);
      const shouldLink =
        selectedTile.type == "ORDERS_IN_PROGRESS" ||
        selectedTile.type == "AWAITING_SAMPLE" ||
        selectedTile.type == "AWAITING_RESULTS" ||
        selectedTile.type == "ORDERS_READY_FOR_VALIDATION";
      return (
        <TableCell key={cell.id}>
          <>
            <div style={{ display: "flex", alignItems: "center" }}>
              <Button
                onClick={async () => {
                  if ("clipboard" in navigator) {
                    return await navigator.clipboard.writeText(cell.value);
                  } else {
                    return document.execCommand("copy", true, cell.value);
                  }
                }}
                kind="ghost"
                iconDescription={intl.formatMessage({
                  id: "instructions.copy.labnum",
                })}
                hasIconOnly
                renderIcon={Copy}
              />
              {shouldLink && accessionHref ? (
                <Link style={{ color: "blue" }} href={accessionHref}>
                  <u>{convertAlphaNumLabNumForDisplay(cell.value)}</u>
                </Link>
              ) : (
                <> {convertAlphaNumLabNumForDisplay(cell.value)}</>
              )}
            </div>
          </>
        </TableCell>
      );
    } else if (cell.info.header === "cugCode") {
      const cugHref = buildTileHref(selectedTile.type, cell.value);
      const shouldLink =
        selectedTile.type == "ORDERS_IN_PROGRESS" ||
        selectedTile.type == "AWAITING_SAMPLE" ||
        selectedTile.type == "AWAITING_RESULTS" ||
        selectedTile.type == "ORDERS_READY_FOR_VALIDATION";
      return (
        <TableCell key={cell.id}>
          {cell.value ? (
            shouldLink && cugHref ? (
              <Link style={{ color: "blue" }} href={cugHref}>
                <u>{cell.value}</u>
              </Link>
            ) : (
              cell.value
            )
          ) : (
            ""
          )}
        </TableCell>
      );
    } else if (cell.info.header === "countOfOrdersEntered" && cell.value) {
      return (
        <TableCell key={cell.id}>
          <Link style={{ color: "blue" }}>{cell.value} </Link>
        </TableCell>
      );
    } else if (
      cell.info.header === "waitingCounter" ||
      cell.info.header === "orderWaitingCounter"
    ) {
      const [, tagType, ...labelParts] = String(
        cell.value || `${Number.MAX_SAFE_INTEGER}|gray|Sin fecha`,
      ).split("|");
      const label = labelParts.join("|");

      return (
        <TableCell key={cell.id}>
          <Tag type={tagType as any} className="awaiting-results-counter-tag">
            {label}
          </Tag>
        </TableCell>
      );
    } else {
      return <TableCell key={cell.id}>{cell.value}</TableCell>;
    }
  };

  const orderHeadersInProgress = [
    {
      key: "priority",
      header: <FormattedMessage id="eorder.priority" />,
    },
    {
      key: "orderDate",
      header: <FormattedMessage id="sample.label.orderdate" />,
    },
    {
      key: "patientId",
      header: <FormattedMessage id="patient.merge.nationalId" />,
    },
    {
      key: "labNumber",
      header: <FormattedMessage id="eorder.labNumber" />,
    },
  ];

  const orderHeadersWithTest = [
    {
      key: "priority",
      header: <FormattedMessage id="eorder.priority" />,
    },
    {
      key: "orderDate",
      header: <FormattedMessage id="sample.label.orderdate" />,
    },
    {
      key: "patientId",
      header: <FormattedMessage id="patient.merge.nationalId" />,
    },
    {
      key: "labNumber",
      header: <FormattedMessage id="eorder.labNumber" />,
    },
    {
      key: "cugCode",
      header: <FormattedMessage id="sample.management.table.header.cug" />,
    },
    {
      key: "testName",
      header: <FormattedMessage id="eorder.test.name" />,
    },
  ];

  const orderHeadersAwaitingResults = [
    ...orderHeadersWithTest,
    {
      key: "waitingCounter",
      header: (
        <FormattedMessage
          id="dashboard.awaitingResults.waitingCounterFromReception"
          defaultMessage="Tiempo esperando desde la recepción de la muestra"
        />
      ),
    },
  ];

  const orderHeadersAwaitingSample = [
    {
      key: "priority",
      header: <FormattedMessage id="eorder.priority" />,
    },
    {
      key: "orderDate",
      header: <FormattedMessage id="sample.label.orderdate" />,
    },
    {
      key: "patientId",
      header: <FormattedMessage id="patient.merge.nationalId" />,
    },
    {
      key: "labNumber",
      header: <FormattedMessage id="eorder.labNumber" />,
    },
    {
      key: "cugCode",
      header: <FormattedMessage id="sample.management.table.header.cug" />,
    },
    {
      key: "waitingCounter",
      header: (
        <FormattedMessage
          id="dashboard.awaitingSample.waitingCounterFromOrder"
          defaultMessage="Tiempo esperando desde la orden"
        />
      ),
    },
  ];

  const orderHeadersReadyForValidation = [
    ...orderHeadersWithTest,
    {
      key: "waitingCounter",
      header: (
        <FormattedMessage
          id="dashboard.readyForValidation.waitingCounterFromResults"
          defaultMessage="Tiempo esperando desde el ingreso de resultados"
        />
      ),
    },
    {
      key: "orderWaitingCounter",
      header: (
        <FormattedMessage
          id="dashboard.readyForValidation.totalWaitingCounterFromOrder"
          defaultMessage="Tiempo total desde la orden"
        />
      ),
    },
  ];

  const userHeaders = [
    {
      key: "userFirstName",
      header: "First Name",
    },
    {
      key: "userLastName",
      header: "Last Name",
    },
    {
      key: "countOfOrdersEntered",
      header: "Orders Entered",
    },
  ];

  const testTypeOptions = Array.from(
    new Set(
      (data || [])
        .map((item) => item?.testName)
        .filter((name) => typeof name === "string" && name.trim().length > 0),
    ),
  ).sort((a, b) => a.localeCompare(b));

  const parseOrderDateTime = (orderDate?: string): Date | null => {
    if (!orderDate) {
      return null;
    }

    const normalizedDate = String(orderDate).trim().replace(" ", "T");
    const parsedDate = new Date(normalizedDate);

    if (Number.isNaN(parsedDate.getTime())) {
      return null;
    }

    return parsedDate;
  };

  const getWaitingInfo = (waitingStartDate?: string) => {
    const parsedOrderDate = parseOrderDateTime(waitingStartDate);

    if (!parsedOrderDate) {
      return {
        label: intl.formatMessage({
          id: "dashboard.awaitingResults.waiting.noDate",
          defaultMessage: "Sin fecha",
        }),
        tagType: "gray",
        waitingMs: -1,
      };
    }

    const now = new Date();
    const waitingMs = Math.max(0, now.getTime() - parsedOrderDate.getTime());
    const totalHours = Math.floor(waitingMs / (1000 * 60 * 60));
    const totalDays = Math.floor(totalHours / 24);

    const label =
      totalHours < 24
        ? intl.formatMessage(
            {
              id: "dashboard.awaitingResults.waiting.hours",
              defaultMessage:
                "hace {count, plural, one {# hora} other {# horas}}",
            },
            { count: totalHours },
          )
        : intl.formatMessage(
            {
              id: "dashboard.awaitingResults.waiting.days",
              defaultMessage:
                "hace {count, plural, one {# día} other {# días}}",
            },
            { count: totalDays },
          );

    let tagType = "green";

    if (totalDays >= 10) {
      tagType = "red";
    } else if (totalDays >= 5) {
      tagType = "yellow";
    }

    return {
      label,
      tagType,
      waitingMs,
    };
  };

  const serializeWaitingCounterValue = (
    waitingMs?: number,
    tagType?: string,
    label?: string,
  ) => {
    const normalizedWaitingMs =
      typeof waitingMs === "number" && waitingMs >= 0
        ? waitingMs
        : Number.MAX_SAFE_INTEGER;
    const sortableValue = String(normalizedWaitingMs).padStart(16, "0");
    return `${sortableValue}|${tagType || "gray"}|${label || ""}`;
  };

  const matchesSelectedFilters = (item) => {
    const shouldFilterBySection =
      tilesWithTabs.includes(selectedTile.type) &&
      selectedTestSection != "all" &&
      selectedTestSection !== "";

    const matchesSection = (() => {
      if (!shouldFilterBySection) {
        return true;
      }
      const rawSectionValue = String(item?.testSection ?? "").trim();
      if (!rawSectionValue) {
        return true;
      }
      const sectionIds = rawSectionValue
        .split(",")
        .map((value) => value.trim())
        .filter((value) => value.length > 0);
      return sectionIds.includes(String(selectedTestSection));
    })();

    const matchesTestType =
      supportsTestTypeFilter && selectedTestType !== "all"
        ? item?.testName === selectedTestType
        : true;

    return matchesSection && matchesTestType;
  };

  const filteredTableData =
    selectedTile != null
      ? data
          .filter((item) => matchesSelectedFilters(item))
          .map((item) => {
            if (!supportsWaitingCounter) {
              return item;
            }

            const waitingInfo = getWaitingInfo(
              item.waitingStartDate || item.orderDate,
            );

            const orderWaitingInfo =
              selectedTile?.type === "ORDERS_READY_FOR_VALIDATION"
                ? getWaitingInfo(item.orderCreatedStartDate || item.orderDate)
                : null;

            return {
              ...item,
              waitingMs: waitingInfo.waitingMs,
              waitingCounter: serializeWaitingCounterValue(
                waitingInfo.waitingMs,
                waitingInfo.tagType,
                waitingInfo.label,
              ),
              ...(orderWaitingInfo
                ? {
                    orderWaitingMs: orderWaitingInfo.waitingMs,
                    orderWaitingCounter: serializeWaitingCounterValue(
                      orderWaitingInfo.waitingMs,
                      orderWaitingInfo.tagType,
                      orderWaitingInfo.label,
                    ),
                  }
                : {}),
            };
          })
          .sort((a, b) => {
            if (!supportsWaitingCounter) {
              return 0;
            }

            const firstWaitingMs = a.waitingMs ?? -1;
            const secondWaitingMs = b.waitingMs ?? -1;

            if (waitingTimeSort === "mostUrgent") {
              return secondWaitingMs - firstWaitingMs;
            }

            return firstWaitingMs - secondWaitingMs;
          })
      : [];

  return (
    <>
      {loading && <Loading description="Loading Dasboard..." />}
      {notificationVisible === true ? <AlertDialog /> : ""}
      {selectedTile == null ? (
        <div className="home-dashboard-container">
          {tileList.map((tile, index) => (
            <ClickableTile
              key={index}
              className="dashboard-tile"
              onClick={() => handleMaximizeClick(tile)}
            >
              <h3 className="tile-title">{tile.title}</h3>
              <p className="tile-subtitle">{tile.subTitle}</p>
              <p className="tile-value">
                {tile.type === "AVERAGE_TURN_AROUND_TIME"
                  ? Number(tile.value || 0).toFixed(2)
                  : tile.value}
              </p>

              <div className="tile-icon">
                <div
                  onClick={() => handleMaximizeClick(tile)}
                  className="icon-wrapper"
                >
                  <Maximize
                    id="maximizeIcon"
                    size={20}
                    className="clickable-icon"
                  />
                </div>
              </div>
            </ClickableTile>
          ))}
        </div>
      ) : (
        <div className="dashboard-view">
          <Tile className="dashboard-tile">
            <Grid>
              <Column lg={16} md={8} sm={4}>
                <h3 className="tile-title-view">{selectedTile.title}</h3>
                <p className="tile-subtitle-view">{selectedTile.subTitle}</p>
                <p className="tile-value-view">
                  {selectedTile.type === "AVERAGE_TURN_AROUND_TIME"
                    ? Number(selectedTile.value || 0).toFixed(2)
                    : selectedTile.value}
                </p>
                {
                  <div className="tile-icon">
                    <div onClick={handleMinimizeClick} className="icon-wrapper">
                      <Minimize
                        id="minimizeIcon"
                        size={20}
                        className="clickable-icon"
                      />
                    </div>
                  </div>
                }
              </Column>
            </Grid>
            {selectedTile.type !== "AVERAGE_TURN_AROUND_TIME" && (
              <div
                style={{
                  display: "flex",
                  flexWrap: "wrap",
                  gap: "1rem",
                  alignItems: "flex-end",
                  padding: "1rem 0",
                }}
              >
                <div style={{ minWidth: "25rem" }}>
                  <DatePicker
                    datePickerType="range"
                    onChange={handleDateChange}
                    dateFormat="Y-m-d"
                  >
                    <DatePickerInput
                      id="date-picker-start"
                      placeholder="yyyy-mm-dd"
                      labelText={intl.formatMessage({
                        id: "dashboard.filter.startDate",
                        defaultMessage: "Fecha inicio",
                      })}
                      size="md"
                    />
                    <DatePickerInput
                      id="date-picker-end"
                      placeholder="yyyy-mm-dd"
                      labelText={intl.formatMessage({
                        id: "dashboard.filter.endDate",
                        defaultMessage: "Fecha fin",
                      })}
                      size="md"
                    />
                  </DatePicker>
                </div>

                {supportsWaitingCounter && (
                  <div style={{ minWidth: "18rem", maxWidth: "22rem" }}>
                    <Select
                      id="awaiting-results-priority-sort"
                      labelText={intl.formatMessage({
                        id: "dashboard.awaitingResults.sort.label",
                        defaultMessage: "Ordenar por tiempo esperando",
                      })}
                      value={waitingTimeSort}
                      onChange={(event) => {
                        setWaitingTimeSort(event.target.value);
                        setPage(1);
                      }}
                    >
                      <SelectItem
                        value="mostUrgent"
                        text={intl.formatMessage({
                          id: "dashboard.awaitingResults.sort.mostUrgent",
                          defaultMessage: "Más urgente primero",
                        })}
                      />
                      <SelectItem
                        value="lessUrgent"
                        text={intl.formatMessage({
                          id: "dashboard.awaitingResults.sort.lessUrgent",
                          defaultMessage: "Menos urgente primero",
                        })}
                      />
                    </Select>
                  </div>
                )}

                {supportsTestTypeFilter && (
                  <div style={{ minWidth: "18rem", maxWidth: "22rem" }}>
                    <Select
                      id="dashboard-test-type-filter"
                      labelText={intl.formatMessage({
                        id: "dashboard.filter.test.type",
                      })}
                      value={selectedTestType}
                      onChange={(event) => {
                        setSelectedTestType(event.target.value);
                        setPage(1);
                      }}
                    >
                      <SelectItem
                        value="all"
                        text={intl.formatMessage({
                          id: "dashboard.filter.test.type.all",
                        })}
                      />
                      {testTypeOptions.map((testName) => (
                        <SelectItem
                          key={testName}
                          value={testName}
                          text={testName}
                        />
                      ))}
                    </Select>
                  </div>
                )}
              </div>
            )}
            <div className="gridBoundary">
              {selectedTile.type == "AVERAGE_TURN_AROUND_TIME" ? (
                <>
                  <div className="home-dashboard-container">
                    {averageTimeTileList.map((tile, index) => (
                      <Tile key={index} className="dashboard-tile">
                        <h3 className="tile-title">{tile.title}</h3>
                        <p className="tile-subtitle">{tile.subTitle}</p>
                        <p className="tile-value">
                          {Number(tile.value).toFixed(2)}
                        </p>
                      </Tile>
                    ))}
                  </div>
                </>
              ) : (
                <Grid>
                  <Column lg={16} md={8} sm={4}>
                    {pagination && (
                      <Grid>
                        <Column lg={14} />
                        <Column
                          lg={2}
                          style={{
                            display: "flex",
                            flexDirection: "column",
                            alignItems: "center",
                            gap: "10px",
                            width: "110%",
                          }}
                        >
                          <Link>
                            {currentApiPage} / {totalApiPages}
                          </Link>
                          <div style={{ display: "flex", gap: "10px" }}>
                            <Button
                              hasIconOnly
                              id="loadpreviousresults"
                              onClick={loadPreviousResultsPage}
                              disabled={previousPage != null ? false : true}
                              renderIcon={ArrowLeft}
                              iconDescription="previous"
                            ></Button>
                            <Button
                              hasIconOnly
                              id="loadnextresults"
                              onClick={loadNextResultsPage}
                              disabled={nextPage != null ? false : true}
                              renderIcon={ArrowRight}
                              iconDescription="next"
                            ></Button>
                          </div>
                        </Column>
                      </Grid>
                    )}
                    {tilesWithTabs.includes(selectedTile.type) && (
                      <Grid>
                        <Column lg={16} md={8} sm={4}>
                          <Tabs>
                            {hasRole(
                              userSessionDetails,
                              "Global Administrator",
                            ) ? (
                              <TabList
                                style={{ width: "100%" }}
                                aria-label="List of tabs"
                                contained
                              >
                                <Tab
                                  onClick={() => setSelectedTestSection("all")}
                                >
                                  <FormattedMessage id="all.label" />
                                </Tab>

                                {testSections?.map((item, id) => {
                                  return (
                                    <Tab
                                      key={id}
                                      onClick={() =>
                                        setSelectedTestSection(String(item.id))
                                      }
                                    >
                                      {item.value}
                                    </Tab>
                                  );
                                })}
                              </TabList>
                            ) : (
                              <TabList
                                style={{ width: "100%" }}
                                aria-label="List of tabs"
                                contained
                              >
                                {testSections?.map((item, id) => {
                                  return (
                                    <Tab
                                      key={id}
                                      onClick={() =>
                                        setSelectedTestSection(String(item.id))
                                      }
                                    >
                                      {item.value}
                                    </Tab>
                                  );
                                })}
                              </TabList>
                            )}
                          </Tabs>
                        </Column>
                      </Grid>
                    )}
                    <DataTable
                      rows={filteredTableData.slice(
                        (page - 1) * pageSize,
                        page * pageSize,
                      )}
                      headers={
                        selectedTile.type === "AWAITING_RESULTS"
                          ? orderHeadersAwaitingResults
                          : selectedTile.type === "ORDERS_READY_FOR_VALIDATION"
                            ? orderHeadersReadyForValidation
                            : [
                                  "ORDERS_IN_PROGRESS",
                                  "ORDERS_COMPLETED_TODAY",
                                  "ORDERS_PATIALLY_COMPLETED_TODAY",
                                  "ORDERS_REJECTED_TODAY",
                                  "ORDERS_FOR_USER",
                                  "UN_PRINTED_RESULTS",
                                  "INCOMING_ORDERS",
                                  "DELAYED_TURN_AROUND",
                                ].includes(selectedTile.type)
                              ? orderHeadersInProgress
                              : selectedTile.type ===
                                  "ORDERS_ENTERED_BY_USER_TODAY"
                                ? userHeaders
                                : selectedTile.type === "AWAITING_SAMPLE"
                                  ? orderHeadersAwaitingSample
                                  : orderHeadersWithTest
                      }
                      isSortable
                    >
                      {({ rows, headers, getHeaderProps, getTableProps }) => (
                        <TableContainer title="" description="">
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
                                      selectedTile.type ==
                                      "ORDERS_ENTERED_BY_USER_TODAY"
                                        ? viewUserOrders(row)
                                        : {};
                                    }}
                                  >
                                    {row.cells.map((cell) =>
                                      renderCell(cell, row),
                                    )}
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
                      pageSizes={[10, 20, 30, 50, 100]}
                      totalItems={filteredTableData.length}
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
              )}
            </div>
          </Tile>
        </div>
      )}
    </>
  );
};
export default HomeDashBoard;
