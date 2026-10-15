package org.openelisglobal.result.service;

import java.util.List;
import java.util.function.Consumer;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.registration.interfaces.IResultUpdate;
import org.openelisglobal.result.action.util.ResultsUpdateDataSet;

public interface LogbookResultsPersistService {

    List<Analysis> prepareAndPersistDataSet(ResultsUpdateDataSet actionDataSet, List<IResultUpdate> updaters,
            String sysUserId, Consumer<ResultsUpdateDataSet> preparation);

    List<Analysis> persistDataSet(ResultsUpdateDataSet actionDataSet, List<IResultUpdate> updaters, String sysUserId);
}
