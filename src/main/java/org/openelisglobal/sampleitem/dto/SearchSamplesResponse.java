/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 */
package org.openelisglobal.sampleitem.dto;

import java.util.ArrayList;
import java.util.List;
import org.openelisglobal.sample.bean.SampleOrderItem;

/**
 * Response object for sample search operations.
 *
 * <p>
 * Returns search results including the accession number queried, matching
 * sample items, and total count for pagination support.
 *
 * <p>
 * Related: Feature 001-sample-management, User Story 1
 */
public class SearchSamplesResponse {

    private String accessionNumber;
    private List<SampleItemDTO> sampleItems = new ArrayList<>();
    private SampleOrderItem orderReceptionDetails;
    private List<String> restrictedFieldGroupKeys = new ArrayList<>();
    private List<String> restrictedFieldTagKeys = new ArrayList<>();
    private boolean canRead;
    private boolean canComplete;
    private boolean canUpdate;
    private int totalCount;

    // ========== Constructors ==========

    public SearchSamplesResponse() {
    }

    public SearchSamplesResponse(String accessionNumber, List<SampleItemDTO> sampleItems, int totalCount) {
        this.accessionNumber = accessionNumber;
        this.sampleItems = sampleItems;
        this.totalCount = totalCount;
    }

    // ========== Getters and Setters ==========

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public List<SampleItemDTO> getSampleItems() {
        return sampleItems;
    }

    public void setSampleItems(List<SampleItemDTO> sampleItems) {
        this.sampleItems = sampleItems;
    }

    /**
     * Reception-only order data shown alongside the sample items.
     *
     * <p>
     * This keeps Sample Management self-contained instead of requiring the legacy
     * SampleEdit endpoint, which has Order permissions.
     */
    public SampleOrderItem getOrderReceptionDetails() {
        return orderReceptionDetails;
    }

    public void setOrderReceptionDetails(SampleOrderItem orderReceptionDetails) {
        this.orderReceptionDetails = orderReceptionDetails;
    }

    public List<String> getRestrictedFieldGroupKeys() {
        return restrictedFieldGroupKeys;
    }

    public void setRestrictedFieldGroupKeys(List<String> restrictedFieldGroupKeys) {
        this.restrictedFieldGroupKeys = restrictedFieldGroupKeys == null ? new ArrayList<>()
                : new ArrayList<>(restrictedFieldGroupKeys);
    }

    public List<String> getRestrictedFieldTagKeys() {
        return restrictedFieldTagKeys;
    }

    public void setRestrictedFieldTagKeys(List<String> restrictedFieldTagKeys) {
        this.restrictedFieldTagKeys = restrictedFieldTagKeys == null ? new ArrayList<>()
                : new ArrayList<>(restrictedFieldTagKeys);
    }

    public boolean isCanRead() {
        return canRead;
    }

    public void setCanRead(boolean canRead) {
        this.canRead = canRead;
    }

    public boolean isCanComplete() {
        return canComplete;
    }

    public void setCanComplete(boolean canComplete) {
        this.canComplete = canComplete;
    }

    public boolean isCanUpdate() {
        return canUpdate;
    }

    public void setCanUpdate(boolean canUpdate) {
        this.canUpdate = canUpdate;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
