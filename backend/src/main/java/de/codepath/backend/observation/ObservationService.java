package de.codepath.backend.observation;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Klassenraum mit Sitzplan und Beobachtungsbogen je Kurs. Jedes Mitglied des Kurses hat eine Zeile; dazu
 * kommen frei eingetragene Namen ohne Konto. Kriterien sind Zahlen (optional mit Bereich) oder
 * Auswahllisten; je Person und Kriterium gibt es einen aktuellen Wert, dazu eine freie Notiz.
 * <p>
 * Datenschutz: Bewertungen und Notizen sind nur für Lehrkräfte da ({@code /api/admin/**}), stehen nie im
 * Audit-Log und werden mit dem Kurs bzw. Konto gelöscht.
 */
@Service
@RequiredArgsConstructor
public class ObservationService {

    static final int MAX_CRITERIA = 30;
    static final int MAX_OPTIONS = 30;
    static final int MAX_FREE_PEOPLE = 200;
    static final int MAX_NOTE = 5000;
    private static final BigDecimal LIMIT = new BigDecimal("1000000000");

    private final ObservationBoardRepository boardRepository;
    private final ObservationCriterionRepository criterionRepository;
    private final ObservationPersonRepository personRepository;
    private final ObservationRatingRepository ratingRepository;
    private final CourseService courseService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public record ObservationOption(String value, String description) {
    }

    public record CriterionRequest(String name, ObservationCriterion.Kind kind, BigDecimal min, BigDecimal max,
                                   List<ObservationOption> options) {
    }

    public record CriterionView(Long id, String name, ObservationCriterion.Kind kind, BigDecimal min, BigDecimal max,
                                List<ObservationOption> options) {
    }

    /**
     * @param userId   Schülerkonto oder null bei frei eingetragenem Namen
     * @param ratings  Kriterium-ID → Wert
     */
    public record PersonView(Long id, Long userId, String username, String name, String seatId, String note,
                             Map<Long, String> ratings, LocalDateTime updatedAt) {
    }

    /** @param editable false bei beendeten Kursen (nur noch lesen) */
    public record BoardView(Long courseId, String courseName, boolean editable, RoomLayout layout, LocalDateTime updatedAt,
                            List<CriterionView> criteria, List<PersonView> people) {
    }

    /** Vorlage (ohne Kurs) oder Bogen eines anderen Kurses als Quelle für Raum und Kriterien. */
    public record TemplateSummary(Long id, String name, Long courseId, int tables, int seats, int criteria) {
    }

    // ------------------------------------------------------------------ Lesen

    /** Öffnet den Bogen zum Bearbeiten: legt ihn beim ersten Aufruf an und ergänzt neue Kursmitglieder. */
    @Transactional
    public BoardView open(Long courseId) {
        return view(boardFor(courseId));
    }

    /**
     * Stand des Bogens nur lesend (für GET und Exporte – schreibt nichts): ohne Bogen ein leerer Raum,
     * Kursmitglieder ohne eigene Zeile erscheinen leer (ohne ID), Personen außerhalb des Kurses fehlen.
     */
    @Transactional(readOnly = true)
    public BoardView snapshot(Long courseId) {
        Course course = courseService.get(courseId);
        Set<Long> members = new LinkedHashSet<>(userService.memberIds(courseId));
        Optional<ObservationBoard> board = boardRepository.findByCourseId(courseId);
        BoardView base = board.map(this::view).orElseGet(() -> new BoardView(course.getId(), course.getName(),
                course.isReadable(), RoomLayout.empty(), null, List.of(), List.of()));
        List<PersonView> people = new ArrayList<>();
        Set<Long> present = new HashSet<>();
        for (PersonView p : base.people()) {
            if (p.userId() != null && !members.contains(p.userId())) continue;
            if (p.userId() != null) present.add(p.userId());
            people.add(p);
        }
        for (Long userId : members) {
            if (present.contains(userId)) continue;
            userService.byId(userId).ifPresent(u -> people.add(new PersonView(null, userId, u.getUsername(), u.getVisibleName(),
                    null, null, Map.of(), null)));
        }
        people.sort(Comparator.comparing(PersonView::name, String.CASE_INSENSITIVE_ORDER));
        return new BoardView(base.courseId(), base.courseName(), base.editable(), base.layout(), base.updatedAt(),
                base.criteria(), people);
    }

    @Transactional(readOnly = true)
    public BoardView view(ObservationBoard board) {
        Course course = courseService.get(board.getCourseId());
        List<CriterionView> criteria = criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId()).stream()
                .map(this::criterionView).toList();
        List<ObservationPerson> people = personRepository.findByBoardId(board.getId());
        Map<Long, Map<Long, String>> ratings = new HashMap<>();
        if (!people.isEmpty()) {
            for (ObservationRating r : ratingRepository.findByPersonIdIn(people.stream().map(ObservationPerson::getId).toList())) {
                ratings.computeIfAbsent(r.getPersonId(), k -> new LinkedHashMap<>()).put(r.getCriterionId(), r.getValue());
            }
        }
        List<PersonView> views = new ArrayList<>();
        for (ObservationPerson p : people) {
            User user = p.getUserId() == null ? null : userService.byId(p.getUserId()).orElse(null);
            if (p.getUserId() != null && user == null) continue;
            views.add(new PersonView(p.getId(), p.getUserId(), user == null ? null : user.getUsername(),
                    user == null ? p.getName() : user.getVisibleName(), p.getSeatId(), p.getNote(),
                    ratings.getOrDefault(p.getId(), Map.of()), p.getUpdatedAt()));
        }
        views.sort(Comparator.comparing(PersonView::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PersonView::id, Comparator.nullsLast(Comparator.naturalOrder())));
        return new BoardView(course.getId(), course.getName(), course.isReadable(), layout(board), board.getUpdatedAt(),
                criteria, views);
    }

    // ------------------------------------------------------------------ Raum

    @Transactional
    public BoardView updateLayout(Long courseId, RoomLayout layout) {
        ObservationBoard board = writable(courseId);
        setLayout(board, RoomLayout.validate(layout));
        return view(board);
    }

    void setLayout(ObservationBoard board, RoomLayout layout) {
        board.setLayout(objectMapper.writeValueAsString(layout));
        board.setUpdatedAt(LocalDateTime.now());
        boardRepository.save(board);
        Set<String> seats = layout.seatIds();
        for (ObservationPerson p : personRepository.findByBoardId(board.getId())) {
            if (p.getSeatId() != null && !seats.contains(p.getSeatId())) {
                p.setSeatId(null);
                personRepository.save(p);
            }
        }
    }

    // ------------------------------------------------------------------ Kriterien

    @Transactional
    public BoardView createCriterion(Long courseId, CriterionRequest request) {
        ObservationBoard board = writable(courseId);
        addCriterion(board, request);
        touch(board);
        return view(board);
    }

    ObservationCriterion addCriterion(ObservationBoard board, CriterionRequest request) {
        if (criterionRepository.countByBoardId(board.getId()) >= MAX_CRITERIA) {
            throw ApiException.badRequest("Höchstens " + MAX_CRITERIA + " Kriterien je Bogen.");
        }
        ObservationCriterion criterion = new ObservationCriterion();
        criterion.setBoardId(board.getId());
        criterion.setPosition(criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId()).stream()
                .mapToInt(ObservationCriterion::getPosition).max().orElse(-1) + 1);
        apply(criterion, request);
        return criterionRepository.save(criterion);
    }

    @Transactional
    public BoardView updateCriterion(Long courseId, Long criterionId, CriterionRequest request) {
        ObservationBoard board = writable(courseId);
        ObservationCriterion criterion = criterion(board, criterionId);
        apply(criterion, request);
        criterionRepository.save(criterion);
        // Werte, die nicht mehr passen (Bereich enger, Option entfernt, Typ gewechselt), fallen weg
        List<ObservationOption> options = options(criterion);
        for (ObservationRating rating : ratingRepository.findByCriterionId(criterionId)) {
            String value;
            try {
                value = normalize(criterion, options, rating.getValue());
            } catch (ApiException e) {
                value = null;
            }
            if (value == null) {
                ratingRepository.delete(rating);
            } else if (!value.equals(rating.getValue())) {
                rating.setValue(value);
                ratingRepository.save(rating);
            }
        }
        touch(board);
        return view(board);
    }

    @Transactional
    public BoardView deleteCriterion(Long courseId, Long criterionId) {
        ObservationBoard board = writable(courseId);
        criterionRepository.delete(criterion(board, criterionId));
        touch(board);
        return view(board);
    }

    @Transactional
    public BoardView reorderCriteria(Long courseId, List<Long> ids) {
        ObservationBoard board = writable(courseId);
        List<ObservationCriterion> criteria = criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId());
        if (ids == null || ids.size() != criteria.size() || !new HashSet<>(ids).equals(
                new HashSet<>(criteria.stream().map(ObservationCriterion::getId).toList()))) {
            throw ApiException.badRequest("Die Reihenfolge muss alle Kriterien genau einmal enthalten.");
        }
        for (ObservationCriterion c : criteria) c.setPosition(ids.indexOf(c.getId()));
        criterionRepository.saveAll(criteria);
        touch(board);
        return view(board);
    }

    void apply(ObservationCriterion criterion, CriterionRequest request) {
        if (request == null) throw ApiException.badRequest("Angaben fehlen.");
        String name = request.name() == null ? "" : request.name().strip();
        if (name.isEmpty()) throw ApiException.badRequest("Bitte einen Namen für das Kriterium angeben.");
        if (name.length() > 100) throw ApiException.badRequest("Der Name ist zu lang (höchstens 100 Zeichen).");
        ObservationCriterion.Kind kind = request.kind() == null ? ObservationCriterion.Kind.NUMBER : request.kind();
        criterion.setName(name);
        criterion.setKind(kind);
        if (kind == ObservationCriterion.Kind.NUMBER) {
            BigDecimal min = bound(request.min()), max = bound(request.max());
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw ApiException.badRequest("Das Minimum darf nicht größer als das Maximum sein.");
            }
            criterion.setMinValue(min);
            criterion.setMaxValue(max);
            criterion.setOptions("[]");
        } else {
            criterion.setMinValue(null);
            criterion.setMaxValue(null);
            criterion.setOptions(objectMapper.writeValueAsString(validOptions(request.options())));
        }
    }

    static List<ObservationOption> validOptions(List<ObservationOption> options) {
        if (options == null || options.isEmpty()) throw ApiException.badRequest("Eine Auswahlliste braucht mindestens eine Option.");
        if (options.size() > MAX_OPTIONS) throw ApiException.badRequest("Höchstens " + MAX_OPTIONS + " Optionen.");
        Set<String> seen = new HashSet<>();
        List<ObservationOption> result = new ArrayList<>();
        for (ObservationOption o : options) {
            String value = o == null || o.value() == null ? "" : o.value().strip();
            if (value.isEmpty()) throw ApiException.badRequest("Jede Option braucht einen Wert.");
            if (value.length() > 40) throw ApiException.badRequest("Der Wert einer Option ist zu lang (höchstens 40 Zeichen).");
            if (!seen.add(value)) throw ApiException.badRequest("Der Wert „" + value + "“ kommt mehrfach vor.");
            String description = o.description() == null || o.description().isBlank() ? null : o.description().strip();
            if (description != null && description.length() > 300) {
                throw ApiException.badRequest("Die Beschreibung ist zu lang (höchstens 300 Zeichen).");
            }
            result.add(new ObservationOption(value, description));
        }
        return result;
    }

    private static BigDecimal bound(BigDecimal value) {
        if (value == null) return null;
        if (value.abs().compareTo(LIMIT) >= 0) throw ApiException.badRequest("Der Bereich ist zu groß.");
        return clean(value.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------ Personen und Plätze

    @Transactional
    public BoardView addPerson(Long courseId, String name) {
        ObservationBoard board = writable(courseId);
        addFreePerson(board, name);
        touch(board);
        return view(board);
    }

    ObservationPerson addFreePerson(ObservationBoard board, String name) {
        if (personRepository.countByBoardIdAndUserIdIsNull(board.getId()) >= MAX_FREE_PEOPLE) {
            throw ApiException.badRequest("Höchstens " + MAX_FREE_PEOPLE + " frei eingetragene Namen.");
        }
        ObservationPerson person = new ObservationPerson();
        person.setBoardId(board.getId());
        person.setName(personName(name));
        return personRepository.save(person);
    }

    @Transactional
    public BoardView renamePerson(Long courseId, Long personId, String name) {
        ObservationBoard board = writable(courseId);
        ObservationPerson person = person(board, personId);
        if (person.getUserId() != null) throw ApiException.badRequest("Namen von Schülerkonten ändert man in der Kontoverwaltung.");
        person.setName(personName(name));
        person.setUpdatedAt(LocalDateTime.now());
        personRepository.save(person);
        return view(board);
    }

    @Transactional
    public BoardView deletePerson(Long courseId, Long personId) {
        ObservationBoard board = writable(courseId);
        ObservationPerson person = person(board, personId);
        if (person.getUserId() != null) throw ApiException.badRequest("Schülerkonten gehören zum Kurs und lassen sich hier nicht entfernen.");
        personRepository.delete(person);
        touch(board);
        return view(board);
    }

    /**
     * Setzt die Person auf einen Platz (null = Platz freigeben). Sitzt dort schon jemand, tauschen beide.
     */
    @Transactional
    public BoardView setSeat(Long courseId, Long personId, String seatId) {
        ObservationBoard board = writable(courseId);
        assignSeat(board, person(board, personId), seatId, true);
        touch(board);
        return view(board);
    }

    void assignSeat(ObservationBoard board, ObservationPerson person, String seatId, boolean swap) {
        String target = seatId == null || seatId.isBlank() ? null : seatId;
        if (target != null && !layout(board).seatIds().contains(target)) throw ApiException.notFound("Diesen Platz gibt es nicht.");
        if (Objects.equals(person.getSeatId(), target)) return;
        if (target != null) {
            Optional<ObservationPerson> occupant = personRepository.findByBoardIdAndSeatId(board.getId(), target);
            if (occupant.isPresent()) {
                occupant.get().setSeatId(swap ? person.getSeatId() : null);
                personRepository.save(occupant.get());
            }
        }
        person.setSeatId(target);
        personRepository.save(person);
    }

    @Transactional
    public PersonView setNote(Long courseId, Long personId, String note) {
        ObservationBoard board = writable(courseId);
        ObservationPerson person = person(board, personId);
        if (note != null && note.length() > MAX_NOTE) throw ApiException.badRequest("Die Notiz ist zu lang (höchstens " + MAX_NOTE + " Zeichen).");
        person.setNote(note == null || note.isBlank() ? null : note);
        person.setUpdatedAt(LocalDateTime.now());
        personRepository.save(person);
        return personView(board, person);
    }

    @Transactional
    public PersonView setRating(Long courseId, Long personId, Long criterionId, String value) {
        ObservationBoard board = writable(courseId);
        ObservationPerson person = person(board, personId);
        ObservationCriterion criterion = criterion(board, criterionId);
        rate(person, criterion, value);
        person.setUpdatedAt(LocalDateTime.now());
        personRepository.save(person);
        return personView(board, person);
    }

    void rate(ObservationPerson person, ObservationCriterion criterion, String raw) {
        String value = normalize(criterion, options(criterion), raw);
        ObservationRating.Key key = new ObservationRating.Key(person.getId(), criterion.getId());
        if (value == null) {
            ratingRepository.findById(key).ifPresent(ratingRepository::delete);
            return;
        }
        ObservationRating rating = ratingRepository.findById(key).orElseGet(() -> new ObservationRating(person.getId(), criterion.getId(), value));
        rating.setValue(value);
        rating.setUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);
    }

    /** Prüft einen Wert für das Kriterium; leer = kein Wert (null). Zahlen auch mit Komma. */
    static String normalize(ObservationCriterion criterion, List<ObservationOption> options, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        if (criterion.getKind() == ObservationCriterion.Kind.CHOICE) {
            if (options.stream().noneMatch(o -> o.value().equals(value))) {
                throw ApiException.badRequest("„" + value + "“ ist keine Option von „" + criterion.getName() + "“.");
            }
            return value;
        }
        BigDecimal number;
        try {
            number = new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Bitte eine Zahl eingeben.");
        }
        if (number.abs().compareTo(LIMIT) >= 0) throw ApiException.badRequest("Die Zahl ist zu groß.");
        if (criterion.getMinValue() != null && number.compareTo(criterion.getMinValue()) < 0
                || criterion.getMaxValue() != null && number.compareTo(criterion.getMaxValue()) > 0) {
            throw ApiException.badRequest("„" + criterion.getName() + "“ erlaubt " + range(criterion) + ".");
        }
        String text = number.stripTrailingZeros().toPlainString();
        if (text.length() > 40) throw ApiException.badRequest("Die Zahl hat zu viele Stellen.");
        return text;
    }

    static String range(ObservationCriterion c) {
        if (c.getMinValue() != null && c.getMaxValue() != null) {
            return "Werte von " + plain(c.getMinValue()) + " bis " + plain(c.getMaxValue());
        }
        if (c.getMinValue() != null) return "Werte ab " + plain(c.getMinValue());
        if (c.getMaxValue() != null) return "Werte bis " + plain(c.getMaxValue());
        return "beliebige Zahlen";
    }

    /** Ohne überflüssige Nachkommastellen (1.00 → 1), damit Vergleiche und JSON stabil sind. */
    static BigDecimal clean(BigDecimal value) {
        if (value == null) return null;
        BigDecimal v = value.stripTrailingZeros();
        return v.scale() < 0 ? v.setScale(0) : v;
    }

    static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String personName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) throw ApiException.badRequest("Bitte einen Namen eintragen.");
        if (n.length() > 100) throw ApiException.badRequest("Der Name ist zu lang (höchstens 100 Zeichen).");
        return n;
    }

    // ------------------------------------------------------------------ Vorlagen

    /** Vorlagen und die Bögen anderer Kurse, aus denen sich Raum und Kriterien übernehmen lassen. */
    @Transactional(readOnly = true)
    public List<TemplateSummary> templates() {
        List<TemplateSummary> result = new ArrayList<>();
        for (ObservationBoard b : boardRepository.findByCourseIdIsNullOrderByNameAscIdAsc()) result.add(summary(b, b.getName()));
        List<TemplateSummary> courses = new ArrayList<>();
        for (ObservationBoard b : boardRepository.findByCourseIdIsNotNull()) {
            Course course = courseService.get(b.getCourseId());
            if (course.isReadable()) courses.add(summary(b, course.getName()));
        }
        courses.sort(Comparator.comparing(TemplateSummary::name, String.CASE_INSENSITIVE_ORDER));
        result.addAll(courses);
        return result;
    }

    /** Speichert Raum und Kriterien des Kurses als Vorlage; eine Vorlage mit gleichem Namen wird ersetzt. */
    @Transactional
    public TemplateSummary saveTemplate(Long courseId, String name) {
        ObservationBoard source = boardFor(courseId);
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) throw ApiException.badRequest("Bitte einen Namen für die Vorlage angeben.");
        if (n.length() > 200) throw ApiException.badRequest("Der Name ist zu lang (höchstens 200 Zeichen).");
        ObservationBoard template = boardRepository.findFirstByCourseIdIsNullAndNameOrderByIdAsc(n).orElseGet(() -> {
            ObservationBoard b = new ObservationBoard();
            b.setName(n);
            return b;
        });
        template.setLayout(source.getLayout());
        template.setUpdatedAt(LocalDateTime.now());
        boardRepository.save(template);
        replaceCriteria(template, criterionRepository.findByBoardIdOrderByPositionAscIdAsc(source.getId()).stream().map(this::request).toList());
        return summary(template, n);
    }

    /** Übernimmt Raum und/oder Kriterien aus einer Vorlage oder einem anderen Kurs. Vorhandene Kriterien mit gleichem Namen bleiben. */
    @Transactional
    public BoardView applyTemplate(Long courseId, Long sourceId, boolean room, boolean criteria) {
        ObservationBoard board = writable(courseId);
        ObservationBoard source = boardRepository.findById(sourceId).orElseThrow(() -> ApiException.notFound("Vorlage nicht gefunden."));
        if (source.getId().equals(board.getId())) throw ApiException.badRequest("Bitte eine andere Vorlage wählen.");
        if (room) setLayout(board, layout(source));
        if (criteria) {
            Set<String> names = new HashSet<>();
            for (ObservationCriterion c : criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId())) {
                names.add(c.getName().toLowerCase(Locale.ROOT));
            }
            for (ObservationCriterion c : criterionRepository.findByBoardIdOrderByPositionAscIdAsc(source.getId())) {
                if (names.add(c.getName().toLowerCase(Locale.ROOT))) addCriterion(board, request(c));
            }
        }
        touch(board);
        return view(board);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ObservationBoard template = boardRepository.findById(id).orElseThrow(() -> ApiException.notFound("Vorlage nicht gefunden."));
        if (template.getCourseId() != null) throw ApiException.badRequest("Der Bogen eines Kurses ist keine Vorlage.");
        boardRepository.delete(template);
    }

    /** Ersetzt alle Kriterien eines Bogens (Vorlagen; Bewertungen hängen nur an Kursbögen). */
    void replaceCriteria(ObservationBoard board, List<CriterionRequest> requests) {
        criterionRepository.deleteAll(criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId()));
        criterionRepository.flush();
        if (requests.size() > MAX_CRITERIA) throw ApiException.badRequest("Höchstens " + MAX_CRITERIA + " Kriterien je Bogen.");
        int position = 0;
        for (CriterionRequest request : requests) {
            ObservationCriterion c = new ObservationCriterion();
            c.setBoardId(board.getId());
            c.setPosition(position++);
            apply(c, request);
            criterionRepository.save(c);
        }
    }

    private TemplateSummary summary(ObservationBoard board, String name) {
        RoomLayout layout = layout(board);
        int tables = (int) layout.items().stream().filter(i -> i.kind() == RoomLayout.Kind.TABLE).count();
        return new TemplateSummary(board.getId(), name, board.getCourseId(), tables, layout.seatIds().size(),
                (int) criterionRepository.countByBoardId(board.getId()));
    }

    // ------------------------------------------------------------------ Kurs beenden / übernehmen

    @EventListener
    @Transactional
    public void onCourseEnding(CourseService.CourseEndingEvent event) {
        boardRepository.deleteByCourseId(event.courseId());
    }

    /**
     * Der alte Kurs gilt nach der Übernahme als beendet: Raum, Kriterien, Plätze und frei eingetragene Namen
     * wandern in den neuen Kurs. Bewertungen und Notizen nur, wenn auch der Fortschritt mitkommt.
     */
    @EventListener
    @Transactional
    public void onCoursePromoted(CourseService.CoursePromotedEvent event) {
        boardRepository.findByCourseId(event.sourceCourseId()).ifPresent(board -> {
            board.setCourseId(event.targetCourseId());
            boardRepository.save(board);
            if (!event.options().keepProgress()) {
                jdbc.update("delete from observation_ratings where person_id in (select id from observation_people where board_id = ?)", board.getId());
                jdbc.update("update observation_people set note = null where board_id = ?", board.getId());
            }
        });
    }

    // ------------------------------------------------------------------ Hilfen

    /** Bogen eines Kurses, bei Bedarf angelegt; neue Kursmitglieder bekommen eine Zeile. */
    ObservationBoard boardFor(Long courseId) {
        Course course = courseService.get(courseId);
        Optional<ObservationBoard> existing = boardRepository.findByCourseId(courseId);
        if (existing.isEmpty()) {
            if (!course.isReadable()) throw ApiException.badRequest("Der Kurs ist beendet.");
            // gleichzeitiges erstes Öffnen in zwei Tabs legt nur einen Bogen an
            jdbc.update("insert into observation_boards (course_id) values (?) on conflict (course_id) do nothing", courseId);
            existing = boardRepository.findByCourseId(courseId);
        }
        ObservationBoard board = existing.orElseThrow();
        Set<Long> members = new HashSet<>(userService.memberIds(courseId));
        // Wer den Kurs verlassen hat, verschwindet samt Bewertungen und Notizen aus dem Bogen
        for (ObservationPerson p : personRepository.findByBoardId(board.getId())) {
            if (p.getUserId() != null && !members.contains(p.getUserId())) personRepository.delete(p);
        }
        for (Long userId : members) {
            jdbc.update("insert into observation_people (board_id, user_id) values (?, ?) on conflict on constraint uq_observation_people_user do nothing",
                    board.getId(), userId);
        }
        return board;
    }

    ObservationBoard writable(Long courseId) {
        if (!courseService.get(courseId).isReadable()) throw ApiException.badRequest("Der Kurs ist beendet.");
        return boardFor(courseId);
    }

    RoomLayout layout(ObservationBoard board) {
        RoomLayout layout = objectMapper.readValue(board.getLayout(), RoomLayout.class);
        return layout.items() == null ? RoomLayout.empty() : layout;
    }

    List<ObservationOption> options(ObservationCriterion criterion) {
        return objectMapper.readValue(criterion.getOptions(), new TypeReference<List<ObservationOption>>() {
        });
    }

    CriterionView criterionView(ObservationCriterion c) {
        return new CriterionView(c.getId(), c.getName(), c.getKind(), clean(c.getMinValue()), clean(c.getMaxValue()), options(c));
    }

    CriterionRequest request(ObservationCriterion c) {
        return new CriterionRequest(c.getName(), c.getKind(), clean(c.getMinValue()), clean(c.getMaxValue()), options(c));
    }

    private ObservationCriterion criterion(ObservationBoard board, Long id) {
        return criterionRepository.findById(id).filter(c -> c.getBoardId().equals(board.getId()))
                .orElseThrow(() -> ApiException.notFound("Kriterium nicht gefunden."));
    }

    ObservationPerson person(ObservationBoard board, Long id) {
        return personRepository.findById(id).filter(p -> p.getBoardId().equals(board.getId()))
                .orElseThrow(() -> ApiException.notFound("Person nicht gefunden."));
    }

    private PersonView personView(ObservationBoard board, ObservationPerson person) {
        User user = person.getUserId() == null ? null : userService.byId(person.getUserId()).orElse(null);
        Map<Long, String> ratings = new LinkedHashMap<>();
        for (ObservationRating r : ratingRepository.findByPersonId(person.getId())) ratings.put(r.getCriterionId(), r.getValue());
        return new PersonView(person.getId(), person.getUserId(), user == null ? null : user.getUsername(),
                user == null ? person.getName() : user.getVisibleName(), person.getSeatId(), person.getNote(), ratings,
                person.getUpdatedAt());
    }

    private void touch(ObservationBoard board) {
        board.setUpdatedAt(LocalDateTime.now());
        boardRepository.save(board);
    }
}
