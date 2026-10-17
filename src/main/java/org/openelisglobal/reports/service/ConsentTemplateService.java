package org.openelisglobal.reports.service;

import java.util.Map;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;

public interface ConsentTemplateService {

    ConsentTemplateConfigForm getConfig();

    ConsentTemplateConfigForm parseTemplate(String filename, String content);

    Map<String, Object> getOptions();

    ConsentTemplateConfigForm saveConfig(String sysUserId, ConsentTemplateConfigForm form);

    byte[] generateConsentPdf(ConsentPreviewForm form);
}
