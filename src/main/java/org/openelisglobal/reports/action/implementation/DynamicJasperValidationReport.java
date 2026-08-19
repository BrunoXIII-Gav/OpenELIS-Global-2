package org.openelisglobal.reports.action.implementation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.audittrail.valueholder.History;
import org.openelisglobal.history.service.HistoryService;
import org.openelisglobal.image.service.ImageService;
import org.openelisglobal.image.valueholder.Image;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.referencetables.service.ReferenceTablesService;
import org.openelisglobal.reports.action.implementation.reportBeans.ClinicalPatientData;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;

public class DynamicJasperValidationReport extends PatientCILNSPClinical_vreduit {

    private static final String MODE_KEY = "mode";
    private static final String DYNAMIC_MODE = "jasper_dynamic";
    private static final String TEMPLATE_CONTENT_KEY = "templateContent";
    private static final String PARAMETER_DEFINITIONS_KEY = "parameterDefinitions";
    private static final String MAPPINGS_KEY = "mappings";
    private static final String ANALYSIS_REFERENCE_TABLE = "ANALYSIS";
    private static final String USER_FIELD_TYPE = "USER";
    private static final Pattern COLLECTION_DATE_PATTERN = Pattern
            .compile("(\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b)");
    private static final Pattern ANALYST_SOURCE_PATTERN = Pattern
            .compile("^biologist(\\d+)(Name|Specialty|CbpCode)$", Pattern.CASE_INSENSITIVE);

    private final ImageService imageService = SpringContext.getBean(ImageService.class);
    private final HistoryService historyService = SpringContext.getBean(HistoryService.class);
    private final ReferenceTablesService referenceTablesService = SpringContext.getBean(ReferenceTablesService.class);
    private final SystemUserService systemUserService = SpringContext.getBean(SystemUserService.class);
    private final org.openelisglobal.provider.service.ProviderProfileFieldService providerProfileFieldService = SpringContext.getBean(org.openelisglobal.provider.service.ProviderProfileFieldService.class);

    private JSONObject config = new JSONObject();
    private String templateContent = "";
    private List<TemplateParameterDefinition> parameterDefinitions = Collections.emptyList();
    private final Map<String, Analysis> analysisById = new HashMap<>();
    private final Map<String, List<TestAdditionalFieldPayload>> biologistDefinitionsByTestId = new HashMap<>();
    private final Map<String, ProfessionalInfo> professionalInfoByUserId = new HashMap<>();
    private List<String> cachedBiologistUserIds;
    private ProfessionalInfo cachedValidatorInfo;

    @Override
    protected String reportFileName() {
        return "PatientClinicalReport";
    }

    @Override
    public void initializeReport(org.openelisglobal.reports.form.ReportForm form) {
        super.initializeReport(form);
        analysisById.clear();
        biologistDefinitionsByTestId.clear();
        professionalInfoByUserId.clear();
        cachedBiologistUserIds = null;
        cachedValidatorInfo = null;
        config = parseConfig(form);
        templateContent = StringUtils.defaultString(config.optString(TEMPLATE_CONTENT_KEY));
        parameterDefinitions = parseParameterDefinitions(config);
        if (!errorFound) {
            applyDynamicMappings();
        }
    }

    @Override
    public byte[] runReport() throws java.io.UnsupportedEncodingException, java.io.IOException, java.sql.SQLException,
            IllegalStateException, JRException, java.text.ParseException {
        if (errorFound || StringUtils.isBlank(templateContent)) {
            if (StringUtils.isBlank(templateContent) && !errorFound) {
                add1LineErrorMessage("report.error.message.noPrintableItems");
            }
            return super.runReport();
        }

        JasperReport compiled = JasperCompileManager
                .compileReport(new ByteArrayInputStream(templateContent.getBytes(StandardCharsets.UTF_8)));
        return JasperRunManager.runReportToPdf(compiled, getReportParameters(), new JREmptyDataSource(1));
    }

    @Override
    public net.sf.jasperreports.engine.JRDataSource getReportDataSource() throws IllegalStateException {
        if (errorFound) {
            return new JRBeanCollectionDataSource(errorMsgs);
        }
        return new JREmptyDataSource(1);
    }

    private JSONObject parseConfig(org.openelisglobal.reports.form.ReportForm form) {
        if (form == null || GenericValidator.isBlankOrNull(form.getValidationTemplateConfigJson())) {
            return new JSONObject();
        }
        try {
            JSONObject parsed = new JSONObject(form.getValidationTemplateConfigJson());
            if (!StringUtils.equals(DYNAMIC_MODE, parsed.optString(MODE_KEY))) {
                return new JSONObject();
            }
            return parsed;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private List<TemplateParameterDefinition> parseParameterDefinitions(JSONObject parsedConfig) {
        JSONArray definitions = parsedConfig.optJSONArray(PARAMETER_DEFINITIONS_KEY);
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<TemplateParameterDefinition> parsed = new ArrayList<>();
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject row = definitions.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String name = StringUtils.trimToNull(row.optString("name"));
            if (name == null) {
                continue;
            }
            String className = StringUtils.defaultIfBlank(StringUtils.trimToNull(row.optString("className")),
                    "java.lang.String");
            parsed.add(new TemplateParameterDefinition(name, className));
        }
        return parsed;
    }

    private void applyDynamicMappings() {
        JSONObject mappings = config.optJSONObject(MAPPINGS_KEY);
        ClinicalPatientData first = getFirstReportItem();

        for (TemplateParameterDefinition definition : parameterDefinitions) {
            if (definition == null || StringUtils.isBlank(definition.name)) {
                continue;
            }

            if (reportParameters.containsKey(definition.name) && mappings == null) {
                continue;
            }

            JSONObject mapping = mappings == null ? null : mappings.optJSONObject(definition.name);
            Object resolvedValue = resolveMappedValue(mapping, definition.className, first);
            if (resolvedValue != null) {
                reportParameters.put(definition.name, resolvedValue);
            } else if (mapping != null && !"java.io.InputStream".equalsIgnoreCase(definition.className)) {
                reportParameters.put(definition.name, "");
            } else if (!reportParameters.containsKey(definition.name)
                    && !"java.io.InputStream".equalsIgnoreCase(definition.className)) {
                reportParameters.put(definition.name, "");
            }
        }
    }

    private Object resolveMappedValue(JSONObject mapping, String className, ClinicalPatientData first) {
        if (mapping == null) {
            return null;
        }

        String type = StringUtils.defaultIfBlank(StringUtils.trimToNull(mapping.optString("type")), "source");
        String value = StringUtils.trimToNull(mapping.optString("value"));

        if ("conditional".equalsIgnoreCase(type)) {
            return resolveConditionalValue(mapping, className, first);
        }
        if ("constant".equalsIgnoreCase(type)) {
            return coerceValue(className, value);
        }
        if ("user_field".equalsIgnoreCase(type)) {
            return resolveUserFieldValue(mapping, className, first);
        }
        if ("image".equalsIgnoreCase(type)) {
            if (!"java.io.InputStream".equalsIgnoreCase(className)) {
                return null;
            }
            return resolveImageInputStream(value);
        }
        if ("empty".equalsIgnoreCase(type) || value == null) {
            return null;
        }
        return coerceValue(className, resolveSourceValue(value, first));
    }

    /**
     * Resolve value from user_field mapping type (new cascading system).
     * Mapping structure: {
     *   "type": "user_field", 
     *   "userFieldId": "analyst_1", 
     *   "userFieldKey": "responsible_analyst", 
     *   "profileFieldMappings": {
     *     "BIOLOGIST": "cbpCode",
     *     "MEDICAL_DOCTOR": "rne",
     *     "ENGINEER": "cip"
     *   }
     * }
     */
    private Object resolveUserFieldValue(JSONObject mapping, String className, ClinicalPatientData first) {
        if (mapping == null || first == null) {
            return null;
        }

        String userFieldId = StringUtils.trimToNull(mapping.optString("userFieldId"));
        String userFieldKey = StringUtils.trimToNull(mapping.optString("userFieldKey"));

        if (userFieldKey == null) {
            return null;
        }

        // Special handling for "validator" field
        if ("validator".equalsIgnoreCase(userFieldId)) {
            return resolveValidatorFieldValue(mapping, className);
        }

        if ("requester".equalsIgnoreCase(userFieldId)) {
            return resolveRequesterFieldValue(mapping, className, first);
        }

        // Get systemUserId from the additional field value
        String systemUserId = StringUtils.trimToNull(first.getAdditionalFieldValue(userFieldKey));
        if (systemUserId == null) {
            return null;
        }

        // Resolve professional info
        ProfessionalInfo professionalInfo = resolveProfessionalInfo(systemUserId);
        if (professionalInfo == null) {
            return null;
        }

        // Determine which field to use based on profile
        String profileFieldKey = determineProfileFieldKey(mapping, systemUserId);
        if (profileFieldKey == null) {
            return null;
        }

        // Extract the profile field value
        return extractProfileFieldValue(professionalInfo, profileFieldKey, className, systemUserId);
    }

    private Object resolveRequesterFieldValue(JSONObject mapping, String className, ClinicalPatientData first) {
        Provider requesterProvider = resolveRequesterProvider(first);
        if (requesterProvider == null) {
            return null;
        }

        String profileFieldKey = determineProfileFieldKeyForProfileCode(mapping,
                StringUtils.trimToNull(requesterProvider.getProfessionalProfileCode()));
        if (profileFieldKey == null) {
            return null;
        }

        ProfessionalInfo requesterInfo = buildRequesterProfessionalInfo(requesterProvider, first);
        return extractProfileFieldValue(requesterInfo, profileFieldKey, className, null, requesterProvider);
    }

    /**
     * Determine which profile field to use based on the user's professional profile.
     * If profileFieldMappings exists, use the mapping for the user's profile.
     * Otherwise, fall back to the legacy profileFieldKey.
     */
    private String determineProfileFieldKey(JSONObject mapping, String systemUserId) {
        if (mapping == null) {
            return null;
        }

        // Check if we have profile-specific mappings
        JSONObject profileFieldMappings = mapping.optJSONObject("profileFieldMappings");
        if (profileFieldMappings != null && !profileFieldMappings.isEmpty()) {
            String fieldKey = determineProfileFieldKeyForProfileCode(mapping, getUserProfessionalProfileCode(systemUserId));
            if (fieldKey != null) {
                return fieldKey;
            }
        }

        // Fall back to legacy single profileFieldKey (for backward compatibility)
        return StringUtils.trimToNull(mapping.optString("profileFieldKey"));
    }

    private String determineProfileFieldKeyForProfileCode(JSONObject mapping, String profileCode) {
        if (mapping == null || StringUtils.isBlank(profileCode)) {
            return null;
        }

        JSONObject profileFieldMappings = mapping.optJSONObject("profileFieldMappings");
        if (profileFieldMappings == null || profileFieldMappings.isEmpty()) {
            return null;
        }

        return StringUtils.trimToNull(profileFieldMappings.optString(profileCode));
    }

    /**
     * Get the professional profile code for a system user.
     */
    private String getUserProfessionalProfileCode(String systemUserId) {
        if (StringUtils.isBlank(systemUserId)) {
            return null;
        }

        try {
            SystemUser user = systemUserService.getUserById(systemUserId);
            if (user == null || StringUtils.isBlank(user.getLinkedProviderPersonId())) {
                return null;
            }

            Person linkedPerson = personService.getPersonById(user.getLinkedProviderPersonId());
            if (linkedPerson == null) {
                return null;
            }

            Provider provider = providerService.getProviderByPerson(linkedPerson);
            if (provider == null) {
                return null;
            }

            return StringUtils.trimToNull(provider.getProfessionalProfileCode());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve validator field value (special case for validator).
     */
    private Object resolveValidatorFieldValue(JSONObject mapping, String className) {
        ProfessionalInfo validatorInfo = resolveValidatorInfo();
        if (validatorInfo == null) {
            return null;
        }

        // For validator, we need to find the systemUserId from the most recent validation history
        String validatorUserId = null;
        if (reportItems != null && !reportItems.isEmpty()) {
            ClinicalPatientData first = getFirstReportItem();
            if (first != null && StringUtils.isNotBlank(first.getAnalysisId())) {
                Analysis analysis = getAnalysisForReportItem(first);
                if (analysis != null) {
                    History validationHistory = getMostRecentValidationHistory(analysis);
                    if (validationHistory != null) {
                        validatorUserId = validationHistory.getSysUserId();
                    }
                }
            }
        }

        // Determine which field to use based on validator's profile
        String profileFieldKey = determineProfileFieldKey(mapping, validatorUserId);
        if (profileFieldKey == null) {
            return null;
        }

        return extractProfileFieldValue(validatorInfo, profileFieldKey, className, validatorUserId);
    }

    /**
     * Extract specific field value from ProfessionalInfo.
     */
    private Object extractProfileFieldValue(ProfessionalInfo info, String profileFieldKey, String className, String systemUserId) {
        return extractProfileFieldValue(info, profileFieldKey, className, systemUserId, null);
    }

    private Object extractProfileFieldValue(ProfessionalInfo info, String profileFieldKey, String className,
            String systemUserId, Provider provider) {
        if (info == null || profileFieldKey == null) {
            return null;
        }

        // System fields from ProfessionalInfo
        String stringValue = switch (profileFieldKey.toLowerCase()) {
            case "name" -> info.displayName;
            case "specialty" -> {
                yield info.specialty;
            }
            case "cbpcode" -> info.cbpCode;
            case "signature" -> {
                if ("java.io.InputStream".equalsIgnoreCase(className)) {
                    yield null; // Will be handled below
                }
                yield null;
            }
            default -> provider != null ? resolveCustomProfileField(provider, profileFieldKey)
                    : resolveCustomProfileField(systemUserId, profileFieldKey);
        };

        // Special handling for signature image
        if ("signature".equalsIgnoreCase(profileFieldKey) && "java.io.InputStream".equalsIgnoreCase(className)) {
            return info.signatureStream;
        }

        // Handle other system fields from Provider entity
        Provider resolvedProvider = provider;
        if (stringValue == null && resolvedProvider == null && systemUserId != null) {
            SystemUser user = systemUserService.getUserById(systemUserId);
            resolvedProvider = user == null ? null : resolveLinkedProvider(user);
        }
        if (stringValue == null && resolvedProvider != null) {
            stringValue = switch (profileFieldKey.toLowerCase()) {
                case "professionalInitials", "professionalinitials" -> resolvedProvider.getProfessionalInitials();
                case "dni" -> resolvedProvider.getDni();
                case "npi" -> resolvedProvider.getNpi();
                default -> null;
            };
        }

        return coerceValue(className, stringValue);
    }

    private Provider resolveRequesterProvider(ClinicalPatientData first) {
        Analysis analysis = getAnalysisForReportItem(first);
        if (analysis == null || analysis.getSampleItem() == null || analysis.getSampleItem().getSample() == null) {
            return null;
        }
        return sampleHumanService.getProviderForSample(analysis.getSampleItem().getSample());
    }

    private ProfessionalInfo buildRequesterProfessionalInfo(Provider provider, ClinicalPatientData first) {
        if (provider == null) {
            return ProfessionalInfo.empty();
        }

        Person person = provider.getPerson();
        String displayName = "";
        if (person != null) {
            displayName = StringUtils.normalizeSpace(
                    StringUtils.defaultString(person.getFirstName()) + " " + StringUtils.defaultString(person.getLastName()));
        }
        if (StringUtils.isBlank(displayName) && first != null) {
            displayName = StringUtils.defaultIfBlank(first.getPrescriber(), first.getContactInfo());
        }
        return new ProfessionalInfo(displayName, StringUtils.defaultString(provider.getSpecialty()),
                StringUtils.defaultString(provider.getCbpCode()), null);
    }

    private String resolveCustomProfileField(Provider provider, String fieldKey) {
        if (provider == null || fieldKey == null) {
            return null;
        }

        try {
            if (provider.getProfileFieldValues() == null || provider.getProfileFieldValues().isEmpty()) {
                providerProfileFieldService.hydrateProfileFieldValues(provider);
            }

            Map<String, Object> profileFieldValuesObj = provider.getProfileFieldValues();
            if (profileFieldValuesObj != null && profileFieldValuesObj.containsKey(fieldKey)) {
                Object fieldValue = profileFieldValuesObj.get(fieldKey);
                return StringUtils.trimToNull(fieldValue != null ? String.valueOf(fieldValue) : null);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve custom profile field value from ProviderProfileFieldValue.
     */
    private String resolveCustomProfileField(String systemUserId, String fieldKey) {
        if (systemUserId == null || fieldKey == null) {
            return null;
        }

        try {
            SystemUser user = systemUserService.getUserById(systemUserId);
            if (user == null || StringUtils.isBlank(user.getLinkedProviderPersonId())) {
                return null;
            }

            Person linkedPerson = personService.getPersonById(user.getLinkedProviderPersonId());
            if (linkedPerson == null) {
                return null;
            }

            Provider provider = providerService.getProviderByPerson(linkedPerson);
            if (provider == null) {
                return null;
            }

            // Hydrate profile field values if not already done
            if (provider.getProfileFieldValues() == null || provider.getProfileFieldValues().isEmpty()) {
                providerProfileFieldService.hydrateProfileFieldValues(provider);
            }

            // Get custom field values from provider
            Map<String, Object> profileFieldValuesObj = provider.getProfileFieldValues();
            if (profileFieldValuesObj != null && profileFieldValuesObj.containsKey(fieldKey)) {
                Object fieldValue = profileFieldValuesObj.get(fieldKey);
                return StringUtils.trimToNull(fieldValue != null ? String.valueOf(fieldValue) : null);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the most recent validation history for an analysis.
     */
    private History getMostRecentValidationHistory(Analysis analysis) {
        if (analysis == null || StringUtils.isBlank(analysis.getId())) {
            return null;
        }

        // Get the ANALYSIS table ID
        String analysisTableId = referenceTablesService.getReferenceTableByName("ANALYSIS").getId();
        if (StringUtils.isBlank(analysisTableId)) {
            return null;
        }

        List<History> histories = historyService.getHistoryByRefIdAndRefTableId(analysis.getId(), analysisTableId);
        if (histories == null || histories.isEmpty()) {
            return null;
        }

        History mostRecent = null;
        for (History candidate : histories) {
            if (hasStatusChange(candidate) && isMoreRecent(candidate, mostRecent)) {
                String statusId = extractSimpleTag(new String(candidate.getChanges(), StandardCharsets.UTF_8), "statusId");
                // Check if it's a validation status
                if (StringUtils.isNotBlank(statusId) && 
                    (statusId.contains("TechnicalAcceptance") || statusId.contains("BiologicalAcceptance") || statusId.contains("Finalized"))) {
                    mostRecent = candidate;
                }
            }
        }

        return mostRecent;
    }

    private Object coerceValue(String className, String value) {
        if (StringUtils.isBlank(className)) {
            return value;
        }
        if ("java.lang.String".equalsIgnoreCase(className)) {
            return StringUtils.defaultString(value);
        }
        if ("java.lang.Boolean".equalsIgnoreCase(className) || "boolean".equalsIgnoreCase(className)) {
            return Boolean.valueOf(StringUtils.defaultString(value));
        }
        try {
            BigDecimal parsedNumber = parseNumericValue(value);
            if ("java.lang.Integer".equalsIgnoreCase(className) || "int".equalsIgnoreCase(className)) {
                return parsedNumber == null ? null : parsedNumber.intValueExact();
            }
            if ("java.lang.Long".equalsIgnoreCase(className) || "long".equalsIgnoreCase(className)) {
                return parsedNumber == null ? null : parsedNumber.longValueExact();
            }
            if ("java.lang.Double".equalsIgnoreCase(className) || "double".equalsIgnoreCase(className)) {
                return parsedNumber == null ? null : parsedNumber.doubleValue();
            }
            if ("java.lang.Float".equalsIgnoreCase(className) || "float".equalsIgnoreCase(className)) {
                return parsedNumber == null ? null : parsedNumber.floatValue();
            }
        } catch (Exception e) {
            return null;
        }
        return value;
    }

    private Object resolveConditionalValue(JSONObject mapping, String className, ClinicalPatientData first) {
        if (mapping == null) {
            return null;
        }

        String conditionSource = StringUtils.trimToNull(mapping.optString("conditionSource"));
        BigDecimal actualValue = parseNumericValue(resolveSourceValue(conditionSource, first));
        if (conditionSource == null || actualValue == null) {
            return null;
        }

        JSONArray rules = mapping.optJSONArray("rules");
        if (rules != null && !rules.isEmpty()) {
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null) {
                    continue;
                }
                BigDecimal comparedValue = parseNumericValue(StringUtils.trimToNull(rule.optString("compareTo")));
                if (comparedValue == null) {
                    continue;
                }
                if (evaluateNumericCondition(actualValue, rule.optString("operator"), comparedValue)) {
                    return resolveConditionalBranchValue(
                            StringUtils.defaultIfBlank(StringUtils.trimToNull(rule.optString("resultType")), "empty"),
                            StringUtils.trimToNull(rule.optString("resultValue")), className, first);
                }
            }

            String fallbackType = StringUtils.defaultIfBlank(StringUtils.trimToNull(mapping.optString("fallbackType")),
                    "empty");
            String fallbackValue = StringUtils.trimToNull(mapping.optString("fallbackValue"));
            return resolveConditionalBranchValue(fallbackType, fallbackValue, className, first);
        }

        BigDecimal comparedValue = parseNumericValue(StringUtils.trimToNull(mapping.optString("compareTo")));
        if (comparedValue == null) {
            return null;
        }

        boolean matches = evaluateNumericCondition(actualValue, mapping.optString("operator"), comparedValue);
        String branchTypeKey = matches ? "trueType" : "falseType";
        String branchValueKey = matches ? "trueValue" : "falseValue";
        String branchType = StringUtils.defaultIfBlank(StringUtils.trimToNull(mapping.optString(branchTypeKey)), "empty");
        String branchValue = StringUtils.trimToNull(mapping.optString(branchValueKey));
        return resolveConditionalBranchValue(branchType, branchValue, className, first);
    }

    private Object resolveConditionalBranchValue(String branchType, String branchValue, String className,
            ClinicalPatientData first) {
        if ("constant".equalsIgnoreCase(branchType)) {
            return coerceValue(className, branchValue);
        }
        if ("source".equalsIgnoreCase(branchType)) {
            return coerceValue(className, resolveSourceValue(branchValue, first));
        }
        return null;
    }

    static BigDecimal parseNumericValue(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }

        String normalized = rawValue.trim().replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            if (normalized.lastIndexOf(',') > normalized.lastIndexOf('.')) {
                normalized = normalized.replace(".", "").replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(',', '.');
        }

        try {
            return new BigDecimal(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean evaluateNumericCondition(BigDecimal actualValue, String rawOperator, BigDecimal comparedValue) {
        if (actualValue == null || comparedValue == null) {
            return false;
        }

        int comparison = actualValue.compareTo(comparedValue);
        String operator = normalizeConditionalOperator(rawOperator);
        if ("lt".equals(operator)) {
            return comparison < 0;
        }
        if ("lte".equals(operator)) {
            return comparison <= 0;
        }
        if ("eq".equals(operator)) {
            return comparison == 0;
        }
        if ("gte".equals(operator)) {
            return comparison >= 0;
        }
        if ("gt".equals(operator)) {
            return comparison > 0;
        }
        return false;
    }

    private static String normalizeConditionalOperator(String rawOperator) {
        if (StringUtils.isBlank(rawOperator)) {
            return "lt";
        }
        String operator = rawOperator.trim();
        if ("lt".equalsIgnoreCase(operator) || "<".equals(operator)) {
            return "lt";
        }
        if ("lte".equalsIgnoreCase(operator) || "<=".equals(operator)) {
            return "lte";
        }
        if ("eq".equalsIgnoreCase(operator) || "=".equals(operator) || "==".equals(operator)) {
            return "eq";
        }
        if ("gte".equalsIgnoreCase(operator) || ">=".equals(operator)) {
            return "gte";
        }
        if ("gt".equalsIgnoreCase(operator) || ">".equals(operator)) {
            return "gt";
        }
        return "";
    }

    private InputStream resolveImageInputStream(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if ("validatorSignature".equalsIgnoreCase(value)) {
            return resolveValidatorInfo().signatureStream;
        }
        if (StringUtils.startsWithIgnoreCase(value, "imageId:")) {
            String imageId = StringUtils.trimToNull(value.substring("imageId:".length()));
            if (imageId != null) {
                try {
                    Image image = imageService.get(imageId);
                    return image == null || image.getImage() == null ? null : new ByteArrayInputStream(image.getImage());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        Optional<Image> image = imageService.getImageBySiteInfoName(value);
        return image.isPresent() && image.get().getImage() != null ? new ByteArrayInputStream(image.get().getImage())
                : null;
    }

    private ClinicalPatientData getFirstReportItem() {
        if (reportItems == null || reportItems.isEmpty()) {
            return null;
        }
        return reportItems.get(0);
    }

    private String resolveSourceValue(String source, ClinicalPatientData first) {
        if (StringUtils.isBlank(source)) {
            return "";
        }

        String normalized = source.trim();
        if ("patientName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientName());
        }
        if ("dni".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getDni());
        }
        if ("passportNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPassportNumber());
        }
        if ("foreignId".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getForeignId());
        }
        if ("nationalId".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getNationalId());
        }
        if ("subjectNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSubjectNumber());
        }
        if ("sampleCug".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSampleCug());
        }
        if ("accessionNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getAccessionNumber());
        }
        if ("gender".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getGender());
        }
        if ("dob".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getDob());
        }
        if ("patientSiteNumber".equalsIgnoreCase(normalized) || "contact".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientSiteNumber());
        }
        if ("prescriber".equalsIgnoreCase(normalized) || "requestingPhysician".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultIfBlank(first.getPrescriber(), first.getContactInfo());
        }
        if ("requesterFirstName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterFirstName());
        }
        if ("requesterLastName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterLastName());
        }
        if ("requesterPhone".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterPhone());
        }
        if ("requesterEmail".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterEmail());
        }
        if ("requesterCmp".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterCmp());
        }
        if ("requesterRne".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterRne());
        }
        if ("requesterSpecialty".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterSpecialty());
        }
        if ("siteInfo".equalsIgnoreCase(normalized) || "referringSite".equalsIgnoreCase(normalized)) {
            return resolveReferenceCenter(first);
        }
        if ("collectionDateTime".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getCollectionDateTime());
        }
        if ("collectionDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : extractCollectionDate(first.getCollectionDateTime());
        }
        if ("sampleType".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSampleType());
        }
        if ("validationDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getValidationDate());
        }
        if ("validatorName".equalsIgnoreCase(normalized)) {
            return resolveValidatorInfo().displayName;
        }
        if ("validatorSpecialty".equalsIgnoreCase(normalized)) {
            return resolveValidatorInfo().specialty;
        }
        String analystValue = resolveAnalystSourceValue(normalized);
        if (analystValue != null) {
            return analystValue;
        }
        if ("orderFinishDate".equalsIgnoreCase(normalized) || "resultDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getOrderFinishDate());
        }
        if ("orderDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getOrderDate());
        }
        if ("testDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getTestDate());
        }
        if ("analysisResult1".equalsIgnoreCase(normalized) || "analysisResult2".equalsIgnoreCase(normalized)) {
            int position = "analysisResult2".equalsIgnoreCase(normalized) ? 1 : 0;
            return resolveAnalysisResult(position);
        }

        if (normalized.startsWith("orderAdditional.") || normalized.startsWith("sampleAdditional.")
                || normalized.startsWith("testAdditional.") || normalized.startsWith("additional.")) {
            return resolveAdditionalValue(normalized);
        }

        String fromAdditional = resolveAdditionalValue(normalized);
        return StringUtils.defaultString(fromAdditional);
    }

    private String resolveAnalystSourceValue(String sourceId) {
        Matcher matcher = ANALYST_SOURCE_PATTERN.matcher(StringUtils.defaultString(sourceId));
        if (!matcher.matches()) {
            return null;
        }

        int slotNumber;
        try {
            slotNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return "";
        }

        if (slotNumber < 1) {
            return "";
        }

        ProfessionalInfo analystInfo = resolveBiologistInfo(slotNumber - 1);
        String fieldName = StringUtils.defaultString(matcher.group(2));
        if ("Name".equalsIgnoreCase(fieldName)) {
            return analystInfo.displayName;
        }
        if ("Specialty".equalsIgnoreCase(fieldName)) {
            return analystInfo.specialty;
        }
        if ("CbpCode".equalsIgnoreCase(fieldName)) {
            return analystInfo.cbpCode;
        }
        return "";
    }

    private ProfessionalInfo resolveBiologistInfo(int slotIndex) {
        List<String> userIds = resolveBiologistUserIds();
        if (slotIndex < 0 || slotIndex >= userIds.size()) {
            return ProfessionalInfo.empty();
        }
        return resolveProfessionalInfo(userIds.get(slotIndex));
    }

    private List<String> resolveBiologistUserIds() {
        if (cachedBiologistUserIds != null) {
            return cachedBiologistUserIds;
        }

        List<String> userIds = new ArrayList<>();
        if (reportItems == null || reportItems.isEmpty()) {
            cachedBiologistUserIds = userIds;
            return cachedBiologistUserIds;
        }

        for (ClinicalPatientData item : reportItems) {
            if (item == null || StringUtils.isBlank(item.getAnalysisId())) {
                continue;
            }

            Analysis analysis = getAnalysisForReportItem(item);
            if (analysis == null || analysis.getTest() == null || StringUtils.isBlank(analysis.getTest().getId())) {
                continue;
            }

            List<TestAdditionalFieldPayload> definitions = biologistDefinitionsByTestId.computeIfAbsent(
                    analysis.getTest().getId(), testId -> {
                        List<TestAdditionalFieldPayload> fetched = testAdditionalFieldService.getFieldsForTest(testId,
                                false);
                        return fetched == null ? Collections.emptyList() : new ArrayList<>(fetched);
                    });

            definitions.stream().filter(this::isBiologistSelectorField)
                    .sorted(Comparator.comparing((TestAdditionalFieldPayload field) -> field.getSortOrder() == null
                            ? Integer.MAX_VALUE
                            : field.getSortOrder()).thenComparing(field -> StringUtils.defaultString(field.getFieldKey()),
                                    String.CASE_INSENSITIVE_ORDER))
                    .forEach(field -> {
                        String selectedUserId = item.getAdditionalFieldValue(field.getFieldKey());
                        if (StringUtils.isNotBlank(selectedUserId)) {
                            userIds.add(selectedUserId.trim());
                        }
                    });
        }

        cachedBiologistUserIds = userIds;
        return cachedBiologistUserIds;
    }

    private boolean isBiologistSelectorField(TestAdditionalFieldPayload field) {
        return field != null && Boolean.TRUE.equals(field.getActive())
                && isUserSelectorFieldType(field.getFieldType())
                && StringUtils.isNotBlank(field.getFieldKey());
    }

    private boolean isUserSelectorFieldType(String fieldType) {
        String normalizedFieldType = StringUtils.trimToNull(fieldType);
        return StringUtils.equalsIgnoreCase(USER_FIELD_TYPE, normalizedFieldType);
    }

    private Analysis getAnalysisForReportItem(ClinicalPatientData item) {
        if (item == null || StringUtils.isBlank(item.getAnalysisId())) {
            return null;
        }
        return analysisById.computeIfAbsent(item.getAnalysisId(), analysisService::getAnalysisById);
    }

    private ProfessionalInfo resolveValidatorInfo() {
        if (cachedValidatorInfo != null) {
            return cachedValidatorInfo;
        }

        if (previewValidated && StringUtils.isNotBlank(systemUserId)) {
            cachedValidatorInfo = resolveProfessionalInfo(systemUserId);
            return cachedValidatorInfo;
        }

        String analysisTableId = Optional.ofNullable(referenceTablesService.getReferenceTableByName(ANALYSIS_REFERENCE_TABLE))
                .map(referenceTable -> referenceTable.getId()).orElse(null);
        if (StringUtils.isBlank(analysisTableId)) {
            cachedValidatorInfo = ProfessionalInfo.empty();
            return cachedValidatorInfo;
        }

        History latestValidation = null;
        for (ClinicalPatientData item : reportItems == null ? Collections.<ClinicalPatientData>emptyList() : reportItems) {
            if (item == null || StringUtils.isBlank(item.getAnalysisId())) {
                continue;
            }

            List<History> historyList = historyService.getHistoryByRefIdAndRefTableId(item.getAnalysisId(), analysisTableId);
            if (historyList == null || historyList.isEmpty()) {
                continue;
            }

            History latestStatusChange = null;
            History latestAnyUpdate = null;
            for (History history : historyList) {
                if (history == null || !"U".equals(history.getActivity())) {
                    continue;
                }
                if (latestAnyUpdate == null || isMoreRecent(history, latestAnyUpdate)) {
                    latestAnyUpdate = history;
                }
                if (hasStatusChange(history) && (latestStatusChange == null || isMoreRecent(history, latestStatusChange))) {
                    latestStatusChange = history;
                }
            }

            History selected = latestStatusChange != null ? latestStatusChange : latestAnyUpdate;
            if (selected != null && (latestValidation == null || isMoreRecent(selected, latestValidation))) {
                latestValidation = selected;
            }
        }

        if (latestValidation == null || StringUtils.isBlank(latestValidation.getSysUserId())) {
            cachedValidatorInfo = ProfessionalInfo.empty();
            return cachedValidatorInfo;
        }

        cachedValidatorInfo = resolveProfessionalInfo(latestValidation.getSysUserId());
        return cachedValidatorInfo;
    }

    private ProfessionalInfo resolveProfessionalInfo(String systemUserId) {
        if (StringUtils.isBlank(systemUserId)) {
            return ProfessionalInfo.empty();
        }
        return professionalInfoByUserId.computeIfAbsent(systemUserId, this::buildProfessionalInfo);
    }

    private ProfessionalInfo buildProfessionalInfo(String systemUserId) {
        SystemUser user = systemUserService.getUserById(systemUserId);
        if (user == null) {
            return ProfessionalInfo.empty();
        }

        Provider linkedProvider = resolveLinkedProvider(user);
        String displayName = resolveProfessionalDisplayName(user, linkedProvider);
        String specialty = linkedProvider == null ? "" : StringUtils.defaultString(linkedProvider.getSpecialty());
        String cbpCode = linkedProvider == null ? "" : StringUtils.defaultString(linkedProvider.getCbpCode());
        ByteArrayInputStream signatureStream = decodeSignatureImage(user.getSignatureImageData());
        return new ProfessionalInfo(displayName, specialty, cbpCode, signatureStream);
    }

    private Provider resolveLinkedProvider(SystemUser user) {
        if (user == null || StringUtils.isBlank(user.getLinkedProviderPersonId())) {
            return null;
        }
        Person linkedPerson = personService.getPersonById(user.getLinkedProviderPersonId());
        if (linkedPerson == null) {
            return null;
        }
        return providerService.getProviderByPerson(linkedPerson);
    }

    private String resolveProfessionalDisplayName(SystemUser user, Provider provider) {
        if (provider != null && provider.getPerson() != null) {
            String fullName = StringUtils.normalizeSpace(StringUtils.defaultString(provider.getPerson().getFirstName())
                    + " " + StringUtils.defaultString(provider.getPerson().getLastName()));
            if (StringUtils.isNotBlank(fullName)) {
                return fullName;
            }
        }

        String userFullName = StringUtils.normalizeSpace(
                StringUtils.defaultString(user.getFirstName()) + " " + StringUtils.defaultString(user.getLastName()));
        if (StringUtils.isNotBlank(userFullName)) {
            return userFullName;
        }

        return StringUtils.defaultIfBlank(StringUtils.defaultString(user.getDisplayName()),
                StringUtils.defaultString(user.getLoginName()));
    }

    private ByteArrayInputStream decodeSignatureImage(String signatureImageData) {
        String raw = StringUtils.trimToNull(signatureImageData);
        if (raw == null) {
            return null;
        }
        try {
            String base64Payload = raw;
            if (StringUtils.startsWithIgnoreCase(base64Payload, "data:")) {
                int commaIndex = base64Payload.indexOf(',');
                if (commaIndex > -1 && commaIndex + 1 < base64Payload.length()) {
                    base64Payload = base64Payload.substring(commaIndex + 1);
                }
            }
            byte[] decoded = Base64.getDecoder().decode(base64Payload);
            return decoded.length == 0 ? null : new ByteArrayInputStream(decoded);
        } catch (IllegalArgumentException decodeError) {
            return null;
        }
    }

    private boolean isMoreRecent(History candidate, History current) {
        if (candidate == null) {
            return false;
        }
        if (current == null || current.getTimestamp() == null) {
            return true;
        }
        if (candidate.getTimestamp() == null) {
            return false;
        }
        return candidate.getTimestamp().after(current.getTimestamp());
    }

    private boolean hasStatusChange(History history) {
        if (history == null || history.getChanges() == null || history.getChanges().length == 0) {
            return false;
        }
        String changes = new String(history.getChanges(), StandardCharsets.UTF_8);
        return StringUtils.isNotBlank(extractSimpleTag(changes, "statusId"));
    }

    private String extractSimpleTag(String xmlText, String tagName) {
        if (StringUtils.isBlank(xmlText) || StringUtils.isBlank(tagName)) {
            return null;
        }
        String startTag = "<" + tagName + ">";
        int begin = xmlText.indexOf(startTag);
        if (begin < 0) {
            return null;
        }
        begin += startTag.length();
        int end = xmlText.indexOf("</" + tagName + ">");
        if (end < 0 || end < begin) {
            return null;
        }
        return xmlText.substring(begin, end);
    }

    static String extractCollectionDate(String rawValue) {
        String normalized = StringUtils.trimToEmpty(rawValue);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }

        Matcher matcher = COLLECTION_DATE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return StringUtils.defaultString(matcher.group(1));
        }

        return normalized;
    }

    private String resolveAnalysisResult(int position) {
        if (reportItems == null || reportItems.isEmpty()) {
            return "";
        }
        List<String> values = reportItems.stream().map(ClinicalPatientData::getResult).filter(StringUtils::isNotBlank)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        return values.size() > position ? values.get(position) : "";
    }

    private String resolveReferenceCenter(ClinicalPatientData first) {
        String siteInfo = sanitizeSiteInfo(first == null ? "" : first.getSiteInfo());
        if (StringUtils.isNotBlank(siteInfo)) {
            return siteInfo;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add("orderAdditional.reference_center");
        candidates.add("orderAdditional.centro_referencia");
        candidates.add("orderAdditional.referring_site");
        candidates.add("orderAdditional.referringSite");
        candidates.add("reference_center");
        candidates.add("centro_referencia");
        candidates.add("referring_site");
        candidates.add("referringSite");
        for (String key : candidates) {
            String value = resolveAdditionalValue(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String sanitizeSiteInfo(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String sanitized = raw.replace("<br/>", " ").replace("<br />", " ").replace("<br>", " ");
        return sanitized.replaceAll("\\s+", " ").trim();
    }

    private String resolveAdditionalValue(String key) {
        if (StringUtils.isBlank(key) || reportItems == null || reportItems.isEmpty()) {
            return "";
        }

        List<String> lookupKeys = expandAdditionalFieldLookupKeys(key);
        for (ClinicalPatientData data : reportItems) {
            Map<String, String> values = data.getAdditionalFieldValues();
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (String lookupKey : lookupKeys) {
                String value = StringUtils.trimToNull(values.get(lookupKey));
                if (value != null) {
                    return value;
                }
            }
        }
        return "";
    }

    private List<String> expandAdditionalFieldLookupKeys(String baseKey) {
        if (StringUtils.isBlank(baseKey)) {
            return Collections.emptyList();
        }

        String trimmed = baseKey.trim();
        LinkedHashSet<String> lookupKeys = new LinkedHashSet<>();
        lookupKeys.add(trimmed);

        if (trimmed.startsWith("testAdditional.")) {
            String rawKey = trimmed.substring("testAdditional.".length());
            if (StringUtils.isNotBlank(rawKey)) {
                lookupKeys.add(rawKey);
                lookupKeys.add("additional." + rawKey);
            }
            return new ArrayList<>(lookupKeys);
        }

        if (trimmed.startsWith("additional.")) {
            String rawKey = trimmed.substring("additional.".length());
            if (StringUtils.isNotBlank(rawKey)) {
                lookupKeys.add(rawKey);
                lookupKeys.add("testAdditional." + rawKey);
            }
            return new ArrayList<>(lookupKeys);
        }

        if (trimmed.startsWith("orderAdditional.") || trimmed.startsWith("sampleAdditional.")) {
            String rawKey = trimmed.substring(trimmed.indexOf('.') + 1);
            if (StringUtils.isNotBlank(rawKey)) {
                lookupKeys.add(rawKey);
            }
            return new ArrayList<>(lookupKeys);
        }

        lookupKeys.add("testAdditional." + trimmed);
        lookupKeys.add("additional." + trimmed);
        return new ArrayList<>(lookupKeys);
    }

    @SuppressWarnings("unused")
    private String normalizeAliasToken(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String unaccented = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return unaccented.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static class TemplateParameterDefinition {
        private final String name;
        private final String className;

        private TemplateParameterDefinition(String name, String className) {
            this.name = name;
            this.className = className;
        }
    }

    private static class ProfessionalInfo {
        private final String displayName;
        private final String specialty;
        private final String cbpCode;
        private final ByteArrayInputStream signatureStream;

        private ProfessionalInfo(String displayName, String specialty, String cbpCode,
                ByteArrayInputStream signatureStream) {
            this.displayName = StringUtils.defaultString(displayName);
            this.specialty = StringUtils.defaultString(specialty);
            this.cbpCode = StringUtils.defaultString(cbpCode);
            this.signatureStream = signatureStream;
        }

        private static ProfessionalInfo empty() {
            return new ProfessionalInfo("", "", "", null);
        }
    }
}
