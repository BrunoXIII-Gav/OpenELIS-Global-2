package org.openelisglobal.sample.service;

public interface OrderAccessionService {
    String preview(String programCode);

    String confirm(String proposed, boolean generated);
}
