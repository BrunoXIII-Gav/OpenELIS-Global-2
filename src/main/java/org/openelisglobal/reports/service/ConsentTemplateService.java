package org.openelisglobal.reports.service;

import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;

public interface ConsentTemplateService {

    ConsentTemplateConfigForm getConfig();

    ConsentTemplateConfigForm saveConfig(String sysUserId, ConsentTemplateConfigForm form);

    byte[] generateConsentPdf(ConsentPreviewForm form);
}
