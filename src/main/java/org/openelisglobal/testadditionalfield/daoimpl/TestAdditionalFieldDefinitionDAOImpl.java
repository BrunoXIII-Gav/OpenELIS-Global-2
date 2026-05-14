package org.openelisglobal.testadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldDefinitionDAO;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TestAdditionalFieldDefinitionDAOImpl extends BaseDAOImpl<TestAdditionalFieldDefinition, Integer>
        implements TestAdditionalFieldDefinitionDAO {

    public TestAdditionalFieldDefinitionDAOImpl() {
        super(TestAdditionalFieldDefinition.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldDefinition> findByTestId(Integer testId, boolean includeInactive) {
        String sql = "select d.id, d.test_id, d.field_key, d.display_name, d.field_type, d.required, d.active, "
                + "d.sort_order, d.default_value, d.max_length, d.metadata_json, d.sys_user_id "
                + "from test_additional_field_def d where d.test_id = :testId"
                + (includeInactive ? "" : " and d.active = true") + " order by d.sort_order asc, d.id asc";
        NativeQuery<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameter("testId", testId);
        return mapRows(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldDefinition> findByTestIds(List<Integer> testIds, boolean activeOnly) {
        if (testIds == null || testIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = "select d.id, d.test_id, d.field_key, d.display_name, d.field_type, d.required, d.active, "
                + "d.sort_order, d.default_value, d.max_length, d.metadata_json, d.sys_user_id "
                + "from test_additional_field_def d where d.test_id in (:testIds)"
                + (activeOnly ? " and d.active = true" : "") + " order by d.test_id asc, d.sort_order asc, d.id asc";
        NativeQuery<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameterList("testIds", testIds);
        return mapRows(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldDefinition> findByIds(List<Integer> fieldDefIds) {
        if (fieldDefIds == null || fieldDefIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = "select d.id, d.test_id, d.field_key, d.display_name, d.field_type, d.required, d.active, "
                + "d.sort_order, d.default_value, d.max_length, d.metadata_json, d.sys_user_id "
                + "from test_additional_field_def d where d.id in (:fieldDefIds)";
        NativeQuery<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameterList("fieldDefIds", fieldDefIds);
        return mapRows(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestAdditionalFieldDefinition> findByTestIdAndFieldKey(Integer testId, String fieldKey) {
        String sql = "select d.id, d.test_id, d.field_key, d.display_name, d.field_type, d.required, d.active, "
                + "d.sort_order, d.default_value, d.max_length, d.metadata_json, d.sys_user_id "
                + "from test_additional_field_def d " + "where d.test_id = :testId and lower(d.field_key) = :fieldKey";
        NativeQuery<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameter("testId", testId);
        query.setParameter("fieldKey", fieldKey.toLowerCase());
        List<TestAdditionalFieldDefinition> mapped = mapRows(query.getResultList());
        if (mapped.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapped.get(0));
    }

    private List<TestAdditionalFieldDefinition> mapRows(List<?> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().filter(Object[].class::isInstance).map(Object[].class::cast).map(this::mapRow).toList();
    }

    private TestAdditionalFieldDefinition mapRow(Object[] row) {
        TestAdditionalFieldDefinition definition = new TestAdditionalFieldDefinition();
        definition.setId(asInteger(row[0]));
        definition.setTestId(asInteger(row[1]));
        definition.setFieldKey(asString(row[2]));
        definition.setDisplayName(asString(row[3]));
        definition.setFieldType(asString(row[4]));
        definition.setRequired(asBoolean(row[5]));
        definition.setActive(asBoolean(row[6]));
        definition.setSortOrder(asInteger(row[7]));
        definition.setDefaultValue(asString(row[8]));
        definition.setMaxLength(asInteger(row[9]));
        definition.setMetadataJson(asString(row[10]));
        definition.setSysUserId(asString(row[11]));
        return definition;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
