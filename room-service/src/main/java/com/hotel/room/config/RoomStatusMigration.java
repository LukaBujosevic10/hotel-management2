package com.hotel.room.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off data migration.
 *
 * The OCCUPIED status no longer exists. Rows written by the previous version
 * would make Hibernate fail with "No enum constant RoomStatus.OCCUPIED" on read,
 * so they are normalised before the application starts serving traffic.
 *
 * @DependsOn("entityManagerFactory") guarantees this runs AFTER Hibernate has
 * created/updated the schema but still during context refresh.
 */
@Component
@DependsOn("entityManagerFactory")
public class RoomStatusMigration {

    private static final Logger log = LoggerFactory.getLogger(RoomStatusMigration.class);

    private final JdbcTemplate jdbc;

    public RoomStatusMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void normalizeLegacyStatuses() {
        try {
            int updated = jdbc.update("UPDATE rooms SET status = 'AVAILABLE' WHERE status = 'OCCUPIED'");
            if (updated > 0) {
                log.info("Migrated {} room(s) from the removed OCCUPIED status to AVAILABLE", updated);
            }
        } catch (Exception ex) {
            // Fresh database: the table may not exist yet. Nothing to migrate.
            log.debug("Skipping room status migration: {}", ex.getMessage());
        }
    }
}
