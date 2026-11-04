package org.openelisglobal;

import static org.junit.Assert.*;

import java.util.Locale;
import liquibase.change.core.AddColumnChange;
import liquibase.change.core.CreateSequenceChange;
import liquibase.change.core.CreateTableChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;

public class UcdMigrationPlanTest {
    @Test
    public void completeInstallationPlanParsesWithoutCreatingRemovedIdentifierStructures() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            String entry = "liquibase/base-changelog.xml";
            var plan = ChangeLogParserFactory.getInstance().getParser(entry, resources).parse(entry,
                    new ChangeLogParameters(), resources);
            assertFalse(plan.getChangeSets().isEmpty());
            assertTrue(plan.getChangeSets().stream()
                    .anyMatch(step -> "105-training-laboratory-number-reset-guard".equals(step.getId())));
            for (var step : plan.getChangeSets()) {
                for (var change : step.getChanges()) {
                    if (change instanceof CreateTableChange table) {
                        assertFalse(table.getTableName().toLowerCase(Locale.ROOT).contains("cug"));
                        assertTrue(table.getColumns().stream()
                                .noneMatch(column -> "cug_code".equalsIgnoreCase(column.getName())));
                    }
                    if (change instanceof CreateSequenceChange sequence)
                        assertFalse(sequence.getSequenceName().toLowerCase(Locale.ROOT).contains("cug"));
                    if (change instanceof AddColumnChange columns)
                        assertTrue(columns.getColumns().stream()
                                .noneMatch(column -> "cug_code".equalsIgnoreCase(column.getName())));
                }
            }
        }
    }
}
