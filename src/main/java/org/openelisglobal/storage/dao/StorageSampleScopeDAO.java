package org.openelisglobal.storage.dao;

import java.util.List;
import java.util.Set;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.storage.valueholder.SampleStorageAssignment;

public interface StorageSampleScopeDAO extends BaseDAO<SampleItem, String> {
    Set<String> findSampleItemIdsByLabUnits(Set<String> labUnitIds);

    /** Specimens with no own analyses may inherit their parent's scope. */
    List<Object[]> findUnanalysedSampleItemParents();

    List<SampleStorageAssignment> findAssignmentsInHierarchy(String locationType, Integer locationId);

    Set<String> findAssignedSampleItemIdsInHierarchy(String locationType, Integer locationId);
}
