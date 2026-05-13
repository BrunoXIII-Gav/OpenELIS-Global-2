package org.openelisglobal.sampleadditionalfield.service;

import java.util.List;
import org.openelisglobal.sampleadditionalfield.bean.SampleFixedFieldConfigPayload;

public interface SampleAdditionalFieldService {
    List<SampleFixedFieldConfigPayload> getFixedFieldConfigs();
    void upsertFixedFieldConfigs(List<SampleFixedFieldConfigPayload> payloads, String currentUserId);
}
