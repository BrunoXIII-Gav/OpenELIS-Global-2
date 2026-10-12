package org.openelisglobal.testconfiguration.packageio.service;

import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refresh only after publication, so failed imports never enter shared display
 * caches.
 */
@Service
public class TestConfigurationCatalogRefreshService {
    public record Imported() {
    }

    @Autowired
    private TestService testService;
    @Autowired
    private TypeOfSampleService sampleTypeService;
    @Autowired
    private DisplayListService displayListService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void imported(Imported event) {
        sampleTypeService.clearCache();
        testService.refreshTestNames();
        for (ListType type : ListType.values()) {
            String name = type.name();
            if (name.startsWith("SAMPLE_TYPE") || name.startsWith("TEST_SECTION") || name.startsWith("PANELS")
                    || name.startsWith("METHOD") || name.startsWith("UNIT_OF_MEASURE") || name.equals("ALL_TESTS")
                    || name.equals("ORDERABLE_TESTS") || name.equals("DICTIONARY_TEST_RESULTS"))
                displayListService.refreshList(type);
        }
    }
}
