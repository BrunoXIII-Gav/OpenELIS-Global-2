package org.openelisglobal.test.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

/**
 * Installation identities read from the original bootstrap and migration
 * resources. SQL is parsed as data only; this class never executes it. Names,
 * numeric IDs and timestamps are deliberately not used to recognize a system
 * test.
 */
@Component
public class SystemTestCatalog {
    private static final Pattern INSERT_TEST = Pattern
            .compile("(?is)\\bINSERT\\s+INTO\\s+(?:clinlims\\.)?test\\s*\\(([^)]+)\\)\\s*VALUES\\s*(.*)");
    private final Set<String> guids;

    public SystemTestCatalog() {
        this(new ClassPathResource("configuration/installation/OpenELIS-Global.sql"), migrationResources());
    }

    SystemTestCatalog(Resource bootstrap, Resource[] migrations) {
        try {
            Set<String> identities = new HashSet<>();
            readBootstrap(bootstrap, identities);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            for (Resource migration : migrations) {
                try (var stream = migration.getInputStream()) {
                    var document = factory.newDocumentBuilder().parse(stream);
                    var inserts = document.getElementsByTagNameNS("*", "insert");
                    for (int i = 0; i < inserts.getLength(); i++) {
                        var insert = (Element) inserts.item(i);
                        if (!"test".equalsIgnoreCase(insert.getAttribute("tableName")))
                            continue;
                        var columns = insert.getElementsByTagNameNS("*", "column");
                        for (int j = 0; j < columns.getLength(); j++) {
                            var column = (Element) columns.item(j);
                            if ("guid".equalsIgnoreCase(column.getAttribute("name")))
                                addGuid(column.getAttribute("value"), identities);
                        }
                    }
                    var sqls = document.getElementsByTagNameNS("*", "sql");
                    for (int i = 0; i < sqls.getLength(); i++)
                        readSql(sqls.item(i).getTextContent(), identities);
                }
            }
            guids = Collections.unmodifiableSet(identities);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read installation test identities", e);
        }
    }

    public Set<String> guids() {
        return guids;
    }

    public boolean contains(String guid) {
        return guid != null && guids.contains(guid.trim().toLowerCase(Locale.ROOT));
    }

    private static Resource[] migrationResources() {
        try {
            return new PathMatchingResourcePatternResolver().getResources("classpath*:liquibase/**/*.xml");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to locate installation migrations", e);
        }
    }

    private static void readBootstrap(Resource resource, Set<String> identities) throws Exception {
        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            int guidIndex = -1;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("COPY clinlims.test (")) {
                    guidIndex = Arrays.asList(line.substring(line.indexOf('(') + 1, line.indexOf(')')).split(",\\s*"))
                            .indexOf("guid");
                } else if (line.equals("\\.")) {
                    guidIndex = -1;
                } else if (guidIndex >= 0) {
                    String[] values = line.split("\\t", -1);
                    if (values.length > guidIndex)
                        addGuid(values[guidIndex], identities);
                }
            }
        }
    }

    private static void readSql(String sql, Set<String> identities) {
        for (String statement : splitOutsideExpressions(sql, ';')) {
            var insert = INSERT_TEST.matcher(statement);
            if (!insert.find())
                continue;
            List<String> columns = splitOutsideExpressions(insert.group(1), ',').stream()
                    .map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
            int guidIndex = columns.indexOf("guid");
            if (guidIndex < 0)
                continue;
            for (String row : splitOutsideExpressions(insert.group(2), ',')) {
                row = row.trim();
                if (!row.startsWith("(") || !row.endsWith(")"))
                    continue;
                List<String> values = splitOutsideExpressions(row.substring(1, row.length() - 1), ',');
                if (values.size() == columns.size()) {
                    String guid = values.get(guidIndex).trim();
                    if (guid.startsWith("'") && guid.endsWith("'"))
                        addGuid(guid.substring(1, guid.length() - 1), identities);
                }
            }
        }
    }

    private static List<String> splitOutsideExpressions(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean quoted = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!quoted && c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                while (i + 1 < text.length() && text.charAt(i + 1) != '\n')
                    i++;
                continue;
            }
            if (c == '\'') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    part.append("''");
                    i++;
                    continue;
                }
                quoted = !quoted;
            }
            if (!quoted) {
                if (c == '(')
                    depth++;
                else if (c == ')')
                    depth--;
                if (c == delimiter && depth == 0) {
                    parts.add(part.toString());
                    part.setLength(0);
                    continue;
                }
            }
            part.append(c);
        }
        parts.add(part.toString());
        return parts;
    }

    private static void addGuid(String value, Set<String> identities) {
        try {
            identities.add(UUID.fromString(value.trim()).toString());
        } catch (IllegalArgumentException ignored) {
            // A generated or absent GUID cannot identify an existing installation row.
        }
    }
}
