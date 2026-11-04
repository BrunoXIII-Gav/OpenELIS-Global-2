package org.openelisglobal.reports.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@Service
public class ConsentJasperService {
    // A single compiled template is retained; replacing a template cannot grow the
    // cache.
    private String compiledContent;
    private JasperReport compiledReport;

    private static final int MAX_TEMPLATE_BYTES = 2 * 1024 * 1024;
    private static final java.util.Set<String> SUPPORTED_TYPES = java.util.Set.of("java.lang.String",
            "java.lang.Boolean", "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.math.BigDecimal",
            "java.util.List", "java.util.Collection");

    public ConsentTemplateConfigForm parse(String filename, String content) {
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".jrxml") || content == null
                || content.isBlank() || content.getBytes(StandardCharsets.UTF_8).length > MAX_TEMPLATE_BYTES) {
            throw new IllegalArgumentException("consent.template.error.file");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            Element root = factory.newDocumentBuilder().parse(stream(content)).getDocumentElement();
            if (!"jasperReport".equals(root.getLocalName())) {
                throw new IllegalArgumentException("consent.template.error.invalid");
            }
            List<Map<String, String>> definitions = new ArrayList<>();
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element)) {
                    continue;
                }
                Element element = (Element) node;
                String kind = element.getLocalName();
                if ("queryString".equals(kind) && !element.getTextContent().isBlank()) {
                    throw new IllegalArgumentException("consent.template.error.query");
                }
                if (!("parameter".equals(kind) || "field".equals(kind))
                        || "false".equals(element.getAttribute("isForPrompting"))) {
                    continue;
                }
                String name = element.getAttribute("name");
                String type = element.getAttribute("class");
                if (type.isBlank()) {
                    type = "java.lang.String";
                }
                if (name.isBlank() || name.startsWith("REPORT_") || !SUPPORTED_TYPES.contains(type)) {
                    throw new IllegalArgumentException("consent.template.error.type");
                }
                String id = "field".equals(kind) ? "field:" + name : name;
                if (definitions.stream().anyMatch(d -> id.equals(d.get("id")))) {
                    throw new IllegalArgumentException("consent.template.error.invalid");
                }
                definitions.add(Map.of("id", id, "name", name, "className", type, "kind", kind));
            }
            if (definitions.isEmpty()) {
                throw new IllegalArgumentException("consent.template.error.noFields");
            }
            // Validate the complete report, including expressions and layout, before it can
            // be saved.
            compile(content);
            ConsentTemplateConfigForm result = new ConsentTemplateConfigForm();
            result.setTemplateContent(content);
            result.setTemplateFilename(filename.replaceAll("[^\\p{L}\\p{N}._ -]", "_"));
            result.setParameters(definitions);
            result.setFields(new LinkedHashMap<>());
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("consent.template.error.invalid", e);
        }
    }

    public byte[] render(ConsentTemplateConfigForm config, Map<String, String> values) {
        try {
            Map<String, Object> parameters = new LinkedHashMap<>();
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map<String, String> definition : config.getParameters()) {
                String value = values.getOrDefault(definition.get("id"), "");
                Object converted = convert(value, definition.get("className"));
                ("field".equals(definition.get("kind")) ? row : parameters).put(definition.get("name"), converted);
            }
            return JasperRunManager.runReportToPdf(compile(config.getTemplateContent()), parameters,
                    row.isEmpty() ? new JREmptyDataSource(1)
                            : new JRMapCollectionDataSource(java.util.Collections.singletonList(row)));
        } catch (Exception e) {
            throw new IllegalArgumentException("consent.template.error.render", e);
        }
    }

    private Object convert(String value, String type) {
        if ("java.lang.String".equals(type)) {
            return value;
        }
        if ("java.util.List".equals(type) || "java.util.Collection".equals(type)) {
            return value.isBlank() ? java.util.Collections.emptyList() : List.of(value.split("\\n"));
        }
        if (value.isBlank()) {
            return null;
        }
        switch (type) {
        case "java.lang.Integer":
            return Integer.valueOf(value);
        case "java.lang.Long":
            return Long.valueOf(value);
        case "java.lang.Double":
            return Double.valueOf(value);
        case "java.math.BigDecimal":
            return new java.math.BigDecimal(value);
        case "java.lang.Boolean":
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("consent.template.error.type");
            }
            return Boolean.valueOf(value);
        default:
            throw new IllegalArgumentException("consent.template.error.type");
        }
    }

    private synchronized JasperReport compile(String content) throws net.sf.jasperreports.engine.JRException {
        if (!content.equals(compiledContent)) {
            JasperReport report = JasperCompileManager.compileReport(JRXmlLoader.load(stream(content)));
            compiledContent = content;
            compiledReport = report;
        }
        return compiledReport;
    }

    private ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
