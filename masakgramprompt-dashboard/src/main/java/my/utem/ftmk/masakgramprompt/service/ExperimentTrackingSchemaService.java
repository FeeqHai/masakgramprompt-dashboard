package my.utem.ftmk.masakgramprompt.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Adds missing timing columns used by experiment progress and performance pages.
 */
@Service
public class ExperimentTrackingSchemaService {

    private final JdbcTemplate jdbcTemplate;

    public ExperimentTrackingSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Performs a safe startup migration for older database schemas.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void addTrackingColumnsIfNeeded() {
        try {
            addColumnIfMissing("started_at", "TIMESTAMP NULL");
            addColumnIfMissing("finished_at", "TIMESTAMP NULL");
            addColumnIfMissing("processing_time_ms", "BIGINT NULL");
        } catch (Exception ex) {
            System.err.println("Experiment timing upgrade was skipped: " + ex.getMessage());
        }
    }

    /**
     * Checks information_schema before running ALTER TABLE for one column.
     */
    private void addColumnIfMissing(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'experiment'
                  AND column_name = ?
                """, Integer.class, columnName);

        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE experiment ADD COLUMN " + columnName + " " + definition);
        }
    }
}
