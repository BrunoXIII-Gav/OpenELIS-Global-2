package org.openelisglobal.test.dao;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.test.valueholder.Test;

public interface TestCreationOriginDAO {
    List<Test> unclassifiedTests();

    int classify(Collection<String> ids, boolean userCreated, String actorId);
}
