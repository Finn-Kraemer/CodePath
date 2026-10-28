package de.codepath.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V12 übernimmt bestehende Daten: Nutzer kommen in einen "Bestandskurs", Freischaltungen werden zu
 * Kursfreigaben, gelöste Aufgaben zu Punkte-Buchungen, Praxisabgaben zu Versuchen mit Bewertung.
 */
class LegacyMigrationTest {

    @Test
    void migratesPhaseZeroDataIntoTheNewModel() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            DriverManagerDataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(ds).target("11").placeholderReplacement(false).load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            jdbc.execute("""
                    insert into users (id, username, password_hash, role, total_points) values
                      (1, 'admin', 'x', 'ADMIN', 0), (2, 'lena', 'x', 'STUDENT', 25), (3, 'tim', 'x', 'STUDENT', 0);
                    insert into modules (id, slug, title, is_unlocked) values (1, 'git', 'Git', true), (2, 'web', 'Web', false);
                    insert into tasks (id, module_id, slug, title, type, difficulty, points, config) values
                      (1, 1, 'g1', 'G1', 'MULTIPLE_CHOICE', 'EASY', 10, '{}'),
                      (2, 2, 'w13', 'W13', 'PRACTICE', 'HARD', 50, '{}');
                    insert into user_task_completions (user_id, task_id, points_awarded, is_completed, failed_attempts, support_used, is_locked)
                      values (2, 1, 15, true, 0, false, false), (3, 1, 0, false, 2, true, true);
                    insert into practice_submissions (user_id, task_id, status, content, admin_comment)
                      values (2, 2, 'APPROVED', '<p>alt</p>', 'gut'), (3, 2, 'PENDING', '<p>neu</p>', null);
                    """);

            Flyway.configure().dataSource(ds).placeholderReplacement(false).load().migrate();

            assertEquals(1, jdbc.queryForObject("select count(*) from courses where name = 'Bestandskurs'", Integer.class));
            assertEquals(2, jdbc.queryForObject("select count(*) from memberships", Integer.class));
            assertEquals(Boolean.TRUE, jdbc.queryForObject("select unlocked from module_releases where module_id = 1", Boolean.class));
            assertEquals(15, jdbc.queryForObject("select sum(amount) from points_entries where user_id = 2", Integer.class));
            assertEquals("SOLVED", jdbc.queryForObject("select status from progress where user_id = 2 and task_id = 1", String.class));
            assertEquals(Boolean.TRUE, jdbc.queryForObject("select locked from progress where user_id = 3 and task_id = 1", Boolean.class));
            assertEquals(1, jdbc.queryForObject("select hints_used from progress where user_id = 3 and task_id = 1", Integer.class));
            assertEquals("PENDING_REVIEW", jdbc.queryForObject("select status from progress where user_id = 3 and task_id = 2", String.class));
            assertEquals(2, jdbc.queryForObject("select count(*) from practice_reviews", Integer.class));
            assertEquals("gut", jdbc.queryForObject("select r.comment from practice_reviews r join attempts a on a.id = r.attempt_id where a.user_id = 2", String.class));
            assertEquals("<p>neu</p>", jdbc.queryForObject("select answer->>'content' from attempts where user_id = 3", String.class));
        }
    }
}
