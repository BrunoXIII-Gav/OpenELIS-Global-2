package org.openelisglobal.sampleadditionalfield.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.sampleadditionalfield.bean.SampleFixedFieldConfigPayload;
import org.openelisglobal.sampleadditionalfield.dao.SampleFixedFieldConfigDAO;
import org.openelisglobal.sampleadditionalfield.valueholder.SampleFixedFieldConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleAdditionalFieldServiceImpl implements SampleAdditionalFieldService {

    private static final List<FixedFieldDefault> FIXED_FIELD_DEFAULTS = List.of(
            new FixedFieldDefault("rejected", 10, false, false), new FixedFieldDefault("cug", 15, true, true),
            new FixedFieldDefault("quantity", 20, false, false), new FixedFieldDefault("uom", 30, false, false),
            new FixedFieldDefault("collectionDate", 40, false, false),
            new FixedFieldDefault("collectionTime", 50, false, false),
            new FixedFieldDefault("collector", 60, false, false),
            new FixedFieldDefault("storageLocation", 70, false, false),
            new FixedFieldDefault("panels", 80, false, false), new FixedFieldDefault("tests", 90, false, false),
            new FixedFieldDefault("referral", 100, false, false));

    private static final Set<String> FIXED_FIELD_KEYS = FIXED_FIELD_DEFAULTS.stream().map(f -> f.fieldKey.toLowerCase())
            .collect(Collectors.toSet());

    @Autowired
    private SampleFixedFieldConfigDAO fixedFieldConfigDAO;

    @Override
    @Transactional(readOnly = true)
    public List<SampleFixedFieldConfigPayload> getFixedFieldConfigs() {
        List<SampleFixedFieldConfig> configured = fixedFieldConfigDAO.findAllOrdered();
        Map<String, SampleFixedFieldConfig> byKey = configured.stream()
                .collect(Collectors.toMap(c -> c.getFieldKey().toLowerCase(), c -> c, (left, right) -> left));

        List<SampleFixedFieldConfigPayload> result = new ArrayList<>();
        for (FixedFieldDefault def : FIXED_FIELD_DEFAULTS) {
            SampleFixedFieldConfig config = byKey.get(def.fieldKey.toLowerCase());
            if (config == null) {
                SampleFixedFieldConfigPayload fallback = new SampleFixedFieldConfigPayload();
                fallback.setFieldKey(def.fieldKey);
                fallback.setVisible(true);
                fallback.setRequired(def.required);
                fallback.setReadonly(def.readonly);
                fallback.setSortOrder(def.sortOrder);
                result.add(fallback);
            } else {
                result.add(mapToPayload(config));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void upsertFixedFieldConfigs(List<SampleFixedFieldConfigPayload> payloads, String currentUserId) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        for (SampleFixedFieldConfigPayload payload : payloads) {
            if (payload == null || StringUtils.isBlank(payload.getFieldKey())) {
                continue;
            }
            String normalizedKey = payload.getFieldKey().trim();
            if (!FIXED_FIELD_KEYS.contains(normalizedKey.toLowerCase())) {
                throw new IllegalArgumentException("Unsupported sample fixed field key: " + normalizedKey);
            }
            SampleFixedFieldConfig entity = fixedFieldConfigDAO.findByFieldKey(normalizedKey)
                    .orElseGet(SampleFixedFieldConfig::new);
            entity.setFieldKey(normalizedKey);
            entity.setVisible(payload.getVisible() == null || payload.getVisible());
            entity.setRequired(payload.getRequired() != null && payload.getRequired());
            entity.setReadonly(payload.getReadonly() != null && payload.getReadonly());
            entity.setSortOrder(
                    payload.getSortOrder() == null ? getDefaultSortOrder(normalizedKey) : payload.getSortOrder());
            entity.setSysUserId(currentUserId);

            if (entity.getId() == null) {
                fixedFieldConfigDAO.insert(entity);
            } else {
                fixedFieldConfigDAO.update(entity);
            }
        }
    }

    private Integer getDefaultSortOrder(String fieldKey) {
        return FIXED_FIELD_DEFAULTS.stream().filter(f -> f.fieldKey.equalsIgnoreCase(fieldKey)).map(f -> f.sortOrder)
                .findFirst().orElse(0);
    }

    private SampleFixedFieldConfigPayload mapToPayload(SampleFixedFieldConfig config) {
        SampleFixedFieldConfigPayload payload = new SampleFixedFieldConfigPayload();
        payload.setId(config.getId());
        payload.setFieldKey(config.getFieldKey());
        payload.setVisible(config.getVisible());
        payload.setRequired(config.getRequired());
        payload.setReadonly(config.getReadonly());
        payload.setSortOrder(config.getSortOrder());
        return payload;
    }

    private static class FixedFieldDefault {
        private final String fieldKey;
        private final Integer sortOrder;
        private final boolean required;
        private final boolean readonly;

        private FixedFieldDefault(String fieldKey, Integer sortOrder, boolean required, boolean readonly) {
            this.fieldKey = fieldKey;
            this.sortOrder = sortOrder;
            this.required = required;
            this.readonly = readonly;
        }
    }
}
