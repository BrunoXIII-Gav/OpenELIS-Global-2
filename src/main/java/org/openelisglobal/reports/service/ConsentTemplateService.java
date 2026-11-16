package org.openelisglobal.reports.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;

public interface ConsentTemplateService {
    String DEFAULT_TEMPLATE_ID = "RPT-CONSENT-TEMPLATE";

    ConsentTemplateConfigForm getConfig();

    ConsentTemplateConfigForm getConfig(String templateId);

    List<ConsentTemplateConfigForm> getTemplates(boolean includeInactive);

    ConsentTemplateConfigForm saveTemplate(String sysUserId, String templateId, ConsentTemplateConfigForm form);

    ConsentTemplateConfigForm parseTemplate(String filename, String content);

    Map<String, Object> getOptions();

    ConsentTemplateConfigForm saveConfig(String sysUserId, ConsentTemplateConfigForm form);

    byte[] generateConsentPdf(ConsentPreviewForm form);

    byte[] generateConsentPdf(ConsentPreviewForm form, String userId);
}
