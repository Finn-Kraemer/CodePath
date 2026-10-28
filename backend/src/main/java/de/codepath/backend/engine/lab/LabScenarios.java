package de.codepath.backend.engine.lab;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LabScenarios {

    private final Map<String, LabScenario> byId = new LinkedHashMap<>();

    public LabScenarios(List<LabScenario> scenarios) {
        scenarios.forEach(s -> byId.put(s.id(), s));
    }

    public Optional<LabScenario> find(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public List<LabScenario> all() {
        return List.copyOf(byId.values());
    }
}
