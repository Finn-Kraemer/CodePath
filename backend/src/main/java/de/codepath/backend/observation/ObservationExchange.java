package de.codepath.backend.observation;

import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.pdf.PdfFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Export und Import eines Beobachtungsbogens (JSON mit Raum, Kriterien, Plätzen, Bewertungen und Notizen),
 * CSV für Tabellenkalkulationen und PDF. Im Audit-Log steht nur, dass exportiert wurde – nie Inhalte.
 */
@Service
@RequiredArgsConstructor
public class ObservationExchange {

    static final String FORMAT = "codepath-observation";

    private final ObservationService service;
    private final ObservationPersonRepository personRepository;
    private final ObservationCriterionRepository criterionRepository;
    private final AuditService auditService;

    public record ExportCriterion(String name, ObservationCriterion.Kind kind, BigDecimal min, BigDecimal max,
                                  List<ObservationService.ObservationOption> options) {
    }

    /**
     * @param username Schülerkonto (beim Import über den Benutzernamen zugeordnet) oder null
     * @param ratings  Kriterium (Name) → Wert
     */
    public record ExportPerson(String username, String name, String seatId, String note, Map<String, String> ratings) {
    }

    public record BoardExport(String format, int version, String course, RoomLayout layout, List<ExportCriterion> criteria,
                              List<ExportPerson> people) {
    }

    public record ImportResult(ObservationService.BoardView board, int criteria, int people, List<String> warnings) {
    }

    // ------------------------------------------------------------------ JSON

    @Transactional
    public BoardExport export(Long courseId) {
        ObservationService.BoardView view = service.board(courseId);
        Map<Long, String> names = new HashMap<>();
        List<ExportCriterion> criteria = new ArrayList<>();
        for (ObservationService.CriterionView c : view.criteria()) {
            names.put(c.id(), c.name());
            criteria.add(new ExportCriterion(c.name(), c.kind(), c.min(), c.max(), c.options()));
        }
        List<ExportPerson> people = new ArrayList<>();
        for (ObservationService.PersonView p : ordered(view)) {
            Map<String, String> ratings = new LinkedHashMap<>();
            for (ObservationService.CriterionView c : view.criteria()) {
                String value = p.ratings().get(c.id());
                if (value != null) ratings.put(c.name(), value);
            }
            people.add(new ExportPerson(p.username(), p.name(), p.seatId(), p.note(), ratings));
        }
        auditService.log("OBSERVATION_EXPORTED", "course:" + courseId, Map.of("format", "json"));
        return new BoardExport(FORMAT, 1, view.courseName(), view.layout(), criteria, people);
    }

    /**
     * Übernimmt einen exportierten Bogen in den Kurs: Raum ersetzen (wenn die Datei einen hat), Kriterien über
     * den Namen abgleichen, Personen über den Benutzernamen (nur Mitglieder des Kurses) bzw. den frei
     * eingetragenen Namen. Nicht passende Werte landen als Hinweis im Ergebnis.
     */
    @Transactional
    public ImportResult importBoard(Long courseId, BoardExport data) {
        if (data == null || !FORMAT.equals(data.format())) throw ApiException.badRequest("Das ist keine Exportdatei eines Beobachtungsbogens.");
        ObservationBoard board = service.writable(courseId);
        List<String> warnings = new ArrayList<>();
        if (data.layout() != null && data.layout().items() != null && !data.layout().items().isEmpty()) {
            service.setLayout(board, RoomLayout.validate(data.layout()));
        }

        Map<String, ObservationCriterion> criteria = new HashMap<>();
        for (ObservationCriterion c : criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId())) {
            criteria.put(c.getName().toLowerCase(Locale.ROOT), c);
        }
        int criteriaCount = 0;
        for (ExportCriterion in : Optional.ofNullable(data.criteria()).orElse(List.of())) {
            if (in == null || in.name() == null) continue;
            ObservationService.CriterionRequest request = new ObservationService.CriterionRequest(in.name(), in.kind(), in.min(), in.max(), in.options());
            ObservationCriterion existing = criteria.get(in.name().strip().toLowerCase(Locale.ROOT));
            try {
                if (existing != null) {
                    service.apply(existing, request);
                    criterionRepository.save(existing);
                } else {
                    existing = service.addCriterion(board, request);
                    criteria.put(existing.getName().toLowerCase(Locale.ROOT), existing);
                }
                criteriaCount++;
            } catch (ApiException e) {
                warnings.add("Kriterium „" + in.name() + "“: " + e.getMessage());
            }
        }

        Map<String, ObservationPerson> byUsername = new HashMap<>();
        Map<String, ObservationPerson> byName = new HashMap<>();
        for (ObservationService.PersonView p : service.view(board).people()) {
            ObservationPerson person = service.person(board, p.id());
            if (p.username() != null) byUsername.put(p.username(), person);
            else byName.put(p.name().toLowerCase(Locale.ROOT), person);
        }
        Set<String> seats = service.layout(board).seatIds();
        int peopleCount = 0;
        for (ExportPerson in : Optional.ofNullable(data.people()).orElse(List.of())) {
            if (in == null) continue;
            ObservationPerson person = in.username() != null ? byUsername.get(in.username()) : null;
            if (person == null) {
                String name = in.name() != null && !in.name().isBlank() ? in.name().strip() : in.username();
                if (name == null || name.isBlank()) continue;
                if (in.username() != null) warnings.add("@" + in.username() + " gehört nicht zum Kurs – als Name ohne Konto übernommen.");
                person = byName.get(name.toLowerCase(Locale.ROOT));
                if (person == null) {
                    try {
                        person = service.addFreePerson(board, name);
                    } catch (ApiException e) {
                        warnings.add(name + ": " + e.getMessage());
                        continue;
                    }
                    byName.put(person.getName().toLowerCase(Locale.ROOT), person);
                }
            }
            String label = in.username() != null ? "@" + in.username() : person.getName();
            if (in.seatId() != null && seats.contains(in.seatId())) service.assignSeat(board, person, in.seatId(), false);
            if (in.note() != null) {
                person.setNote(in.note().length() > ObservationService.MAX_NOTE ? in.note().substring(0, ObservationService.MAX_NOTE) : in.note());
                personRepository.save(person);
            }
            for (Map.Entry<String, String> r : Optional.ofNullable(in.ratings()).orElse(Map.of()).entrySet()) {
                ObservationCriterion criterion = r.getKey() == null ? null : criteria.get(r.getKey().strip().toLowerCase(Locale.ROOT));
                if (criterion == null) {
                    warnings.add(label + ": Kriterium „" + r.getKey() + "“ fehlt.");
                    continue;
                }
                try {
                    service.rate(person, criterion, r.getValue());
                } catch (ApiException e) {
                    warnings.add(label + ": " + e.getMessage());
                }
            }
            peopleCount++;
        }
        auditService.log("OBSERVATION_IMPORTED", "course:" + courseId, Map.of("criteria", criteriaCount, "people", peopleCount));
        return new ImportResult(service.view(board), criteriaCount, peopleCount, warnings);
    }

    // ------------------------------------------------------------------ CSV

    /** Eine Zeile je Person in Sitzplan-Reihenfolge: Name, Benutzername, Platz, Kriterien, Notizen. */
    @Transactional
    public String csv(Long courseId) {
        ObservationService.BoardView view = service.board(courseId);
        Map<String, String> seatLabels = seatLabels(view.layout());
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Name;Benutzername;Platz");
        for (ObservationService.CriterionView c : view.criteria()) sb.append(';').append(csvCell(c.name()));
        sb.append(";Notizen\n");
        for (ObservationService.PersonView p : ordered(view)) {
            sb.append(csvCell(p.name())).append(';').append(csvCell(p.username())).append(';')
                    .append(csvCell(p.seatId() == null ? null : seatLabels.get(p.seatId())));
            for (ObservationService.CriterionView c : view.criteria()) sb.append(';').append(csvCell(p.ratings().get(c.id())));
            sb.append(';').append(csvCell(p.note())).append('\n');
        }
        auditService.log("OBSERVATION_EXPORTED", "course:" + courseId, Map.of("format", "csv"));
        return sb.toString();
    }

    static String csvCell(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        // Keine Formeln in Tabellenprogrammen auslösen
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?")) v = "'" + v;
        return v.contains(";") || v.contains("\n") || v.contains("\r") || v.contains("\"") ? "\"" + v + "\"" : v;
    }

    // ------------------------------------------------------------------ PDF

    /** Sitzplan und Tabelle, optional eine Seite je Person. */
    @Transactional
    public PdfFile pdf(Long courseId, boolean perPerson) {
        ObservationService.BoardView view = service.board(courseId);
        byte[] content = ObservationPdf.render(view, ordered(view), seatLabels(view.layout()), perPerson, LocalDate.now());
        auditService.log("OBSERVATION_EXPORTED", "course:" + courseId, Map.of("format", "pdf", "perPerson", perPerson));
        return new PdfFile("Beobachtungsbogen_Kurs_" + courseId + ".pdf", "Beobachtungsbogen " + view.courseName(), content);
    }

    // ------------------------------------------------------------------ Hilfen

    static Map<String, String> seatLabels(RoomLayout layout) {
        Map<String, String> labels = new HashMap<>();
        for (RoomLayout.SeatRef seat : layout.seatsInOrder()) labels.put(seat.id(), seat.label());
        return labels;
    }

    /** Personen in Sitzplan-Reihenfolge, danach alle ohne Platz alphabetisch. */
    static List<ObservationService.PersonView> ordered(ObservationService.BoardView view) {
        Map<String, Integer> order = new HashMap<>();
        List<RoomLayout.SeatRef> seats = view.layout().seatsInOrder();
        for (int i = 0; i < seats.size(); i++) order.put(seats.get(i).id(), i);
        List<ObservationService.PersonView> people = new ArrayList<>(view.people());
        people.sort(Comparator.comparingInt((ObservationService.PersonView p) -> p.seatId() == null ? Integer.MAX_VALUE
                        : order.getOrDefault(p.seatId(), Integer.MAX_VALUE))
                .thenComparing(ObservationService.PersonView::name, String.CASE_INSENSITIVE_ORDER));
        return people;
    }
}
