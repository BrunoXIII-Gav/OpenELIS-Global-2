package org.openelisglobal.testconfiguration.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.testconfiguration.form.SampleTypeUomAssignForm;
import org.openelisglobal.testconfiguration.service.SampleTypeUomAssignService;
import org.openelisglobal.typeofsample.service.TypeOfSampleUnitOfMeasureService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleUnitOfMeasure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest")
public class SampleTypeUomAssignRestController extends BaseController {

    private static final String[] ALLOWED_FIELDS = new String[] { "sampleTypeId", "unitOfMeasureIds" };

    @Autowired
    private TypeOfSampleUnitOfMeasureService typeOfSampleUnitOfMeasureService;

    @Autowired
    private SampleTypeUomAssignService sampleTypeUomAssignService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping("/SampleTypeUomAssign")
    public SampleTypeUomAssignForm getAssignments() {
        SampleTypeUomAssignForm form = new SampleTypeUomAssignForm();
        List<IdValuePair> sampleTypes = new ArrayList<>(
                DisplayListService.getInstance().getList(DisplayListService.ListType.SAMPLE_TYPE));
        sampleTypes.addAll(DisplayListService.getInstance().getList(DisplayListService.ListType.SAMPLE_TYPE_INACTIVE));
        List<IdValuePair> uoms = DisplayListService.getInstance().getList(DisplayListService.ListType.UNIT_OF_MEASURE);

        form.setSampleTypeList(sampleTypes);
        form.setUomList(uoms);
        form.setSampleTypeUomMap(buildAssignmentMap(sampleTypes, uoms));
        return form;
    }

    @PostMapping("/SampleTypeUomAssign")
    public SampleTypeUomAssignForm saveAssignments(HttpServletRequest request,
            @RequestBody @Valid SampleTypeUomAssignForm form, BindingResult result) {
        if (!result.hasErrors()) {
            sampleTypeUomAssignService.replaceAssignments(form.getSampleTypeId(), form.getUnitOfMeasureIds(),
                    getSysUserId(request));
        }
        return getAssignments();
    }

    @GetMapping("/sample-type-uoms")
    public List<IdValuePair> getAvailableUomsForSampleType(
            @RequestParam(name = "sampleTypeId", required = false) String sampleTypeId) {
        List<IdValuePair> allUoms = DisplayListService.getInstance().getList(DisplayListService.ListType.UNIT_OF_MEASURE);
        if (sampleTypeId == null || sampleTypeId.isBlank()) {
            return allUoms;
        }

        List<TypeOfSampleUnitOfMeasure> assignments = typeOfSampleUnitOfMeasureService.getBySampleTypeId(sampleTypeId);
        if (assignments == null || assignments.isEmpty()) {
            return allUoms;
        }

        List<String> assignedIds = assignments.stream().map(TypeOfSampleUnitOfMeasure::getUnitOfMeasureId)
                .collect(Collectors.toList());
        return allUoms.stream().filter(uom -> assignedIds.contains(uom.getId())).collect(Collectors.toList());
    }

    @GetMapping("/sample-type-uoms/assignments")
    public Map<String, List<String>> getAssignmentIdsBySampleType() {
        Map<String, List<String>> assignmentsBySampleType = new LinkedHashMap<>();
        for (TypeOfSampleUnitOfMeasure assignment : typeOfSampleUnitOfMeasureService.getAll()) {
            assignmentsBySampleType.computeIfAbsent(assignment.getTypeOfSampleId(), key -> new ArrayList<>())
                    .add(assignment.getUnitOfMeasureId());
        }
        return assignmentsBySampleType;
    }

    private Map<String, List<IdValuePair>> buildAssignmentMap(List<IdValuePair> sampleTypes, List<IdValuePair> uoms) {
        Map<String, IdValuePair> uomById = uoms.stream().collect(Collectors.toMap(IdValuePair::getId, value -> value));
        Map<String, List<IdValuePair>> assignmentsBySampleType = new LinkedHashMap<>();

        for (IdValuePair sampleType : sampleTypes) {
            List<IdValuePair> assignedUoms = typeOfSampleUnitOfMeasureService.getBySampleTypeId(sampleType.getId()).stream()
                    .map(TypeOfSampleUnitOfMeasure::getUnitOfMeasureId).map(uomById::get).filter(uom -> uom != null)
                    .collect(Collectors.toList());
            assignmentsBySampleType.put(sampleType.getId(), assignedUoms);
        }

        return assignmentsBySampleType;
    }

    @Override
    protected String findLocalForward(String forward) {
        return "PageNotFound";
    }

    @Override
    protected String getPageTitleKey() {
        return null;
    }

    @Override
    protected String getPageSubtitleKey() {
        return null;
    }
}
