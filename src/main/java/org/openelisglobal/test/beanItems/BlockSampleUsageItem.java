package org.openelisglobal.test.beanItems;

import java.io.Serializable;

public class BlockSampleUsageItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String childBlockName;
    private String parentTubeBlockName;
    private String usedQuantity;
    private String remainingQuantity;
    private boolean locked;

    public String getChildBlockName() {
        return childBlockName;
    }

    public void setChildBlockName(String childBlockName) {
        this.childBlockName = childBlockName;
    }

    public String getParentTubeBlockName() {
        return parentTubeBlockName;
    }

    public void setParentTubeBlockName(String parentTubeBlockName) {
        this.parentTubeBlockName = parentTubeBlockName;
    }

    public String getUsedQuantity() {
        return usedQuantity;
    }

    public void setUsedQuantity(String usedQuantity) {
        this.usedQuantity = usedQuantity;
    }

    public String getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(String remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
