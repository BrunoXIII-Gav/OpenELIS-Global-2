package org.openelisglobal.testdependency.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testdependency.form.TestParentChildDependencyForm;
import org.openelisglobal.testdependency.service.TestParentChildDependencyService;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/test-parent-child-dependencies")
@Validated
public class TestParentChildDependencyRestController extends BaseRestController {

    @Autowired
    private TestParentChildDependencyService dependencyService;

    @Autowired
    private TestService testService;

    @Autowired
    private TestAdditionalFieldService testAdditionalFieldService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<TestParentChildDependencyForm>> getDependencies(
            @RequestParam(required = false) String parentTestId,
            @RequestParam(required = false) Boolean activeOnly) {

        List<TestParentChildDependency> dependencies;
        boolean active = activeOnly != null && activeOnly.booleanValue();

        if (!GenericValidator.isBlankOrNull(parentTestId)) {
            dependencies = dependencyService.getByParentTestId(parentTestId);
            if (active) {
                dependencies = dependencies.stream().filter(d -> Boolean.TRUE.equals(d.getActive()))
                        .collect(Collectors.toList());
            }
        } else if (active) {
            dependencies = dependencyService.getAllActive();
        } else {
            dependencies = dependencyService.getAll();
        }

        List<TestParentChildDependencyForm> forms = dependencies.stream().map(this::toForm).collect(Collectors.toList());
        return ResponseEntity.ok(forms);
    }

    @GetMapping(value = "/parent-test-fields", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<ParentFieldOption>> getParentTestFields(@RequestParam String parentTestId) {
        if (GenericValidator.isBlankOrNull(parentTestId)) {
            throw new IllegalArgumentException("parentTestId is required");
        }

        List<TestAdditionalFieldPayload> fields = testAdditionalFieldService.getFieldsForTest(parentTestId, false);
        List<ParentFieldOption> options = fields.stream().filter(this::isEligibleParentUsageField).map(field -> {
            ParentFieldOption option = new ParentFieldOption();
            option.setFieldKey(field.getFieldKey());
            option.setDisplayName(field.getDisplayName());
            return option;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(options);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<TestParentChildDependencyForm> upsertDependency(
            @RequestBody TestParentChildDependencyForm form,
            HttpServletRequest request) {

        String parentTestId = form.getParentTestId();
        String childTestId = form.getChildTestId();

        if (GenericValidator.isBlankOrNull(parentTestId) || GenericValidator.isBlankOrNull(childTestId)) {
            throw new IllegalArgumentException("parentTestId and childTestId are required");
        }

        if (parentTestId.equals(childTestId)) {
            throw new IllegalArgumentException("A test cannot be parent and child at the same time");
        }

        Test parentTest = testService.get(parentTestId);
        Test childTest = testService.get(childTestId);
        if (parentTest == null || childTest == null) {
            throw new IllegalArgumentException("Parent and child tests must exist");
        }

        TestParentChildDependency dependency;
        if (!GenericValidator.isBlankOrNull(form.getId())) {
            dependency = dependencyService.get(form.getId());
            if (dependency == null) {
                throw new IllegalArgumentException("Dependency not found: " + form.getId());
            }
        } else {
            TestParentChildDependency existing = dependencyService.getByChildTestId(childTestId);
            dependency = existing != null ? existing : new TestParentChildDependency();
        }

        dependency.setParentTest(parentTest);
        dependency.setChildTest(childTest);
        dependency.setActive(form.getActive() == null ? Boolean.TRUE : form.getActive());
        dependency.setDisplayOrder(form.getDisplayOrder());
        String normalizedSampleUsageSource = normalizeSampleUsageSource(form.getSampleUsageSource());
        dependency.setSampleUsageSource(normalizedSampleUsageSource);
        if (TestParentChildDependency.SAMPLE_USAGE_SOURCE_PARENT_TEST_FIELD.equals(normalizedSampleUsageSource)) {
            String parentResultFieldKey = normalizeParentFieldKey(form.getParentResultFieldKey());
            validateParentFieldSelection(parentTestId, parentResultFieldKey);
            dependency.setParentResultFieldKey(parentResultFieldKey);
        } else {
            dependency.setParentResultFieldKey(null);
        }
        dependency.setSysUserId(getSysUserId(request));

        if (GenericValidator.isBlankOrNull(dependency.getId())) {
            dependencyService.insert(dependency);
        } else {
            dependencyService.update(dependency);
        }

        if (Boolean.TRUE.equals(dependency.getActive())
                && Boolean.TRUE.equals(childTest.getDirectSampleUsageEnabled())) {
            childTest.setDirectSampleUsageEnabled(Boolean.FALSE);
            childTest.setSysUserId(getSysUserId(request));
            testService.update(childTest);
        }

        return ResponseEntity.status(HttpStatus.OK).body(toForm(dependency));
    }

    @DeleteMapping(value = "/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteDependency(@PathVariable String id) {
        TestParentChildDependency dependency = dependencyService.get(id);
        if (dependency == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            dependencyService.delete(dependency);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "deleteDependency", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private TestParentChildDependencyForm toForm(TestParentChildDependency dependency) {
        TestParentChildDependencyForm form = new TestParentChildDependencyForm();
        form.setId(dependency.getId());
        form.setParentTestId(dependency.getParentTest() != null ? dependency.getParentTest().getId() : null);
        form.setChildTestId(dependency.getChildTest() != null ? dependency.getChildTest().getId() : null);
        form.setActive(dependency.getActive());
        form.setDisplayOrder(dependency.getDisplayOrder());
        form.setSampleUsageSource(dependency.getSampleUsageSource());
        form.setParentResultFieldKey(dependency.getParentResultFieldKey());
        return form;
    }

    private String normalizeSampleUsageSource(String sampleUsageSource) {
        if (GenericValidator.isBlankOrNull(sampleUsageSource)) {
            return TestParentChildDependency.SAMPLE_USAGE_SOURCE_SAMPLE_ITEM_REMAINING;
        }
        String normalized = sampleUsageSource.trim().toUpperCase(Locale.ROOT);
        if (TestParentChildDependency.SAMPLE_USAGE_SOURCE_SAMPLE_ITEM_REMAINING.equals(normalized)
                || TestParentChildDependency.SAMPLE_USAGE_SOURCE_PARENT_TEST_FIELD.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Invalid sampleUsageSource: " + sampleUsageSource);
    }

    private String normalizeParentFieldKey(String parentResultFieldKey) {
        if (parentResultFieldKey == null) {
            return null;
        }
        String trimmed = parentResultFieldKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateParentFieldSelection(String parentTestId, String parentResultFieldKey) {
        if (GenericValidator.isBlankOrNull(parentResultFieldKey)) {
            throw new IllegalArgumentException("parentResultFieldKey is required for PARENT_TEST_FIELD source");
        }
        List<TestAdditionalFieldPayload> fields = testAdditionalFieldService.getFieldsForTest(parentTestId, false);
        boolean existsAndValid = fields.stream().anyMatch(
                field -> parentResultFieldKey.equals(field.getFieldKey()) && isEligibleParentUsageField(field));
        if (!existsAndValid) {
            throw new IllegalArgumentException(
                    "parentResultFieldKey must reference an active required NUMBER field from parent test");
        }
    }

    private boolean isEligibleParentUsageField(TestAdditionalFieldPayload field) {
        if (field == null) {
            return false;
        }
        if (Boolean.FALSE.equals(field.getActive())) {
            return false;
        }
        if (!Boolean.TRUE.equals(field.getRequired())) {
            return false;
        }
        if (GenericValidator.isBlankOrNull(field.getFieldType())) {
            return false;
        }
        return "NUMBER".equalsIgnoreCase(field.getFieldType());
    }

    public static class ParentFieldOption {
        private String fieldKey;
        private String displayName;

        public String getFieldKey() {
            return fieldKey;
        }

        public void setFieldKey(String fieldKey) {
            this.fieldKey = fieldKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
