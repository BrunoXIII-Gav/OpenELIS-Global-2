package org.openelisglobal.testconfiguration.packageio.form;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A closed configuration graph. Keys are portable references, never database
 * IDs.
 */
public class TestConfigurationPackage {
    public String format = "openelis-test-configuration";
    public int version = 1;
    public List<String> roots = new ArrayList<>();
    public List<Node> nodes = new ArrayList<>();

    public static class Node {
        public String key;
        public String kind;
        public boolean catalog;
        public Map<String, JsonNode> values = new LinkedHashMap<>();
        public Map<String, String> references = new LinkedHashMap<>();
    }

    public static class ImportRequest {
        public TestConfigurationPackage bundle;
        public Map<String, String> catalogs = new LinkedHashMap<>();
        public Map<String, Map<String, JsonNode>> overrides = new LinkedHashMap<>();
        public boolean copy;
        public String previewToken;
    }

    public record SelectionRequest(TestConfigurationPackage bundle, String testKey) {
    }

    public record OriginClassification(List<String> testIds, boolean userCreated) {
    }

    public static class Preview {
        public String token;
        public boolean ready;
        public boolean alreadyImported;
        public List<Issue> issues = new ArrayList<>();
        public List<CatalogChoice> catalogs = new ArrayList<>();
        public List<Map<String, Object>> tests = new ArrayList<>();
        public List<Map<String, Object>> differences = new ArrayList<>();
    }

    public record Issue(String code, String key, String property) {
    }

    public record Choice(String id, String label) {
    }

    public record CatalogChoice(String key, String kind, String label, String suggestedId, boolean canCreate,
            List<Choice> candidates) {
    }

    public record ImportResult(String revisionId, boolean alreadyImported, Map<String, String> testIds) {
    }
}
