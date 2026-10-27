package de.codepath.backend.engine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    @ConditionalOnMissingBean(CodeRunner.class)
    CodeRunner disabledCodeRunner() {
        return new DisabledCodeRunner();
    }
}
