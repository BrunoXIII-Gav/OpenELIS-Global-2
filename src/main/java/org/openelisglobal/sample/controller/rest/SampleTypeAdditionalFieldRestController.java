package org.openelisglobal.sample.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldOptionPayload;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping(value = "/rest/")
public class SampleTypeAdditionalFieldRestController extends BaseRestController {

    @Autowired
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;

    @GetMapping(value = "sample-type-additional-fields", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<SampleTypeAdditionalFieldPayload> getFieldsForSampleType(
            @RequestParam("sampleTypeId") String sampleTypeId,
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive) {
        try {
            return sampleTypeAdditionalFieldService.getFieldsForSampleType(sampleTypeId, includeInactive);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping(value = "sample-type-additional-fields/sample-types", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<IdValuePair> getSampleTypesForAdditionalFieldConfiguration() {
        List<IdValuePair> sampleTypes = DisplayListService.getInstance()
                .getList(DisplayListService.ListType.SAMPLE_TYPE_ACTIVE);
        List<IdValuePair> options = new ArrayList<>();
        for (IdValuePair sampleType : sampleTypes) {
            if (sampleType == null || StringUtils.isBlank(sampleType.getId()) || StringUtils.isBlank(sampleType.getValue())) {
                continue;
            }
            options.add(new IdValuePair(sampleType.getId(), sampleType.getValue()));
        }
        options.sort(Comparator.comparing(IdValuePair::getValue));
        return options;
    }

    @PostMapping(value = "sample-type-additional-fields", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<SampleTypeAdditionalFieldPayload> createField(HttpServletRequest request,
            @RequestBody SampleTypeAdditionalFieldPayload payload) {
        try {
            SampleTypeAdditionalFieldPayload created = sampleTypeAdditionalFieldService.createField(payload,
                    getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping(value = "sample-type-additional-fields/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SampleTypeAdditionalFieldPayload updateField(HttpServletRequest request, @PathVariable Integer fieldId,
            @RequestBody SampleTypeAdditionalFieldPayload payload) {
        try {
            return sampleTypeAdditionalFieldService.updateField(fieldId, payload, getSysUserId(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping(value = "sample-type-additional-fields/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> deactivateField(HttpServletRequest request, @PathVariable Integer fieldId) {
        try {
            sampleTypeAdditionalFieldService.deactivateField(fieldId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "sample-type-additional-fields/{fieldId}/options", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<SampleTypeAdditionalFieldOptionPayload> createOption(HttpServletRequest request,
            @PathVariable Integer fieldId, @RequestBody SampleTypeAdditionalFieldOptionPayload payload) {
        try {
            SampleTypeAdditionalFieldOptionPayload created = sampleTypeAdditionalFieldService.createOption(fieldId,
                    payload, getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping(value = "sample-type-additional-fields/options/{optionId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SampleTypeAdditionalFieldOptionPayload updateOption(HttpServletRequest request, @PathVariable Integer optionId,
            @RequestBody SampleTypeAdditionalFieldOptionPayload payload) {
        try {
            return sampleTypeAdditionalFieldService.updateOption(optionId, payload, getSysUserId(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping(value = "sample-type-additional-fields/options/{optionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> deactivateOption(HttpServletRequest request, @PathVariable Integer optionId) {
        try {
            sampleTypeAdditionalFieldService.deactivateOption(optionId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
