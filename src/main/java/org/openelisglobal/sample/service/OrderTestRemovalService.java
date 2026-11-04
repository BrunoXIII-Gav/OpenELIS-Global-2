package org.openelisglobal.sample.service;

import java.util.List;
import org.openelisglobal.analysis.valueholder.Analysis;

public interface OrderTestRemovalService {
    boolean hasResults(Analysis analysis);

    void validateRemoval(List<Analysis> analyses);
}
