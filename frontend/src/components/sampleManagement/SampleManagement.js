import React, { useContext, useState } from "react";
import {
  Grid,
  Column,
  Section,
  Heading,
  InlineNotification,
  Button,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  AlertDialog,
  NotificationKinds,
} from "../common/CustomNotification";
import { NotificationContext } from "../layout/Layout";
import PageBreadCrumb from "../common/PageBreadCrumb";
import SampleSearch from "./SampleSearch";
import SampleResultsTable from "./SampleResultsTable";
import CreateAliquotModal from "./CreateAliquotModal";
import AddTestsModal from "./AddTestsModal";
import { getFromOpenElisServer } from "../utils/Utils";
import "./SampleManagement.css";

/**
 * SampleManagement - Main container component for Sample Management feature.
 *
 * Features:
 * - Integrates SampleSearch and SampleResultsTable components
 * - Manages search results state
 * - Handles API errors with inline notifications
 * - Provides breadcrumb navigation
 * - Displays search metadata (accession number, result count)
 *
 * This component serves as the entry point for User Story 1: Search for sample
 * items by accession number and view results with hierarchy information.
 *
 * Related: Feature 001-sample-management, User Story 1, Task T035
 */
export default function SampleManagement() {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  // Breadcrumb navigation
  const breadcrumbs = [
    { label: "home.label", link: "/" },
    { label: "menu.genericSample" },
    { label: "banner.menu.sampleManagement" },
  ];

  // Search results state
  const [searchResponse, setSearchResponse] = useState(null);
  const [searchError, setSearchError] = useState(null);
  const [selectedSampleIds, setSelectedSampleIds] = useState([]);
  const [currentTestsVisibleBySampleId, setCurrentTestsVisibleBySampleId] =
    useState({});

  // Modal state for aliquoting
  const [isAliquotModalOpen, setIsAliquotModalOpen] = useState(false);

  // Modal state for adding tests
  const [isAddTestsModalOpen, setIsAddTestsModalOpen] = useState(false);

  // Get selected sample for aliquoting (single selection only)
  const selectedSample =
    selectedSampleIds.length === 1
      ? searchResponse?.sampleItems?.find(
          (item) => item.id === selectedSampleIds[0],
        )
      : null;

  /**
   * Handle search results callback from SampleSearch component.
   *
   * @param {Object} response - SearchSamplesResponse from backend
   * @param {Object} error - Error object if search failed
   */
  const handleSearchResults = (response, error) => {
    if (response) {
      hydrateSearchResponse(response, (hydrated) => {
        setSearchResponse(hydrated);
      });
    } else {
      setSearchResponse(response);
    }
    setSearchError(error);

    // Clear selection when new search results arrive
    setSelectedSampleIds([]);
    setCurrentTestsVisibleBySampleId({});
  };

  /**
   * Handle row selection changes from SampleResultsTable component.
   *
   * @param {Array<string>} selectedIds - Array of selected sample item IDs
   */
  const handleSelectionChange = (selectedIds) => {
    setSelectedSampleIds(selectedIds);
  };

  /**
   * Toggle Current Tests section visibility for selected samples.
   * Each sample keeps its own visibility state.
   */
  const handleToggleCurrentTests = () => {
    if (selectedSampleIds.length === 0) {
      return;
    }

    setCurrentTestsVisibleBySampleId((prev) => {
      const areAllVisible = selectedSampleIds.every((id) => prev[id]);
      const next = { ...prev };

      selectedSampleIds.forEach((id) => {
        next[id] = !areAllVisible;
      });

      return next;
    });
  };

  /**
   * Clear error notification.
   */
  const handleDismissError = () => {
    setSearchError(null);
  };

  /**
   * Close aliquot modal.
   */
  const handleCloseAliquotModal = () => {
    setIsAliquotModalOpen(false);
  };

  /**
   * Handle successful aliquot creation.
   */
  const handleAliquotSuccess = (response) => {
    // Refresh search results to show the new aliquot(s)
    if (searchResponse && searchResponse.accessionNumber) {
      handleSearchResults(null, null); // Clear current results
      // Trigger a re-search by calling the search API directly
      // Note: In production, you might want to add a refresh mechanism
      // For now, we'll just show a success message and user can re-search

      // Handle both single and multiple aliquot creation
      const aliquotCount = response.aliquotCount || 1;
      let message;
      if (aliquotCount > 1) {
        // Multiple aliquots created
        const externalIds = response.aliquots
          .map((a) => a.externalId)
          .join(", ");
        message = intl.formatMessage(
          { id: "sample.management.aliquot.successMultiple" },
          { count: aliquotCount, externalIds: externalIds },
        );
      } else {
        // Single aliquot created
        message = intl.formatMessage(
          { id: "sample.management.aliquot.success" },
          { externalId: response.aliquot.externalId },
        );
      }

      setSearchError({
        message: message,
        kind: "success",
      });
    }
  };

  /**
   * Close add tests modal.
   */
  const handleCloseAddTestsModal = () => {
    setIsAddTestsModalOpen(false);
  };

  /**
   * Handle successful test addition.
   * Shows detailed per-sample/aliquot breakdown in the success message.
   */
  const handleAddTestsSuccess = (response) => {
    // Calculate totals
    const totalSkipped = response.results.reduce(
      (sum, r) => sum + (r.skippedTestIds ? r.skippedTestIds.length : 0),
      0,
    );

    // Check if we have multiple samples with detailed results
    const hasDetailedResults = response.results && response.results.length > 1;

    // Build detailed per-sample breakdown for multiple samples
    let detailedBreakdown = "";
    if (hasDetailedResults) {
      const sampleResults = response.results.map((result) => {
        const addedCount = result.addedTestIds ? result.addedTestIds.length : 0;
        const skippedCount = result.skippedTestIds
          ? result.skippedTestIds.length
          : 0;

        // Use external ID for display, fallback to sample item ID
        const displayId = result.sampleItemExternalId || result.sampleItemId;

        if (skippedCount > 0) {
          return intl.formatMessage(
            { id: "sample.management.addTests.resultItem.withSkipped" },
            { sampleId: displayId, added: addedCount, skipped: skippedCount },
          );
        } else {
          return intl.formatMessage(
            { id: "sample.management.addTests.resultItem" },
            { sampleId: displayId, added: addedCount },
          );
        }
      });

      detailedBreakdown = sampleResults.join("; ");
    }

    // Show success message
    let message;
    if (hasDetailedResults) {
      // Detailed message with per-sample breakdown
      if (totalSkipped > 0) {
        message = intl.formatMessage(
          { id: "sample.management.addTests.successDetailedWithSkipped" },
          {
            added: response.successCount,
            skipped: totalSkipped,
            samples: response.results.length,
            details: detailedBreakdown,
          },
        );
      } else {
        message = intl.formatMessage(
          { id: "sample.management.addTests.successDetailed" },
          {
            count: response.successCount,
            samples: response.results.length,
            details: detailedBreakdown,
          },
        );
      }
    } else {
      // Simple message for single sample
      if (totalSkipped > 0) {
        message = intl.formatMessage(
          { id: "sample.management.addTests.successWithSkipped" },
          { added: response.successCount, skipped: totalSkipped },
        );
      } else {
        message = intl.formatMessage(
          { id: "sample.management.addTests.success" },
          { count: response.successCount },
        );
      }
    }

    setSearchError({
      message: message,
      kind: "success",
    });

    // Clear selection after adding tests
    setSelectedSampleIds([]);
  };

  /**
   * Handle test removal/cancellation from expanded row.
   * Updates local state to remove the test from the sample item.
   */
  const handleTestRemoved = (sampleItemId, analysisId, testName) => {
    // Update local state to remove the cancelled test
    if (searchResponse && searchResponse.sampleItems) {
      const updatedSampleItems = searchResponse.sampleItems.map((item) => {
        if (item.id === sampleItemId) {
          return {
            ...item,
            orderedTests: item.orderedTests.filter(
              (test) => test.analysisId !== analysisId,
            ),
          };
        }
        return item;
      });

      setSearchResponse({
        ...searchResponse,
        sampleItems: updatedSampleItems,
      });
    }

    // Show success notification
    setSearchError({
      message: intl.formatMessage(
        { id: "sample.management.cancelTest.success" },
        { testName: testName },
      ),
      kind: "success",
    });
  };

  const handlePersistResult = (result) => {
    if (result?.success) {
      addNotification({
        kind: NotificationKinds.success,
        title: intl.formatMessage({
          id: "sample.management.success.title",
        }),
        message: result.message,
      });
      setNotificationVisible(true);
      setSearchError(null);

      if (searchResponse?.accessionNumber) {
        const currentUrl = new URL(window.location.href);
        currentUrl.searchParams.set(
          "accessionNumber",
          searchResponse.accessionNumber,
        );
        window.location.assign(currentUrl.toString());
        return;
      }
    } else {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({
          id: "sample.management.error.title",
        }),
        message: result?.message || "Error saving sample changes",
      });
      setNotificationVisible(true);
    }
  };

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}

      {/* Breadcrumb Navigation */}
      <PageBreadCrumb breadcrumbs={breadcrumbs} />

      {/* Page Header */}
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              <FormattedMessage
                id="sample.management.title"
                defaultMessage="Sample Management"
              />
            </Heading>
          </Section>
        </Column>
      </Grid>

      <div
        className="orderLegendBody"
        style={{
          width: "min(96%, 1400px)",
          margin: "0 auto",
        }}
      >
        {/* Inline search error */}
        {searchError?.message && !searchError?.kind && (
          <Grid fullWidth={true}>
            <Column lg={16} md={8} sm={4}>
              <InlineNotification
                kind="error"
                title={intl.formatMessage({
                  id: "sample.management.error.title",
                })}
                subtitle={searchError.message}
                onClose={handleDismissError}
              />
            </Column>
          </Grid>
        )}

        {/* Search Section */}
        <div className="sample-management-card sample-management-search-card">
          <div className="sample-management-section-header">
            <Section>
              <Heading>
                <FormattedMessage
                  id="sample.management.search.title"
                  defaultMessage="Search Samples"
                />
              </Heading>
            </Section>
          </div>

          <SampleSearch
            onSearchResults={handleSearchResults}
            includeTests={true}
          />
        </div>

        {/* Empty State (when search has been performed but no results) */}
        {searchResponse &&
          searchResponse.sampleItems &&
          searchResponse.sampleItems.length === 0 && (
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <InlineNotification
                  kind="info"
                  title={intl.formatMessage({
                    id: "sample.management.noResults.title",
                  })}
                  subtitle={intl.formatMessage(
                    { id: "sample.management.noResults.subtitle" },
                    { accessionNumber: searchResponse.accessionNumber },
                  )}
                  hideCloseButton
                />
              </Column>
            </Grid>
          )}

        {/* Action Buttons */}
        {searchResponse &&
          searchResponse.sampleItems &&
          searchResponse.sampleItems.length > 0 &&
          selectedSampleIds.length > 0 && (
            <Grid fullWidth={true}>
              <Column lg={16} md={8} sm={4}>
                <div className="sample-management-action-bar">
                  {/* Toggle Current Tests Section */}
                  <Button kind="tertiary" onClick={handleToggleCurrentTests}>
                    <FormattedMessage
                      id="sample.management.action.currentSample"
                      defaultMessage="Current Sample"
                    />
                  </Button>
                </div>
              </Column>
            </Grid>
          )}

        {/* Results Table Section */}
        {searchResponse &&
          searchResponse.sampleItems &&
          searchResponse.sampleItems.length > 0 && (
            <>
              <div className="sample-management-results-card">
                <div className="sample-management-results-header">
                  <Section>
                    <Heading>
                      <FormattedMessage
                        id="sample.management.results.title"
                        defaultMessage="Sample Items"
                      />
                    </Heading>
                  </Section>
                </div>

                <div className="sample-management-results-table">
                  <SampleResultsTable
                    sampleItems={searchResponse.sampleItems}
                    onSelectionChange={handleSelectionChange}
                    onTestRemoved={handleTestRemoved}
                    currentTestsVisibleBySampleId={currentTestsVisibleBySampleId}
                    onPersistResult={handlePersistResult}
                  />
                </div>
              </div>
            </>
          )}
      </div>

      {/* Create Aliquot Modal */}
      {selectedSample && (
        <CreateAliquotModal
          open={isAliquotModalOpen}
          onClose={handleCloseAliquotModal}
          parentSample={selectedSample}
          onSuccess={handleAliquotSuccess}
        />
      )}

      {/* Add Tests Modal */}
      <AddTestsModal
        open={isAddTestsModalOpen}
        onClose={handleCloseAddTestsModal}
        selectedSampleIds={selectedSampleIds}
        selectedSamples={
          searchResponse?.sampleItems?.filter((item) =>
            selectedSampleIds.includes(item.id),
          ) || []
        }
        onSuccess={handleAddTestsSuccess}
      />
    </>
  );
}

const normalizeOrderFieldKey = (value) =>
  String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+/, "");

const parseFieldMetadata = (metadataJson) => {
  if (!metadataJson || typeof metadataJson !== "string") {
    return {};
  }
  try {
    const parsed = JSON.parse(metadataJson);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (_error) {
    return {};
  }
};

const mapValueFromIdList = (collection, rawValue) => {
  if (
    !Array.isArray(collection) ||
    rawValue === undefined ||
    rawValue === null
  ) {
    return "";
  }
  const normalizedRaw = String(rawValue);
  const match = collection.find(
    (entry) => String(entry?.id ?? entry?.value ?? "") === normalizedRaw,
  );
  return match?.value || "";
};

const stringifyDisplayValue = (value) => {
  if (value === undefined || value === null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map((entry) => stringifyDisplayValue(entry)).join(", ");
  }
  if (typeof value === "object") {
    return (
      value?.value ||
      value?.label ||
      value?.name ||
      (value?.id !== undefined && value?.id !== null ? String(value.id) : "")
    );
  }
  return "";
};

const resolveFixedFieldDisplayValue = (sampleOrderItems, fieldKey) => {
  const rawValue = sampleOrderItems?.[fieldKey];
  if (rawValue === undefined || rawValue === null || rawValue === "") {
    return "";
  }

  if (fieldKey === "priority") {
    return (
      mapValueFromIdList(sampleOrderItems?.priorityList, rawValue) ||
      stringifyDisplayValue(rawValue)
    );
  }

  if (fieldKey === "referringSiteDepartmentId") {
    return (
      mapValueFromIdList(
        sampleOrderItems?.referringSiteDepartmentList,
        rawValue,
      ) || stringifyDisplayValue(rawValue)
    );
  }

  if (fieldKey === "paymentOptionSelection") {
    return (
      mapValueFromIdList(sampleOrderItems?.paymentOptions, rawValue) ||
      stringifyDisplayValue(rawValue)
    );
  }

  if (fieldKey === "testLocationCode") {
    return (
      mapValueFromIdList(sampleOrderItems?.testLocationCodeList, rawValue) ||
      stringifyDisplayValue(rawValue)
    );
  }

  return stringifyDisplayValue(rawValue);
};

const isCustomFieldShownInSampleReception = (field) => {
  const metadata = parseFieldMetadata(field?.metadataJson);
  return Boolean(
    metadata?.sampleReception?.showInSampleReception ||
      metadata?.showInSampleReception,
  );
};

const resolveCustomFieldDisplayValue = (field, valuesByKey, filesByKey) => {
  const fieldKey = String(field?.fieldKey || "");
  const fieldType = String(field?.fieldType || "TEXT").toUpperCase();

  if (fieldType === "DOCUMENT") {
    const filePayload = filesByKey?.[fieldKey];
    return (
      filePayload?.fileName || filePayload?.name || filePayload?.fileType || ""
    );
  }

  if (fieldType === "BOOLEAN") {
    const rawValue = valuesByKey?.[fieldKey];
    if (rawValue === undefined || rawValue === null || rawValue === "") {
      return "false";
    }
    return rawValue === true || String(rawValue).toLowerCase() === "true"
      ? "true"
      : "false";
  }

  const rawValue = valuesByKey?.[fieldKey];
  if (rawValue === undefined || rawValue === null || rawValue === "") {
    return "";
  }

  const options = Array.isArray(field?.options) ? field.options : [];
  if (
    fieldType === "SELECT" ||
    fieldType === "RADIO" ||
    fieldType === "USER"
  ) {
    const selected = options.find(
      (option) => String(option?.optionKey || "") === String(rawValue),
    );
    return selected?.optionLabel || String(rawValue);
  }

  if (fieldType === "MULTISELECT") {
    const selectedKeys = String(rawValue)
      .split(",")
      .map((entry) => entry.trim())
      .filter(Boolean);
    if (selectedKeys.length === 0) {
      return "";
    }
    const labels = selectedKeys.map((selectedKey) => {
      const selected = options.find(
        (option) => String(option?.optionKey || "") === selectedKey,
      );
      return selected?.optionLabel || selectedKey;
    });
    return labels.join(", ");
  }

  return String(rawValue);
};

const buildOrderReceptionFields = (sampleOrderItems) => {
  if (!sampleOrderItems || typeof sampleOrderItems !== "object") {
    return [];
  }

  const fixedConfigs = Array.isArray(sampleOrderItems.fixedFieldConfigs)
    ? sampleOrderItems.fixedFieldConfigs
    : [];
  const orderAdditionalFields = Array.isArray(sampleOrderItems.additionalFields)
    ? sampleOrderItems.additionalFields
    : [];
  const additionalFieldValues = sampleOrderItems.additionalFieldValues || {};
  const additionalFieldFiles = sampleOrderItems.additionalFieldFiles || {};

  const fixedFields = fixedConfigs
    .filter(
      (config) =>
        config?.fieldKey &&
        config?.showInSampleReception === true &&
        config?.visible !== false,
    )
    .sort((left, right) => (left?.sortOrder ?? 0) - (right?.sortOrder ?? 0))
    .map((config) => ({
      source: "fixed",
      fieldKey: config.fieldKey,
      displayName: config.fieldKey,
      fieldType: "TEXT",
      value: resolveFixedFieldDisplayValue(sampleOrderItems, config.fieldKey),
      sortOrder: config?.sortOrder ?? 0,
    }));

  const customFields = orderAdditionalFields
    .filter(
      (field) =>
        field?.fieldKey &&
        field?.active !== false &&
        isCustomFieldShownInSampleReception(field),
    )
    .sort((left, right) => (left?.sortOrder ?? 0) - (right?.sortOrder ?? 0))
    .map((field) => {
      const normalizedKey = normalizeOrderFieldKey(field.fieldKey);
      const directValue =
        resolveCustomFieldDisplayValue(
          field,
          additionalFieldValues,
          additionalFieldFiles,
        ) ||
        resolveCustomFieldDisplayValue(
          field,
          { [field.fieldKey]: additionalFieldValues?.[normalizedKey] },
          additionalFieldFiles,
        );
      return {
        source: "custom",
        fieldKey: field.fieldKey,
        displayName: field.displayName || field.fieldKey,
        fieldType: field.fieldType || "TEXT",
        value: directValue,
        sortOrder: field?.sortOrder ?? 0,
      };
    });

  return [...fixedFields, ...customFields];
};

const mergeWithSampleEditData = (searchResp, sampleEditResp) => {
  if (
    !searchResp ||
    !Array.isArray(searchResp.sampleItems) ||
    !sampleEditResp
  ) {
    return searchResp;
  }

  const existingTests = Array.isArray(sampleEditResp.existingTests)
    ? sampleEditResp.existingTests
    : [];
  const orderReceptionFields = buildOrderReceptionFields(
    sampleEditResp?.sampleOrderItems,
  );

  const bySampleItemId = existingTests.reduce((acc, test) => {
    const key = String(test.sampleItemId || "");
    if (!key) return acc;
    if (!acc[key]) acc[key] = [];
    acc[key].push(test);
    return acc;
  }, {});

  const mergedItems = searchResp.sampleItems.map((item) => {
    const sampleTests = bySampleItemId[String(item.id)] || [];
    if (sampleTests.length === 0) {
      return {
        ...item,
        orderReceptionFields,
      };
    }

    const first = sampleTests[0];
    const additionalFieldValues =
      first.additionalFieldValues &&
      Object.keys(first.additionalFieldValues).length > 0
        ? first.additionalFieldValues
        : item.additionalFieldValues || {};
    const additionalFields =
      Array.isArray(first.additionalFields) && first.additionalFields.length > 0
        ? first.additionalFields
        : item.additionalFields || [];

    return {
      ...item,
      quantityDisplay:
        first.quantity !== undefined &&
        first.quantity !== null &&
        first.quantity !== ""
          ? String(first.quantity)
          : item.quantityDisplay,
      quantity:
        first.quantity !== undefined &&
        first.quantity !== null &&
        first.quantity !== ""
          ? Number(first.quantity)
          : item.quantity,
      unitOfMeasureId:
        first.unitOfMeasureId !== undefined && first.unitOfMeasureId !== null
          ? String(first.unitOfMeasureId)
          : item.unitOfMeasureId,
      collector:
        first.collector !== undefined && first.collector !== null
          ? first.collector
          : item.collector,
      collectionDate: first.collectionDate || item.collectionDate,
      collectionTime: first.collectionTime || item.collectionTime,
      additionalFields,
      additionalFieldValues,
      orderReceptionFields,
    };
  });

  return {
    ...searchResp,
    sampleItems: mergedItems,
  };
};

const hydrateSearchResponse = (response, callback) => {
  if (!response?.accessionNumber) {
    callback(response);
    return;
  }

  getFromOpenElisServer(
    `/rest/SampleEdit?accessionNumber=${encodeURIComponent(response.accessionNumber)}`,
    (sampleEditResp) => {
      callback(mergeWithSampleEditData(response, sampleEditResp));
    },
  );
};
