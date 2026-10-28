package de.codepath.backend;

import de.codepath.backend.content.ContentPackage;
import de.codepath.backend.content.ContentPackageReader;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.engine.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Probelauf für jede mitgelieferte Aufgabe: Konfiguration gültig, Musterlösung wird als richtig,
 * eine falsche Antwort als falsch bewertet. Mit CODEPATH_RUNNER_URL laufen auch die Code-Tests wirklich.
 */
class ContentPackagesTest extends IntegrationTestBase {

    @Autowired
    ContentPackageReader reader;
    @Autowired
    TaskHandlerRegistry registry;
    @Autowired
    CodeRunner runner;

    @Test
    void everyPackagedTaskPassesTheDryRun() throws Exception {
        List<ContentPackage.ModuleBundle> bundles = reader.fromClasspath("content");
        assertEquals(9, bundles.size());
        List<String> failures = new ArrayList<>();
        int count = 0;
        for (var bundle : bundles) {
            for (var tf : bundle.tasks()) {
                count++;
                String where = bundle.module().slug() + "/" + tf.slug();
                TaskHandler handler = registry.get(tf.type());
                List<String> problems = handler.validate(tf.config(), tf.points());
                if (!problems.isEmpty()) {
                    failures.add(where + ": " + problems);
                    continue;
                }
                TaskSpec spec = new TaskSpec(tf.type(), tf.points(), tf.config(), AttemptRules.from(tf.type(), tf.rules()), tf.hints());
                TaskContext context = new TaskContext(1);
                Evaluation right = handler.evaluate(spec, handler.sampleAnswer(spec, context), context);
                Evaluation wrong = handler.evaluate(spec, handler.wrongAnswer(spec, context), context);
                Evaluation.Outcome expected = !handler.autoGraded() ? Evaluation.Outcome.MANUAL
                        : tf.type() == TaskType.CODE && !runner.available() ? Evaluation.Outcome.UNGRADED : Evaluation.Outcome.CORRECT;
                if (right.outcome() != expected) failures.add(where + ": Musterlösung ergibt " + right.outcome() + " (" + right.feedback() + ")");
                if (wrong.isCorrect()) failures.add(where + ": falsche Antwort gilt als richtig");
            }
        }
        assertEquals(129, count);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void seederImportedEverythingAsVersionOne(@Autowired ContentService content) {
        var tasks = content.allPublishedTasks();
        assertEquals(129, tasks.size());
        assertTrue(tasks.stream().allMatch(t -> t.version().getVersion() >= 1 && t.version().isPublished()));
    }
}
