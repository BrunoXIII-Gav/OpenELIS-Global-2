package org.openelisglobal.testadditionalfield.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.documentupload.TemporaryDocumentUploadPayload;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldOptionPayload;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping(value = "/rest/")
public class TestAdditionalFieldRestController extends BaseRestController {

    @Autowired
    private TestAdditionalFieldService testAdditionalFieldService;

    @GetMapping(value = "test-additional-fields", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<TestAdditionalFieldPayload> getFieldsForTest(@RequestParam("testId") String testId,
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive) {
        try {
            return testAdditionalFieldService.getFieldsForTest(testId, includeInactive);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping(value = "test-additional-fields/tests", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<IdValuePair> getTestsForAdditionalFieldConfiguration() {
        List<IdValuePair> tests = DisplayListService.getInstance().getList(DisplayListService.ListType.ALL_TESTS);
        List<IdValuePair> options = new ArrayList<>();
        for (IdValuePair test : tests) {
            if (test == null || StringUtils.isBlank(test.getId()) || StringUtils.isBlank(test.getValue())) {
                continue;
            }
            options.add(new IdValuePair(test.getId(), test.getValue()));
        }
        options.sort(Comparator.comparing(IdValuePair::getValue));
        return options;
    }

    @PostMapping(value = "test-additional-fields", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<TestAdditionalFieldPayload> createField(HttpServletRequest request,
            @RequestBody TestAdditionalFieldPayload payload) {
        try {
            TestAdditionalFieldPayload created = testAdditionalFieldService.createField(payload, getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping(value = "test-additional-fields/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TestAdditionalFieldPayload updateField(HttpServletRequest request, @PathVariable Integer fieldId,
            @RequestBody TestAdditionalFieldPayload payload) {
        try {
            return testAdditionalFieldService.updateField(fieldId, payload, getSysUserId(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping(value = "test-additional-fields/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> deactivateField(HttpServletRequest request, @PathVariable Integer fieldId) {
        try {
            testAdditionalFieldService.deactivateField(fieldId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "test-additional-fields/{fieldId}/options", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<TestAdditionalFieldOptionPayload> createOption(HttpServletRequest request,
            @PathVariable Integer fieldId, @RequestBody TestAdditionalFieldOptionPayload payload) {
        try {
            TestAdditionalFieldOptionPayload created = testAdditionalFieldService.createOption(fieldId, payload,
                    getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping(value = "test-additional-fields/options/{optionId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TestAdditionalFieldOptionPayload updateOption(HttpServletRequest request, @PathVariable Integer optionId,
            @RequestBody TestAdditionalFieldOptionPayload payload) {
        try {
            return testAdditionalFieldService.updateOption(optionId, payload, getSysUserId(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping(value = "test-additional-fields/options/{optionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> deactivateOption(HttpServletRequest request, @PathVariable Integer optionId) {
        try {
            testAdditionalFieldService.deactivateOption(optionId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "test-additional-fields/files/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TemporaryDocumentUploadPayload uploadTemporaryFile(@RequestParam("testId") String testId,
            @RequestParam("fieldKey") String fieldKey, @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("file is required");
            }
            return testAdditionalFieldService.prepareDocumentUpload(testId, fieldKey, file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), file.getBytes());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to upload file");
        }
    }

    @GetMapping(value = "test-additional-fields/files/temp/{uploadToken}")
    @ResponseBody
    public ResponseEntity<?> openTemporaryFile(@PathVariable String uploadToken,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        Optional<TemporaryDocumentUploadPayload> file = testAdditionalFieldService.getTemporaryDocumentUpload(uploadToken);
        return buildFileResponse(file, download, uploadToken);
    }

    @GetMapping(value = "test-additional-fields/files/{analysisId}/{fieldKey}")
    @ResponseBody
    public ResponseEntity<?> openAnalysisFile(@PathVariable String analysisId, @PathVariable String fieldKey,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        Optional<TemporaryDocumentUploadPayload> file = testAdditionalFieldService.getAnalysisDocument(analysisId,
                fieldKey);
        return buildFileResponse(file, download, fieldKey);
    }

    private ResponseEntity<?> buildFileResponse(Optional<TemporaryDocumentUploadPayload> file, boolean download,
            String fallbackName) {
        if (file.isEmpty() || file.get().getContent() == null || file.get().getContent().length == 0) {
            return ResponseEntity.notFound().build();
        }

        TemporaryDocumentUploadPayload payload = file.get();
        String contentType = StringUtils.defaultIfBlank(payload.getFileType(),
                MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String fileName = StringUtils.defaultIfBlank(payload.getFileName(), fallbackName + ".bin")
                .replaceAll("[\\r\\n\"]", "_");
        String dispositionType = download ? "attachment" : "inline";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + fileName + "\"")
                .body(payload.getContent());
    }
}
