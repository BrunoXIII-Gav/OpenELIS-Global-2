package org.openelisglobal.testconfiguration.packageio.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.openelisglobal.testconfiguration.packageio.dao.*;
import org.openelisglobal.testconfiguration.packageio.dao.TestConfigurationPackageDAO.*;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage.*;
import org.openelisglobal.testconfiguration.packageio.valueholder.TestConfigurationRevision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TestConfigurationPackageServiceImpl implements TestConfigurationPackageService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DEFERRED = Set.of("Test.defaultTestResult", "TestReflex.siblingReflexId");
    private static final Set<String> IDENTIFIERS = Set.of("description", "localCode");
    private static final Set<String> MANUAL_RELATIONS = Set.of("TestParentChildDependency", "TestReflex");
    @Autowired
    private TestConfigurationPackageDAO dao;
    @Autowired
    private org.openelisglobal.common.service.UserFieldOptionResolver userFieldOptionResolver;
    @Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    @Override
    public List<Map<String, String>> tests() {
        return dao.userCreatedTests().stream().map(t -> Map.of("id", t.id(), "name", label(t.values()), "code",
                Objects.toString(t.values().get("localCode"), ""))).toList();
    }

    @Override
    public TestConfigurationPackage exportTests(List<String> ids) {
        if (ids == null || ids.size() != 1)
            throw bad("selection");
        Set<String> included = new LinkedHashSet<>(ids);
        LinkedHashMap<String, Snapshot> snapshots = new LinkedHashMap<>();
        for (String id : included)
            collect(dao.snapshot("Test", id), snapshots);
        Map<String, String> children = Map.of("TestResult", "test.id", "ResultLimit", "testId",
                "TestAdditionalFieldDefinition", "testId", "TypeOfSampleTest", "testId", "PanelItem", "test.id",
                "TestAnalyte", "test.id");
        for (var child : children.entrySet()) {
            for (Snapshot row : dao.related(child.getKey(), child.getValue(), included))
                collect(row, snapshots);
        }
        List<String> fields = snapshots.values().stream().filter(s -> "TestAdditionalFieldDefinition".equals(s.kind()))
                .filter(s -> !"USER".equals(s.values().get("fieldType"))).map(Snapshot::id).toList();
        for (Snapshot row : dao.related("TestAdditionalFieldOption", "fieldDefinitionId", fields))
            collect(row, snapshots);
        TestConfigurationPackage result = new TestConfigurationPackage();
        for (String id : included)
            result.roots.add(snapshots.get(identity("Test", id)).stableKey());
        for (Snapshot snapshot : snapshots.values()) {
            Node node = new Node();
            node.key = snapshot.stableKey();
            node.kind = snapshot.kind();
            node.catalog = !TestPackageRegistry.OWNED.contains(node.kind);
            snapshot.values().forEach((p, v) -> node.values.put(p, JSON.valueToTree(v)));
            snapshot.links().forEach(
                    (p, ref) -> node.references.put(p, snapshots.get(identity(ref.kind(), ref.id())).stableKey()));
            if ("USER".equals(snapshot.values().get("fieldType")))
                node.values.put("defaultValue", NullNode.instance);
            sanitizeMetadata(node);
            result.nodes.add(node);
        }
        separateTestNames(result);
        result.nodes.sort(Comparator.comparing(n -> n.key));
        Collections.sort(result.roots);
        validate(result);
        return result;
    }

    @Override
    public TestConfigurationPackage prepareImport(TestConfigurationPackage bundle, String testKey) {
        Map<String, Node> graph = validate(bundle);
        if (testKey == null || !bundle.roots.contains(testKey))
            throw bad("selection");
        // Legacy files may contain connected tests. Extract only the selected test's
        // configuration and catalogs; dependencies and reflexes are configured
        // manually.
        Map<String, Node> independent = graph.values().stream().filter(n -> !MANUAL_RELATIONS.contains(n.kind))
                .collect(Collectors.toMap(n -> n.key, n -> n, (a, b) -> a, LinkedHashMap::new));
        Set<String> selected = needed(independent, List.of(testKey), Map.of());
        TestConfigurationPackage result = new TestConfigurationPackage();
        result.roots.add(testKey);
        result.nodes = bundle.nodes.stream().filter(n -> selected.contains(n.key)).toList();
        validate(result);
        validateIndependent(result);
        return result;
    }

    private void validateIndependent(TestConfigurationPackage bundle) {
        if (bundle.roots.size() != 1 || bundle.nodes.stream().anyMatch(n -> MANUAL_RELATIONS.contains(n.kind)))
            throw bad("selection");
    }

    private void collect(Snapshot snapshot, Map<String, Snapshot> graph) {
        if (graph.putIfAbsent(identity(snapshot.kind(), snapshot.id()), snapshot) != null)
            return;
        if (graph.size() > 5000)
            throw bad("size");
        for (Link ref : snapshot.links().values())
            if (!graph.containsKey(identity(ref.kind(), ref.id())))
                collect(dao.snapshot(ref.kind(), ref.id()), graph);
    }

    private static String identity(String kind, String id) {
        return kind + ":" + id;
    }

    private void sanitizeMetadata(Node node) {
        if ("USER".equals(text(node, "fieldType"))) {
            JsonNode metadata = metadata(node.values.get("metadataJson"));
            if (metadata.isObject()) {
                ((ObjectNode) metadata).remove(List.of("professionalProfileIds", "professionalProfileCodes",
                        "userProfileIds", "userProfileCodes"));
                node.values.put("metadataJson", TextNode.valueOf(metadata.toString()));
            }
        }
    }

    @Override
    public Preview preview(ImportRequest request) {
        validateRequest(request);
        Map<String, Node> graph = validate(request.bundle);
        Preview preview = new Preview();
        preview.token = fingerprint(request);
        preview.alreadyImported = previousImport(request).isPresent();
        Map<String, List<Snapshot>> catalogs = new HashMap<>();
        for (Node node : graph.values()) {
            if (!node.catalog)
                continue;
            List<Snapshot> candidates = catalogs.computeIfAbsent(node.kind, dao::list);
            String signature = catalogSignature(node.values);
            List<Snapshot> matches = signature.equals("{}") ? List.of()
                    : candidates.stream().filter(
                            c -> catalogSignature(c.values()).equals(signature) && matchingContext(node, c, graph))
                            .toList();
            String suggested = matches.size() == 1 && !"Scriptlet".equals(node.kind) ? matches.get(0).id() : "";
            boolean conflicts = candidates.stream().anyMatch(c -> catalogNameConflict(node, c));
            boolean canCreate = !TestPackageRegistry.NO_CREATE.contains(node.kind) && !conflicts && matches.isEmpty();
            preview.catalogs.add(new CatalogChoice(node.key, node.kind, label(node.values), suggested, canCreate,
                    candidates.stream().map(c -> new Choice(c.id(), label(c.values()))).toList()));
            String chosen = request.catalogs.getOrDefault(node.key, suggested);
            if (chosen == null)
                throw bad("reference");
            if (!preview.alreadyImported && (chosen.isBlank() || ("CREATE".equals(chosen) && !canCreate)
                    || (!"CREATE".equals(chosen) && candidates.stream().noneMatch(c -> c.id().equals(chosen))))) {
                preview.issues.add(new Issue("catalog", node.key, node.kind));
            }
        }
        Set<String> needed = needed(graph, request.bundle.roots, preview.catalogs.stream().collect(
                Collectors.toMap(CatalogChoice::key, c -> request.catalogs.getOrDefault(c.key(), c.suggestedId()))));
        preview.catalogs.removeIf(c -> !needed.contains(c.key()));
        preview.issues.removeIf(i -> "catalog".equals(i.code()) && !needed.contains(i.key()));
        Set<String> newCatalogs = new HashSet<>();
        for (CatalogChoice catalog : preview.catalogs) {
            if ("CREATE".equals(request.catalogs.get(catalog.key()))) {
                Node node = graph.get(catalog.key());
                if (!newCatalogs.add(node.kind + catalogSignature(node.values)))
                    preview.issues.add(new Issue("duplicate", node.key, node.kind));
            }
        }
        if (!preview.alreadyImported)
            verifyInsertionOrder(graph, needed,
                    preview.catalogs.stream()
                            .filter(c -> !"CREATE".equals(request.catalogs.getOrDefault(c.key(), c.suggestedId())))
                            .map(CatalogChoice::key).collect(Collectors.toSet()));
        List<Snapshot> existingTests = dao.list("Test");
        Set<String> descriptions = new HashSet<>(), codes = new HashSet<>();
        for (Node node : graph.values()) {
            if (!"Test".equals(node.kind))
                continue;
            Map<String, JsonNode> effective = effective(node, request);
            String description = asText(effective.get("description"));
            String code = asText(effective.get("localCode"));
            if (description.isBlank() || description.length() > 60 || code.length() > 10)
                preview.issues.add(new Issue("identity", node.key, "description"));
            boolean duplicateName = !descriptions.add(normalize(description)) || existingTests.stream().anyMatch(
                    t -> normalize(Objects.toString(t.values().get("description"), "")).equals(normalize(description)));
            boolean duplicateCode = !code.isBlank() && (!codes.add(normalize(code)) || existingTests.stream().anyMatch(
                    t -> normalize(Objects.toString(t.values().get("localCode"), "")).equals(normalize(code))));
            if (!preview.alreadyImported && duplicateName)
                preview.issues.add(new Issue("name", node.key, "description"));
            if (!preview.alreadyImported && duplicateCode)
                preview.issues.add(new Issue("code", node.key, "localCode"));
            preview.tests.add(Map.of("key", node.key, "name", description, "code", code));
        }
        dao.latestRevision(family(request.bundle)).ifPresent(old -> {
            try {
                Map<String, Node> previous = index(JSON.readValue(old.packageJson, TestConfigurationPackage.class));
                for (Node node : graph.values()) {
                    Node before = previous.get(node.key);
                    Node after = effectiveNode(node, request);
                    if (before == null || !JSON.valueToTree(before).equals(JSON.valueToTree(after)))
                        preview.differences.add(Map.of("key", node.key, "kind", node.kind, "before",
                                before == null ? NullNode.instance : JSON.valueToTree(before), "after",
                                JSON.valueToTree(after)));
                }
                for (Node before : previous.values())
                    if (!graph.containsKey(before.key))
                        preview.differences.add(Map.of("key", before.key, "kind", before.kind, "before",
                                JSON.valueToTree(before), "after", NullNode.instance));
            } catch (Exception e) {
                throw new IllegalStateException("testPackage.issue.history", e);
            }
        });
        preview.ready = preview.issues.isEmpty();
        return preview;
    }

    private void validateRequest(ImportRequest request) {
        if (request == null || request.catalogs == null || request.overrides == null)
            throw bad("format");
        Map<String, Node> graph = validate(request.bundle);
        validateIndependent(request.bundle);
        for (var override : request.overrides.entrySet()) {
            Node node = graph.get(override.getKey());
            if (node == null || !"Test".equals(node.kind) || override.getValue() == null
                    || !IDENTIFIERS.containsAll(override.getValue().keySet())
                    || override.getValue().values().stream().anyMatch(v -> v == null || !v.isTextual()))
                throw bad("property");
        }
        for (String key : request.catalogs.keySet())
            if (!graph.containsKey(key) || !graph.get(key).catalog)
                throw bad("reference");
    }

    @Override
    @Transactional
    public ImportResult importPackage(ImportRequest request, String actorId) {
        if (actorId == null || actorId.isBlank())
            throw bad("actor");
        validateRequest(request);
        if (request.previewToken == null || !request.previewToken.equals(fingerprint(request)))
            throw bad("preview");
        dao.lockImports();
        Optional<TestConfigurationRevision> previous = previousImport(request);
        if (previous.isPresent())
            return result(previous.get(), true);
        Preview preview = preview(request);
        if (!preview.ready)
            throw bad("conflicts");
        Map<String, Node> graph = validate(request.bundle);
        Map<String, String> mapped = new LinkedHashMap<>();
        for (CatalogChoice catalog : preview.catalogs) {
            String chosen = request.catalogs.getOrDefault(catalog.key(), catalog.suggestedId());
            if (!"CREATE".equals(chosen))
                mapped.put(catalog.key(), chosen);
        }
        Set<String> needed = needed(graph, request.bundle.roots, preview.catalogs.stream().collect(
                Collectors.toMap(CatalogChoice::key, c -> request.catalogs.getOrDefault(c.key(), c.suggestedId()))));
        List<Node> pending = graph.values().stream().filter(n -> needed.contains(n.key))
                .collect(Collectors.toCollection(ArrayList::new));
        pending.removeIf(n -> mapped.containsKey(n.key));
        Set<String> created = pending.stream().map(n -> n.key).collect(Collectors.toSet());
        // Dependencies without an owning root would otherwise silently create stray
        // rows.
        while (!pending.isEmpty()) {
            boolean progress = false;
            Iterator<Node> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Node node = iterator.next();
                if (node.references.entrySet().stream()
                        .anyMatch(r -> !deferred(node, r.getKey()) && !mapped.containsKey(r.getValue())))
                    continue;
                Map<String, Object> values = convert(node, effective(node, request));
                Map<String, String> references = new LinkedHashMap<>();
                node.references.forEach((p, key) -> {
                    if (!deferred(node, p))
                        references.put(p, mapped.get(key));
                });
                mapped.put(node.key, dao.insert(node.kind, values, references, actorId));
                iterator.remove();
                progress = true;
            }
            if (!progress)
                throw bad("cycle");
        }
        for (Node node : graph.values()) {
            if (node.catalog || !needed.contains(node.key))
                continue;
            node.references.forEach((p, key) -> {
                if (deferred(node, p))
                    dao.link(node.kind, mapped.get(node.key), p, mapped.get(key));
            });
        }
        dao.flush();
        TestConfigurationRevision revision = new TestConfigurationRevision();
        revision.id = UUID.randomUUID().toString();
        revision.fingerprint = request.copy ? hash(fingerprint(request) + revision.id) : importFingerprint(request);
        revision.familyKey = request.copy ? hash(revision.id) : family(request.bundle);
        revision.packageJson = serialize(appliedPackage(request));
        revision.mappingJson = serialize(Map.of("nodes", mapped, "tests",
                request.bundle.nodes.stream().filter(n -> "Test".equals(n.kind))
                        .collect(Collectors.toMap(n -> n.key, n -> mapped.get(n.key))),
                "overrides", request.overrides, "catalogs", request.catalogs));
        revision.actorId = actorId;
        revision.createdAt = Instant.now();
        dao.saveRevision(revision);
        for (Node node : graph.values())
            if (created.contains(node.key))
                dao.saveIdentity(node.kind, mapped.get(node.key),
                        request.copy ? UUID.randomUUID().toString() : node.key, revision.id);
        dao.flush();
        events.publishEvent(new TestConfigurationCatalogRefreshService.Imported());
        return result(revision, false);
    }

    private boolean deferred(Node node, String property) {
        return DEFERRED.contains(node.kind + "." + property);
    }

    private ImportResult result(TestConfigurationRevision revision, boolean already) {
        try {
            Map<String, String> ids = JSON.convertValue(JSON.readTree(revision.mappingJson).get("tests"), Map.class);
            return new ImportResult(revision.id, already, ids);
        } catch (Exception e) {
            throw new IllegalStateException("testPackage.issue.history", e);
        }
    }

    @Override
    public List<Map<String, Object>> history() {
        return dao.revisions().stream().map(r -> Map.<String, Object>of("id", r.id, "actor", r.actorId, "createdAt",
                r.createdAt.toString(), "family", r.familyKey, "mapping", readJson(r.mappingJson))).toList();
    }

    private Map<String, JsonNode> effective(Node node, ImportRequest request) {
        Map<String, JsonNode> values = new LinkedHashMap<>(node.values);
        values.putAll(request.overrides.getOrDefault(node.key, Map.of()));
        if ("Test".equals(node.kind)) {
            values.put("description", TextNode.valueOf(asText(values.get("description")).trim()));
            if (values.containsKey("localCode")) {
                String code = asText(values.get("localCode")).trim();
                values.put("localCode", code.isBlank() ? NullNode.instance : TextNode.valueOf(code));
            }
        }
        if ("Test".equals(node.kind) && request.overrides.getOrDefault(node.key, Map.of()).containsKey("description")) {
            if (values.containsKey("storedName"))
                values.put("storedName", values.get("description"));
        }
        if ("Localization".equals(node.kind)) {
            for (Node test : request.bundle.nodes) {
                JsonNode name = request.overrides.getOrDefault(test.key, Map.of()).get("description");
                if (name != null && "Test".equals(test.kind)
                        && (node.key.equals(test.references.get("localizedTestName")) || (node.key
                                .equals(test.references.get("localizedReportingName"))
                                && normalize(text(node, "english")).equals(normalize(text(test, "description")))))) {
                    // Export gives each test its own name records; no other definition is renamed.
                    values.put("english", name);
                    values.put("french", name);
                }
            }
        }
        return values;
    }

    private Node effectiveNode(Node node, ImportRequest request) {
        Node result = new Node();
        result.key = node.key;
        result.kind = node.kind;
        result.catalog = node.catalog;
        result.values = effective(node, request);
        result.references = node.references;
        return result;
    }

    private Map<String, Object> convert(Node node, Map<String, JsonNode> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Attribute> attrs = dao.attributes(node.kind);
        values.forEach((property, value) -> {
            try {
                result.put(property, value == null || value.isNull() ? null
                        : JSON.convertValue(value, attrs.get(property).javaType()));
            } catch (IllegalArgumentException e) {
                throw bad("type");
            }
        });
        return result;
    }

    Map<String, Node> validate(TestConfigurationPackage bundle) {
        if (bundle == null || !"openelis-test-configuration".equals(bundle.format) || bundle.version != 1
                || bundle.nodes == null || bundle.nodes.isEmpty() || bundle.nodes.size() > 5000 || bundle.roots == null
                || bundle.roots.isEmpty() || bundle.roots.size() > 200
                || serialize(bundle).getBytes(StandardCharsets.UTF_8).length > 5_000_000)
            throw bad("format");
        Map<String, Node> graph = index(bundle);
        for (String root : bundle.roots)
            if (!graph.containsKey(root) || !"Test".equals(graph.get(root).kind))
                throw bad("reference");
        if (new HashSet<>(bundle.roots).size() != bundle.roots.size() || !new HashSet<>(bundle.roots).equals(
                graph.values().stream().filter(n -> "Test".equals(n.kind)).map(n -> n.key).collect(Collectors.toSet())))
            throw bad("ownership");
        Set<String> fields = new HashSet<>(), options = new HashSet<>(), joins = new HashSet<>();
        Map<String, String> parentByChild = new HashMap<>();
        for (Node node : graph.values()) {
            TestPackageRegistry.type(node.kind);
            if (node.catalog == TestPackageRegistry.OWNED.contains(node.kind) || node.values == null
                    || node.references == null)
                throw bad("kind");
            Map<String, Attribute> attrs = dao.attributes(node.kind);
            for (var attribute : attrs.entrySet())
                if (!attribute.getValue().nullable() && !node.references.containsKey(attribute.getKey())
                        && (node.values.get(attribute.getKey()) == null
                                || node.values.get(attribute.getKey()).isNull()))
                    throw bad("property");
            Map<String, Object> raw = new HashMap<>();
            node.values.forEach((p, v) -> raw.put(p, v == null || v.isNull() ? null : v.asText()));
            for (var value : node.values.entrySet()) {
                Attribute attr = attrs.get(value.getKey());
                if (attr == null || attr.association()
                        || (TestPackageRegistry.idReference(node.kind, value.getKey(), raw) != null
                                && value.getValue() != null && !value.getValue().isNull()))
                    throw bad("property");
                if (value.getValue() != null
                        && (value.getValue().isContainerNode() || value.getValue().asText().length() > 100_000))
                    throw bad("type");
            }
            convert(node, node.values);
            for (var ref : node.references.entrySet()) {
                Attribute attr = attrs.get(ref.getKey());
                String target = attr == null ? null
                        : attr.targetKind() != null ? attr.targetKind()
                                : TestPackageRegistry.idReference(node.kind, ref.getKey(), raw);
                Node linked = graph.get(ref.getValue());
                if (target == null || linked == null || !target.equals(linked.kind)
                        || node.values.containsKey(ref.getKey()))
                    throw bad("reference");
            }
            Set<String> required = switch (node.kind) {
            case "Test" -> Set.of("localizedTestName");
            case "TestResult" -> Set.of("test");
            case "TestAnalyte" -> Set.of("test", "analyte");
            case "ResultLimit", "TestAdditionalFieldDefinition" -> Set.of("testId");
            case "TestAdditionalFieldOption" -> Set.of("fieldDefinitionId");
            case "TypeOfSampleTest" -> Set.of("testId", "typeOfSampleId");
            case "PanelItem" -> Set.of("test", "panel");
            case "TestParentChildDependency" -> Set.of("parentTest", "childTest");
            case "TestReflex" -> Set.of("test");
            default -> Set.of();
            };
            if (!node.references.keySet().containsAll(required))
                throw bad("ownership");
            for (String property : List.of("metadataJson", "resultDisplayConfigJson")) {
                if (node.values.containsKey(property))
                    validateMetadata(metadata(node.values.get(property)));
            }
            if ("TestAdditionalFieldDefinition".equals(node.kind)) {
                String key = text(node, "fieldKey");
                if (!key.matches("[A-Za-z][A-Za-z0-9_-]{0,79}") || text(node, "displayName").isBlank()
                        || !fields.add(node.references.get("testId") + ":" + key))
                    throw bad("field");
                try {
                    org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition.FieldType
                            .valueOf(text(node, "fieldType"));
                } catch (Exception e) {
                    throw bad("field");
                }
                if (text(node, "displayName").length() > 150 || text(node, "defaultValue").length() > 500)
                    throw bad("field");
                for (String flag : List.of("required", "active"))
                    if (!node.values.containsKey(flag) || node.values.get(flag) == null
                            || !node.values.get(flag).isBoolean())
                        throw bad("field");
                if (!node.values.containsKey("sortOrder") || node.values.get("sortOrder") == null
                        || !node.values.get("sortOrder").isIntegralNumber())
                    throw bad("field");
                if ("USER".equals(text(node, "fieldType")) && !text(node, "defaultValue").isBlank())
                    throw bad("user");
                if ("USER".equals(text(node, "fieldType"))) {
                    try {
                        userFieldOptionResolver.validateConfiguration("USER", text(node, "metadataJson"), "results");
                    } catch (IllegalArgumentException e) {
                        throw bad("user");
                    }
                }
            }
            if ("TestAdditionalFieldOption".equals(node.kind)) {
                if (text(node, "optionKey").isBlank() || text(node, "optionKey").length() > 80
                        || text(node, "optionLabel").isBlank() || text(node, "optionLabel").length() > 150
                        || !options.add(node.references.get("fieldDefinitionId") + ":" + text(node, "optionKey")))
                    throw bad("field");
                Node definition = graph.get(node.references.get("fieldDefinitionId"));
                if (definition != null && "USER".equals(text(definition, "fieldType")))
                    throw bad("user");
            }
            if (Set.of("PanelItem", "TypeOfSampleTest").contains(node.kind)
                    && !joins.add(node.kind + serialize(new TreeMap<>(node.references))))
                throw bad("duplicate");
            if ("Test".equals(node.kind) && node.references.containsKey("defaultTestResult")) {
                Node result = graph.get(node.references.get("defaultTestResult"));
                if (!node.key.equals(result.references.get("test")))
                    throw bad("ownership");
            }
            if ("TestReflex".equals(node.kind))
                for (String property : List.of("testResult", "testAnalyte")) {
                    Node related = graph.get(node.references.get(property));
                    if (related != null && !Objects.equals(node.references.get("test"), related.references.get("test")))
                        throw bad("ownership");
                }
            if ("TestParentChildDependency".equals(node.kind) && !"false".equals(text(node, "active"))) {
                String parent = node.references.get("parentTest"), child = node.references.get("childTest");
                if (parent == null || child == null || parent.equals(child)
                        || parentByChild.putIfAbsent(child, parent) != null)
                    throw bad("dependency");
                boolean tubeSource = hasTubeMetadata(graph, parent, true);
                if (!tubeSource || !hasTubeMetadata(graph, child, false))
                    throw bad("dependency");
                if (!Set.of("SAMPLE_ITEM_REMAINING", "PARENT_TEST_FIELD").contains(text(node, "sampleUsageSource")))
                    throw bad("dependency");
            }
        }
        for (String child : parentByChild.keySet()) {
            Set<String> seen = new HashSet<>();
            String current = child;
            while (current != null) {
                if (!seen.add(current))
                    throw bad("cycle");
                current = parentByChild.get(current);
            }
        }
        if (needed(graph, bundle.roots, Map.of()).size() != graph.size())
            throw bad("ownership");
        return graph;
    }

    private boolean hasTubeMetadata(Map<String, Node> graph, String testKey, boolean source) {
        List<JsonNode> configurations = new ArrayList<>();
        configurations.add(metadata(graph.get(testKey).values.get("resultDisplayConfigJson")));
        graph.values().stream()
                .filter(n -> "TestAdditionalFieldDefinition".equals(n.kind)
                        && testKey.equals(n.references.get("testId")) && !"false".equals(text(n, "active")))
                .forEach(n -> configurations.add(metadata(n.values.get("metadataJson"))));
        return configurations.stream().anyMatch(n -> source ? n.path("tubeQuantitySource").asBoolean(false)
                : n.path("tubeUsage").path("childBlockEnabled").asBoolean(false));
    }

    private Set<String> needed(Map<String, Node> graph, List<String> roots, Map<String, String> bindings) {
        Set<String> result = new LinkedHashSet<>(roots);
        boolean changed;
        do {
            changed = false;
            for (Node node : graph.values()) {
                if (result.contains(node.key)) {
                    // Existing catalogs retain their own dependencies; their exported dependencies
                    // aren't recreated.
                    String binding = bindings.get(node.key);
                    if (!node.catalog || binding == null || binding.isBlank() || "CREATE".equals(binding))
                        changed |= result.addAll(node.references.values());
                } else if (!node.catalog && !"Localization".equals(node.kind)
                        && node.references.values().stream().anyMatch(k -> result.contains(k) && !graph.get(k).catalog
                                && !"Localization".equals(graph.get(k).kind)))
                    changed |= result.add(node.key);
            }
        } while (changed);
        return result;
    }

    private void verifyInsertionOrder(Map<String, Node> graph, Set<String> needed, Set<String> existing) {
        Set<String> completed = new HashSet<>(existing);
        boolean changed;
        do {
            changed = false;
            for (String key : needed)
                if (!completed.contains(key) && graph.get(key).references.entrySet().stream()
                        .allMatch(r -> deferred(graph.get(key), r.getKey()) || completed.contains(r.getValue())))
                    changed |= completed.add(key);
        } while (changed);
        if (!completed.containsAll(needed))
            throw bad("cycle");
    }

    private boolean matchingContext(Node node, Snapshot candidate, Map<String, Node> graph) {
        return matchingContext(node, candidate, graph, new HashSet<>());
    }

    private boolean matchingContext(Node node, Snapshot candidate, Map<String, Node> graph, Set<String> visited) {
        if (!visited.add(node.key + ":" + candidate.kind() + ":" + candidate.id()))
            return true;
        for (var reference : node.references.entrySet()) {
            Node target = graph.get(reference.getValue());
            if (!target.catalog)
                continue;
            Link link = candidate.links().get(reference.getKey());
            if (link == null || !link.kind().equals(target.kind))
                return false;
            Snapshot parent = dao.snapshot(link.kind(), link.id());
            if (!catalogSignature(parent.values()).equals(catalogSignature(target.values))
                    || !matchingContext(target, parent, graph, visited))
                return false;
        }
        return true;
    }

    private boolean catalogNameConflict(Node node, Snapshot candidate) {
        for (String property : List.of("testSectionName", "unitOfMeasureName", "methodName", "panelName", "analyteName",
                "categoryName", "localAbbreviation")) {
            String source = text(node, property);
            if (!source.isBlank()
                    && normalize(source).equals(normalize(Objects.toString(candidate.values().get(property), ""))))
                return true;
        }
        // Dictionary entries may intentionally repeat in different categories.
        return !"Dictionary".equals(node.kind) && !label(node.values).isBlank()
                && normalize(label(node.values)).equals(normalize(label(candidate.values())));
    }

    private void separateTestNames(TestConfigurationPackage bundle) {
        Map<String, Node> graph = index(bundle);
        List<Node> additions = new ArrayList<>();
        for (Node test : bundle.nodes)
            if ("Test".equals(test.kind)) {
                for (String property : List.of("localizedTestName", "localizedReportingName")) {
                    Node original = graph.get(test.references.get(property));
                    if (original == null)
                        continue;
                    Node name = new Node();
                    name.kind = "Localization";
                    name.key = UUID.nameUUIDFromBytes((test.key + ":" + property).getBytes(StandardCharsets.UTF_8))
                            .toString();
                    name.values = new LinkedHashMap<>(original.values);
                    name.references = new LinkedHashMap<>(original.references);
                    additions.add(name);
                    test.references.put(property, name.key);
                }
            }
        bundle.nodes.addAll(additions);
        Set<String> used = bundle.nodes.stream().flatMap(n -> n.references.values().stream())
                .collect(Collectors.toSet());
        bundle.nodes.removeIf(n -> "Localization".equals(n.kind) && !used.contains(n.key));
    }

    private Map<String, Node> index(TestConfigurationPackage bundle) {
        Map<String, Node> graph = new LinkedHashMap<>();
        for (Node node : bundle.nodes) {
            if (node == null || node.key == null || !node.key.matches("[A-Za-z0-9_-]{1,100}")
                    || graph.putIfAbsent(node.key, node) != null)
                throw bad("duplicate");
        }
        return graph;
    }

    private JsonNode metadata(JsonNode raw) {
        return raw == null || raw.isNull() || raw.asText().isBlank() ? JSON.createObjectNode() : readJson(raw.asText());
    }

    private JsonNode readJson(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw bad("metadata");
        }
    }

    private void validateMetadata(JsonNode node) {
        if (!node.isObject())
            throw bad("metadata");
        node.fields().forEachRemaining(f -> {
            if (f.getKey().matches("(id|ids|.*[_-]ids?|.*Id|.*Ids|.*ID|.*IDs)") && !f.getValue().isNull())
                throw bad("metadataReference");
            if (f.getValue().isObject())
                validateMetadata(f.getValue());
            if (f.getValue().isArray())
                f.getValue().forEach(v -> {
                    if (v.isObject())
                        validateMetadata(v);
                });
        });
    }

    private String text(Node node, String property) {
        return asText(node.values.get(property));
    }

    private String asText(JsonNode value) {
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private String label(Map<String, ?> values) {
        for (String p : List.of("description", "testSectionName", "dictEntry", "categoryName", "methodName",
                "panelName", "analyteName", "scriptletName", "unitOfMeasureName", "localAbbreviation",
                "organizationName", "labelName", "name", "english")) {
            Object value = values.get(p);
            String text = value instanceof JsonNode n ? asText(n) : Objects.toString(value, "");
            if (!text.isBlank())
                return text;
        }
        return "";
    }

    private String catalogSignature(Map<String, ?> values) {
        Map<String, String> identity = new TreeMap<>();
        for (String p : List.of("description", "testSectionName", "dictEntry", "categoryName", "methodName",
                "panelName", "analyteName", "scriptletName", "unitOfMeasureName", "localAbbreviation", "testResultType",
                "loincCode", "organizationName", "labelName", "name")) {
            Object v = values.get(p);
            String text = v instanceof JsonNode n ? asText(n) : Objects.toString(v, "");
            if (!text.isBlank())
                identity.put(p, normalize(text));
        }
        return serialize(identity);
    }

    private String family(TestConfigurationPackage bundle) {
        return hash(serialize(new TreeSet<>(bundle.roots)));
    }

    private String fingerprint(ImportRequest request) {
        return hash(serialize(canonical(JSON.valueToTree(Map.of("bundle", request.bundle, "catalogs", request.catalogs,
                "overrides", request.overrides, "copy", request.copy)))));
    }

    private TestConfigurationPackage appliedPackage(ImportRequest request) {
        TestConfigurationPackage applied = new TestConfigurationPackage();
        applied.roots = request.bundle.roots;
        applied.nodes = request.bundle.nodes.stream().map(n -> effectiveNode(n, request)).toList();
        return applied;
    }

    private String importFingerprint(ImportRequest request) {
        // The same source file can define several independent destination tests.
        // Only an identical reviewed configuration and catalog selection is a replay.
        return hash(serialize(canonical(JSON.valueToTree(Map.of("package",
                packageFingerprint(appliedPackage(request)), "catalogs", request.catalogs)))));
    }

    private Optional<TestConfigurationRevision> previousImport(ImportRequest request) {
        if (request.copy)
            return Optional.empty();
        Optional<TestConfigurationRevision> previous = dao.findRevision(importFingerprint(request));
        if (previous.isPresent())
            return previous;
        // Older revisions used only the source file as their fingerprint. Preserve
        // their replay protection without treating a renamed test as already created.
        return dao.findRevision(packageFingerprint(request.bundle)).filter(revision -> {
            try {
                TestConfigurationPackage applied = JSON.readValue(revision.packageJson, TestConfigurationPackage.class);
                return packageFingerprint(applied).equals(packageFingerprint(appliedPackage(request)))
                        && JSON.readTree(revision.mappingJson).path("catalogs")
                                .equals(JSON.valueToTree(request.catalogs));
            } catch (Exception e) {
                throw new IllegalStateException("testPackage.issue.history", e);
            }
        });
    }

    private String packageFingerprint(TestConfigurationPackage bundle) {
        ObjectNode payload = JSON.valueToTree(bundle);
        payload.set("roots", JSON.valueToTree(new TreeSet<>(bundle.roots)));
        payload.set("nodes", JSON.valueToTree(bundle.nodes.stream().sorted(Comparator.comparing(n -> n.key)).toList()));
        return hash(serialize(canonical(payload)));
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(f -> sorted.put(f.getKey(), f.getValue()));
            sorted.forEach((k, v) -> result.set(k, canonical(v)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(v -> result.add(canonical(v)));
            return result;
        }
        return node;
    }

    private String serialize(Object object) {
        try {
            return JSON.writeValueAsString(object);
        } catch (Exception e) {
            throw bad("format");
        }
    }

    private String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private IllegalArgumentException bad(String code) {
        return new IllegalArgumentException("testPackage.issue." + code);
    }
}
