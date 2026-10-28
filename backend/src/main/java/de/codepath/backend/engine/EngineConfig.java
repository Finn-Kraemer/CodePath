package de.codepath.backend.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class EngineConfig {

    /** Mit CODEPATH_RUNNER_URL und CODEPATH_RUNNER_TOKEN werden Code-Aufgaben serverseitig bewertet. */
    @Bean
    // Leerer Wert (z. B. aus docker-compose ohne Runner) zählt als „nicht eingerichtet“
    @ConditionalOnExpression("!'${codepath.runner.url:}'.isBlank()")
    CodeRunner httpCodeRunner(@Value("${codepath.runner.url}") String url,
                              @Value("${codepath.runner.token:}") String token,
                              ObjectMapper objectMapper) {
        if (token.length() < 16) {
            throw new IllegalStateException("CODEPATH_RUNNER_TOKEN fehlt oder ist kürzer als 16 Zeichen.");
        }
        return new HttpCodeRunner(url, token, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(CodeRunner.class)
    CodeRunner disabledCodeRunner() {
        return new DisabledCodeRunner();
    }
}
