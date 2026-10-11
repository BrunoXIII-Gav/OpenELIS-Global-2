package org.openelisglobal.storage.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hibernate.query.Query;
import org.hibernate.type.CustomType;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.storage.valueholder.SampleStorageAssignment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class StorageSampleScopeDAOImpl extends BaseDAOImpl<SampleItem, String> implements StorageSampleScopeDAO {
    private static final List<String> LOCATION_TYPES = List.of("room", "device", "shelf", "rack", "box");
    private static final List<String> ENTITY_NAMES = List.of("StorageRoom", "StorageDevice", "StorageShelf",
            "StorageRack", "StorageBox");
    private static final List<String> PARENT_PATHS = List.of("", "parentRoom", "parentDevice", "parentShelf",
            "parentRack");

    public StorageSampleScopeDAOImpl() {
        super(SampleItem.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> findSampleItemIdsByLabUnits(Set<String> labUnitIds) {
        if (labUnitIds.isEmpty()) {
            return Set.of();
        }
        Query<String> query = entityManager
                .createQuery("select distinct a.sampleItem.id from Analysis a "
                        + "where coalesce(a.testSection.id, a.test.testSection.id) in :labUnits", String.class)
                .unwrap(Query.class);
        // The production classic translator infers VARCHAR from Java String IDs.
        // Bind with the identifier's mapped numeric type, including inside coalesce.
        return new HashSet<>(
                query.setParameterList("labUnits", labUnitIds, new CustomType(new LIMSStringNumberUserType()))
                        .getResultList());
    }

    @Override
    public List<Object[]> findUnanalysedSampleItemParents() {
        return entityManager
                .createQuery(
                        "select s.id, parent.id from SampleItem s join s.parentSampleItem parent "
                                + "where not exists (select a.id from Analysis a where a.sampleItem.id = s.id)",
                        Object[].class)
                .getResultList();
    }

    @Override
    public Set<String> findAssignedSampleItemIdsInHierarchy(String locationType, Integer locationId) {
        List<Integer> ids = entityManager
                .createQuery("select distinct ssa.sampleItemId from SampleStorageAssignment ssa " + "where "
                        + hierarchyPredicate(locationType, locationId), Integer.class)
                .setParameter("locationId", locationId).getResultList();
        Set<String> result = new HashSet<>();
        ids.stream().filter(Objects::nonNull).map(String::valueOf).forEach(result::add);
        return result;
    }

    @Override
    public List<SampleStorageAssignment> findAssignmentsInHierarchy(String locationType, Integer locationId) {
        return entityManager
                .createQuery("select ssa from SampleStorageAssignment ssa where "
                        + hierarchyPredicate(locationType, locationId), SampleStorageAssignment.class)
                .setParameter("locationId", locationId).getResultList();
    }

    private String hierarchyPredicate(String locationType, Integer locationId) {
        int root = LOCATION_TYPES.indexOf(locationType);
        if (root < 0 || locationId == null) {
            throw new IllegalArgumentException("Invalid storage location");
        }
        List<String> conditions = new ArrayList<>();
        for (int level = root; level < LOCATION_TYPES.size(); level++) {
            StringBuilder path = new StringBuilder("loc");
            for (int parent = level; parent > root; parent--) {
                path.append('.').append(PARENT_PATHS.get(parent));
            }
            conditions.add("(ssa.locationType = '" + LOCATION_TYPES.get(level) + "' and ssa.locationId in "
                    + "(select loc.id from " + ENTITY_NAMES.get(level) + " loc where " + path + ".id = :locationId))");
        }
        return String.join(" or ", conditions);
    }
}
