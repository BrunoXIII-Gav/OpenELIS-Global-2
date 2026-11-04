package org.openelisglobal.testadditionalfield.bean;

import java.util.Map;

/** Values and concurrency tokens read together from the same persisted rows. */
public record AnalysisFieldSnapshot(Map<String, String> values, Map<String, String> versions) {
}
