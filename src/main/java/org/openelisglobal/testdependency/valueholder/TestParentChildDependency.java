package org.openelisglobal.testdependency.valueholder;

import org.openelisglobal.common.valueholder.BaseObject;
import org.openelisglobal.common.valueholder.ValueHolder;
import org.openelisglobal.common.valueholder.ValueHolderInterface;
import org.openelisglobal.test.valueholder.Test;

public class TestParentChildDependency extends BaseObject<String> {

    private static final long serialVersionUID = 1L;

    private String id;
    private ValueHolderInterface parentTest;
    private ValueHolderInterface childTest;
    private Boolean active = Boolean.TRUE;
    private Integer displayOrder;

    public TestParentChildDependency() {
        super();
        parentTest = new ValueHolder();
        childTest = new ValueHolder();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public Test getParentTest() {
        return (Test) parentTest.getValue();
    }

    public void setParentTest(Test parentTest) {
        this.parentTest.setValue(parentTest);
    }

    public Test getChildTest() {
        return (Test) childTest.getValue();
    }

    public void setChildTest(Test childTest) {
        this.childTest.setValue(childTest);
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
