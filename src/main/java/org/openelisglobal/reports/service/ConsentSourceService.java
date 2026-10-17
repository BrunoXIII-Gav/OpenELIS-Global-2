package org.openelisglobal.reports.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.json.JSONArray;
import org.openelisglobal.dataexchange.fhir.FhirUtil;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileDefinitionService;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileFieldConfigService;
import org.openelisglobal.program.service.ProgramService;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.unitofmeasure.service.UnitOfMeasureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public form fields only. Never derive report sources by reflecting entities
 * or request beans.
 */
@Service
@Transactional(readOnly = true)
public class ConsentSourceService {
    @Autowired
    private OrderAdditionalFieldService orderFields;
    @Autowired
    private PatientAdditionalFieldService patientFields;
    @Autowired
    private ProfessionalProfileDefinitionService profiles;
    @Autowired
    private ProfessionalProfileFieldConfigService profileFields;
    @Autowired
    private UnitOfMeasureService units;

    @Autowired
    private TypeOfSampleService sampleTypes;
    @Autowired
    private SampleTypeAdditionalFieldService sampleFields;
    @Autowired
    private ProgramService programs;
    @Autowired
    private FhirUtil fhir;

    public List<Map<String, Object>> getSources() {
        return getSources(true);
    }

    public List<Map<String, Object>> getSources(boolean includeQuestions) {
        List<Map<String, Object>> sources = fixedSources();
        Map<String, Boolean> orderVisibility = new LinkedHashMap<>();
        orderFields.getFixedFieldConfigs().forEach(f -> orderVisibility.put(f.getFieldKey(), f.getVisible()));
        sources.removeIf(
                s -> "order".equals(s.get("scope")) && Boolean.FALSE.equals(orderVisibility.get(s.get("fieldKey"))));
        orderFields.getFields(false, true).stream().filter(f -> !Boolean.FALSE.equals(f.getActive())).forEach(f -> {
            Map<String, Object> source = additional("orderAdditional." + f.getFieldKey(), f.getDisplayName(),
                    "sampleOrderItems.additionalFieldValues." + f.getFieldKey(), f.getFieldType());
            source.put("metadataJson", f.getMetadataJson() == null ? "{}" : f.getMetadataJson());
            source.put("fieldKey", f.getFieldKey());
            source.put("definitionPath", "sampleOrderItems.additionalFields");
            source.put("filePath", "sampleOrderItems.additionalFieldFiles." + f.getFieldKey() + ".fileName");
            source.put("options",
                    f.getOptions().stream().map(o -> option(o.getOptionKey(), o.getOptionLabel())).toList());
            sources.add(source);
        });
        patientFields.getFields(false, true).stream().filter(f -> !Boolean.FALSE.equals(f.getActive())).forEach(f -> {
            Map<String, Object> source = additional("patientAdditional." + f.getFieldKey(), f.getDisplayName(),
                    "patientProperties.patientAdditionalFieldValues." + f.getFieldKey(), f.getFieldType());
            source.put("metadataJson", f.getMetadataJson() == null ? "{}" : f.getMetadataJson());
            source.put("options",
                    f.getOptions().stream().map(o -> option(o.getOptionKey(), o.getOptionLabel())).toList());
            sources.add(source);
        });
        profiles.getProfiles().forEach(profile -> profileFields
                .getFieldsForProfile(profile.getCode(), false).stream().filter(f -> !Boolean.FALSE.equals(f.getActive())
                        && !Boolean.TRUE.equals(f.getSystemField()) && Boolean.TRUE.equals(f.getShowInOrderEntry()))
                .forEach(f -> {
                    Map<String, Object> source = additional(
                            "providerProfile." + profile.getCode() + "." + f.getFieldKey(),
                            profile.getName() + ": " + f.getDisplayName(),
                            "sampleOrderItems.providerProfileFieldValues." + f.getFieldKey(), f.getFieldType());
                    source.put("profileCode", profile.getCode());
                    source.put("fieldKey", f.getFieldKey());
                    source.put("definitionPath", "sampleOrderItems.providerProfileFields");
                    source.put("options",
                            f.getOptions().stream().map(o -> option(o.getOptionKey(), o.getOptionLabel())).toList());
                    sources.add(source);
                }));
        sampleTypes.getAllTypeOfSamples().forEach(type -> sampleFields.getFieldsForSampleType(type.getId(), false, true)
                .stream().filter(f -> !Boolean.FALSE.equals(f.getActive())).forEach(f -> {
                    Map<String, Object> source = additional("sampleAdditional." + type.getId() + "." + f.getFieldKey(),
                            type.getLocalizedName() + ": " + f.getDisplayName(),
                            "sampleXML.additionalFieldValues." + f.getFieldKey(), f.getFieldType());
                    source.put("scope", "samples");
                    source.put("sampleTypeId", type.getId());
                    source.put("metadataJson", f.getMetadataJson() == null ? "{}" : f.getMetadataJson());
                    source.put("fieldKey", f.getFieldKey());
                    source.put("definitionPath", "additionalFields");
                    source.put("options",
                            f.getOptions().stream().map(o -> option(o.getOptionKey(), o.getOptionLabel())).toList());
                    sources.add(source);
                }));
        sources.stream().filter(s -> "sample.uom".equals(s.get("id"))).forEach(s -> s.put("options",
                units.getAll().stream().map(u -> option(u.getId(), u.getUnitOfMeasureName())).toList()));
        sources.stream().filter(s -> "sample.type".equals(s.get("id"))).forEach(s -> {
            s.put("path", "sampleTypeId");
            s.put("format", "option");
            s.put("options", sampleTypes.getAllTypeOfSamples().stream()
                    .map(t -> option(t.getId(), t.getLocalizedName())).toList());
        });
        sources.stream().filter(s -> "order.programId".equals(s.get("id"))).forEach(s -> s.put("options",
                programs.getAll().stream().map(p -> option(p.getId(), p.getProgramName())).toList()));
        if (includeQuestions) {
            programs.getAll().stream().filter(p -> p.getQuestionnaireUUID() != null).forEach(program -> {
                Questionnaire questionnaire = fhir.getLocalFhirClient().read().resource(Questionnaire.class)
                        .withId(program.getQuestionnaireUUID().toString()).execute();
                addQuestions(sources, program.getId(), program.getProgramName(), questionnaire.getItem());
            });
        }
        return sources;
    }

    private void addQuestions(List<Map<String, Object>> sources, String programId, String programName,
            List<QuestionnaireItemComponent> items) {
        for (var item : items) {
            if (item.hasText() && item.getType() != QuestionnaireItemType.GROUP
                    && item.getType() != QuestionnaireItemType.DISPLAY) {
                Map<String, Object> source = additional("questionnaire." + programId + "." + item.getLinkId(),
                        programName + ": " + item.getText(), "", "TEXT");
                source.put("format", "questionnaire");
                source.put("programId", programId);
                source.put("linkId", item.getLinkId());
                sources.add(source);
            }
            addQuestions(sources, programId, programName, item.getItem());
        }
    }

    private List<Map<String, Object>> fixedSources() {
        try (InputStream in = new ClassPathResource("reports/consent/sources.json").getInputStream()) {
            JSONArray rows = new JSONArray(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                result.add(rows.getJSONObject(i).toMap());
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load consent sources", e);
        }
    }

    private Map<String, Object> additional(String id, String label, String path, String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("label", label);
        result.put("labelKey", "consent.template.source.additional");
        result.put("path", path);
        result.put("fieldType", type);
        result.put("format", "additional");
        return result;
    }

    private Map<String, String> option(String key, String label) {
        return Map.of("optionKey", key == null ? "" : key, "optionLabel", label == null ? "" : label);
    }
}
