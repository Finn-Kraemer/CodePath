package de.codepath.backend.evaluation;

import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Vorlagen für Bewertungsbögen anlegen, bearbeiten, löschen. */
@Service
@RequiredArgsConstructor
public class EvaluationTemplateService {

    static final int MAX_NAME = 200;
    static final int MAX_DESCRIPTION = 2000;
    private static final TypeReference<List<EvaluationCriterion>> CRITERIA = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> RAW = new TypeReference<>() {
    };

    private final EvaluationTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public record TemplateView(Long id, String slug, String name, String description, EvaluationPart kind,
                               List<EvaluationCriterion> criteria, LocalDateTime updatedAt) {
    }

    public record TemplateRequest(String name, String description, EvaluationPart kind, List<EvaluationCriterion> criteria) {
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        return repository.findAllByOrderByKindAscNameAsc().stream().map(this::view).toList();
    }

    @Transactional
    public TemplateView create(TemplateRequest request) {
        EvaluationTemplate template = new EvaluationTemplate();
        apply(template, request);
        template.setSlug(uniqueSlug(template.getName()));
        return view(repository.save(template));
    }

    @Transactional
    public TemplateView update(Long id, TemplateRequest request) {
        EvaluationTemplate template = get(id);
        apply(template, request);
        template.setUpdatedAt(LocalDateTime.now());
        return view(template);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }

    EvaluationTemplate get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Vorlage nicht gefunden."));
    }

    void apply(EvaluationTemplate template, TemplateRequest request) {
        if (request == null) throw ApiException.badRequest("Keine Angaben.");
        if (request.name() == null || request.name().isBlank()) throw ApiException.badRequest("Die Vorlage braucht einen Namen.");
        String name = request.name().strip();
        if (name.length() > MAX_NAME) throw ApiException.badRequest("Der Name ist zu lang.");
        String description = request.description() == null || request.description().isBlank() ? null : request.description().strip();
        if (description != null && description.length() > MAX_DESCRIPTION) throw ApiException.badRequest("Die Beschreibung ist zu lang.");
        if (request.kind() == null) throw ApiException.badRequest("Bitte angeben, wofür die Vorlage gedacht ist.");
        if (request.criteria() == null) throw ApiException.badRequest("Die Vorlage braucht Kriterien.");
        template.setName(name);
        template.setDescription(description);
        template.setKind(request.kind());
        template.setCriteria(toRaw(Criteria.normalize(request.criteria())));
    }

    TemplateView view(EvaluationTemplate t) {
        return new TemplateView(t.getId(), t.getSlug(), t.getName(), t.getDescription(), t.getKind(),
                toCriteria(t.getCriteria()), t.getUpdatedAt());
    }

    List<EvaluationCriterion> toCriteria(List<Map<String, Object>> raw) {
        return raw == null ? null : objectMapper.convertValue(raw, CRITERIA);
    }

    List<Map<String, Object>> toRaw(List<EvaluationCriterion> criteria) {
        return criteria == null ? null : objectMapper.convertValue(criteria, RAW);
    }

    String uniqueSlug(String name) {
        String base = slugify(name);
        String slug = base;
        for (int i = 2; repository.existsBySlug(slug); i++) slug = base + "-" + i;
        return slug;
    }

    static String slugify(String name) {
        String s = name.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (s.length() > 70) s = s.substring(0, 70).replaceAll("-+$", "");
        return s.isEmpty() ? "vorlage" : s;
    }
}
