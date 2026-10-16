package org.openelisglobal.testconfiguration.packageio.dao;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.*;
import org.openelisglobal.common.valueholder.BaseObject;
import org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType;
import org.openelisglobal.test.service.SystemTestCatalog;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testconfiguration.packageio.valueholder.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class TestConfigurationPackageDAOImpl implements TestConfigurationPackageDAO {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private SystemTestCatalog systemTestCatalog;

    private EntityPersister persister(String kind) {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class).getMetamodel()
                .entityPersister(TestPackageRegistry.type(kind));
    }

    @Override
    public Map<String, Attribute> attributes(String kind) {
        EntityPersister p = persister(kind);
        Map<String, Attribute> result = new LinkedHashMap<>();
        for (int i = 0; i < p.getPropertyNames().length; i++) {
            String name = p.getPropertyNames()[i];
            if (TestPackageRegistry.omitted(kind, name))
                continue;
            Type t = p.getPropertyTypes()[i];
            if (t.isCollectionType() || t.isComponentType())
                continue;
            String target = t.isEntityType() ? t.getReturnedClass().getSimpleName() : null;
            if (target != null && !TestPackageRegistry.TYPES.containsKey(target))
                continue;
            result.put(name,
                    new Attribute(t.getReturnedClass(), target, t.isEntityType(), p.getPropertyNullability()[i]));
        }
        return result;
    }

    @Override
    public Snapshot snapshot(String kind, String id) {
        Object entity = entityManager.unwrap(Session.class)
                .createQuery(fetchedQuery(kind) + " where e.id = :id", Object.class)
                .setParameter("id", idValue(kind, id), persister(kind).getIdentifierType()).uniqueResult();
        if (entity == null)
            throw new IllegalArgumentException("testPackage.issue.reference");
        return snapshot(kind, entity);
    }

    private Snapshot snapshot(String kind, Object entity) {
        EntityPersister p = persister(kind);
        Map<String, Object> raw = new LinkedHashMap<>();
        for (var attr : attributes(kind).entrySet())
            raw.put(attr.getKey(), p.getPropertyValue(entity, attr.getKey()));
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Link> links = new LinkedHashMap<>();
        for (var attr : attributes(kind).entrySet()) {
            Object value = raw.get(attr.getKey());
            String target = attr.getValue().targetKind();
            if (target == null)
                target = TestPackageRegistry.idReference(kind, attr.getKey(), raw);
            if (target != null && value != null && !"".equals(value)) {
                String id = attr.getValue().association() ? String.valueOf(persister(target).getIdentifier(value,
                        entityManager.unwrap(SharedSessionContractImplementor.class))) : String.valueOf(value);
                links.put(attr.getKey(), new Link(target, id));
            } else if (target == null)
                values.put(attr.getKey(), value);
        }
        String id = String
                .valueOf(p.getIdentifier(entity, entityManager.unwrap(SharedSessionContractImplementor.class)));
        String stable = "Test".equals(kind) ? ((Test) entity).getGuid() : null;
        TestConfigurationIdentity identity = entityManager.find(TestConfigurationIdentity.class, kind + ":" + id);
        if (identity != null)
            stable = identity.sourceKey;
        if (stable == null || stable.isBlank()) {
            TestConfigurationImportLock origin = entityManager.find(TestConfigurationImportLock.class, 1);
            if (origin == null)
                throw new IllegalStateException("testPackage.issue.migration");
            stable = UUID.nameUUIDFromBytes((origin.originKey + ":" + kind + ":" + id).getBytes(StandardCharsets.UTF_8))
                    .toString();
        }
        return new Snapshot(kind, id, stable, values, links);
    }

    @Override
    public List<Snapshot> list(String kind) {
        return entityManager.createQuery(fetchedQuery(kind), Object.class).getResultList().stream()
                .map(e -> snapshot(kind, e)).toList();
    }

    @Override
    public List<Snapshot> userCreatedTests() {
        // Historical manual definitions had no creation marker. Do not require a
        // manual confirmation for them; exclude explicit system origins and the
        // installation identities independently of names and dates.
        return entityManager.createQuery(fetchedQuery("Test")
                + " where (e.userCreated = true and e.creationOriginVerified = true) or (e.creationOriginVerified = false and e.id not in (select o.id from TestCreationOrigin o where o.userCreated = false)) order by e.description",
                Object.class).getResultList().stream().map(Test.class::cast)
                .filter(t -> !systemTestCatalog.contains(t.getGuid())).map(e -> snapshot("Test", e)).toList();
    }

    @Override
    public List<Snapshot> related(String kind, String property, Collection<String> ids) {
        if (ids.isEmpty())
            return List.of();
        boolean association = property.endsWith(".id");
        String name = association ? property.substring(0, property.length() - 3) : property;
        Attribute attribute = attributes(kind).get(name);
        if (attribute == null)
            throw new IllegalArgumentException("testPackage.issue.property");
        Type bindType = association || attribute.javaType() == String.class
                ? new CustomType(new LIMSStringNumberUserType())
                : IntegerType.INSTANCE;
        Collection<?> parameters = association || attribute.javaType() == String.class ? ids
                : ids.stream().map(Integer::valueOf).toList();
        return entityManager.unwrap(Session.class)
                .createQuery(fetchedQuery(kind) + " where e." + property + " in (:ids)", Object.class)
                .setParameterList("ids", parameters, bindType).list().stream().map(e -> snapshot(kind, e)).toList();
    }

    private String fetchedQuery(String kind) {
        StringBuilder query = new StringBuilder("select e from ").append(TestPackageRegistry.type(kind).getName())
                .append(" e");
        int alias = 0;
        for (var attribute : attributes(kind).entrySet())
            if (attribute.getValue().association())
                query.append(" left join fetch e.").append(attribute.getKey()).append(" a").append(alias++);
        return query.toString();
    }

    private Object idValue(String kind, String id) {
        return persister(kind).getIdentifierType().getReturnedClass() == Integer.class ? Integer.valueOf(id) : id;
    }

    @Override
    @Transactional
    public String insert(String kind, Map<String, Object> values, Map<String, String> references, String actorId) {
        try {
            Object entity = TestPackageRegistry.type(kind).getDeclaredConstructor().newInstance();
            EntityPersister p = persister(kind);
            for (var value : values.entrySet())
                setValue(p, entity, value.getKey(), value.getValue());
            for (var ref : references.entrySet())
                setReference(kind, entity, ref.getKey(), ref.getValue());
            if (entity instanceof BaseObject<?> base)
                base.setSysUserId(actorId);
            if (entity instanceof Test test) {
                test.setGuid(UUID.randomUUID().toString());
                test.setCreationOriginVerifiedBy(actorId);
            }
            if (entity instanceof org.openelisglobal.test.valueholder.TestSection section) {
                section.setCreationOrigin(org.openelisglobal.test.valueholder.TestSection.ORIGIN_USER);
                section.setCreationOriginBy(actorId);
                section.setCreationOriginAt(new java.sql.Timestamp(System.currentTimeMillis()));
            }
            entityManager.persist(entity);
            return String
                    .valueOf(p.getIdentifier(entity, entityManager.unwrap(SharedSessionContractImplementor.class)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("testPackage.issue.kind", e);
        }
    }

    private void setReference(String kind, Object entity, String property, String targetId) {
        Attribute attribute = attributes(kind).get(property);
        if (attribute.association()) {
            setValue(persister(kind), entity, property, entityManager.getReference(
                    TestPackageRegistry.type(attribute.targetKind()), idValue(attribute.targetKind(), targetId)));
        } else {
            setValue(persister(kind), entity, property,
                    attribute.javaType() == Integer.class ? Integer.valueOf(targetId) : targetId);
        }
    }

    private void setValue(EntityPersister persister, Object entity, String property, Object value) {
        int index = Arrays.asList(persister.getPropertyNames()).indexOf(property);
        if (index < 0)
            throw new IllegalArgumentException("testPackage.issue.property");
        persister.setPropertyValue(entity, index, value);
    }

    @Override
    @Transactional
    public void link(String kind, String id, String property, String targetId) {
        setReference(kind, entityManager.find(TestPackageRegistry.type(kind), idValue(kind, id)), property, targetId);
    }

    @Override
    @Transactional
    public void flush() {
        entityManager.flush();
    }

    @Override
    @Transactional
    public void lockImports() {
        if (entityManager.find(TestConfigurationImportLock.class, 1, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new IllegalStateException("testPackage.issue.migration");
    }

    @Override
    public Optional<TestConfigurationRevision> findRevision(String fingerprint) {
        return entityManager
                .createQuery("from TestConfigurationRevision r where r.fingerprint = :hash",
                        TestConfigurationRevision.class)
                .setParameter("hash", fingerprint).getResultStream().findFirst();
    }

    @Override
    public Optional<TestConfigurationRevision> latestRevision(String family) {
        return entityManager
                .createQuery("from TestConfigurationRevision r where r.familyKey = :family order by r.createdAt desc",
                        TestConfigurationRevision.class)
                .setParameter("family", family).setMaxResults(1).getResultStream().findFirst();
    }

    @Override
    public List<TestConfigurationRevision> revisions() {
        return entityManager.createQuery("from TestConfigurationRevision r order by r.createdAt desc",
                TestConfigurationRevision.class).setMaxResults(100).getResultList();
    }

    @Override
    @Transactional
    public void saveRevision(TestConfigurationRevision revision) {
        entityManager.persist(revision);
    }

    @Override
    @Transactional
    public void saveIdentity(String kind, String entityId, String sourceKey, String revisionId) {
        TestConfigurationIdentity identity = new TestConfigurationIdentity();
        identity.id = kind + ":" + entityId;
        identity.sourceKey = sourceKey;
        identity.revisionId = revisionId;
        entityManager.persist(identity);
    }
}
