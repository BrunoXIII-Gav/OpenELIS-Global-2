package org.openelisglobal.testconfiguration.action;

import java.util.ArrayList;
import java.util.List;
import org.openelisglobal.common.util.IdValuePair;

public class MethodTests {

    private IdValuePair methodIdValuePair;
    private List<IdValuePair> tests = new ArrayList<>();
    private List<IdValuePair> availableTests = new ArrayList<>();

    public MethodTests() {
    }

    public MethodTests(IdValuePair methodIdValuePair) {
        this.methodIdValuePair = methodIdValuePair;
    }

    public IdValuePair getMethodIdValuePair() {
        return methodIdValuePair;
    }

    public void setMethodIdValuePair(IdValuePair methodIdValuePair) {
        this.methodIdValuePair = methodIdValuePair;
    }

    public List<IdValuePair> getTests() {
        return tests;
    }

    public void setTests(List<IdValuePair> tests) {
        this.tests = tests;
    }

    public List<IdValuePair> getAvailableTests() {
        return availableTests;
    }

    public void setAvailableTests(List<IdValuePair> availableTests) {
        this.availableTests = availableTests;
    }
}
