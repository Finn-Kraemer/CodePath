package de.codepath.backend.progress;

import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.events.RealtimeUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Punkte-Journal: jede Änderung ist eine eigene Buchung, der Stand ist die Summe. */
@Service
@RequiredArgsConstructor
public class PointsService {

    private final PointsEntryRepository repository;
    private final AuditService auditService;
    private final RealtimeUpdateService realtime;
    private final UserService userService;

    /** Bucht Punkte für das Lösen einer Aufgabe – höchstens einmal je Schüler und Aufgabe. */
    @Transactional
    public boolean awardOnce(Long userId, Long taskId, int amount, PointsReason reason, String note, Long actorId) {
        boolean booked = repository.insertOnce(userId, taskId, amount, reason.name(), note, actorId) == 1;
        if (booked) {
            auditService.log("POINTS_" + reason.name(), "user:" + userId, Map.of("task", taskId, "amount", amount));
            leaderboardChanged(userId);
        }
        return booked;
    }

    /** Korrektur der Lehrkraft als eigene Buchung (kann negativ sein). */
    @Transactional
    public void correct(Long userId, Long taskId, int delta, String note, Long actorId) {
        if (delta == 0) return;
        repository.insertCorrection(userId, taskId, delta, note, actorId);
        auditService.log("POINTS_CORRECTION", "user:" + userId, Map.of("task", taskId == null ? "-" : taskId, "amount", delta, "note", note == null ? "" : note));
        leaderboardChanged(userId);
    }

    /**
     * Bucht Bonuspunkte für eine erreichte Bonus-Station auf einem CodePath – höchstens einmal je
     * Schüler und aktiver Bonus-Station.
     */
    @Transactional
    public boolean awardBonusOnce(Long userId, Long pathId, String nodeId, int amount, String note) {
        if (userId == null || pathId == null || nodeId == null || amount <= 0) return false;
        boolean booked = repository.insertBonusOnce(userId, pathId, nodeId, amount, note) == 1;
        if (booked) {
            auditService.log("POINTS_PATH_BONUS", "user:" + userId,
                    Map.of("path", pathId, "node", nodeId, "amount", amount, "note", note == null ? "" : note));
            leaderboardChanged(userId);
        }
        return booked;
    }

    public Set<String> activeBonusNodeIds(Long userId, Long pathId) {
        if (userId == null || pathId == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (PointsEntry entry : repository.findActiveBonuses(userId, pathId)) {
            if (entry.getNodeId() != null) result.add(entry.getNodeId());
        }
        return result;
    }

    /**
     * Storniert einen bereits gebuchten CodePath-Bonus per Gegenbuchung im Punkte-Journal;
     * der ursprüngliche Eintrag wird als storniert markiert, sodass ein erneutes Erreichen
     * wieder buchen kann und das Journal vollständig nachvollziehbar bleibt.
     */
    @Transactional
    public void cancelBonus(Long userId, Long pathId, String nodeId, String note, Long actorId) {
        if (userId == null || pathId == null || nodeId == null) return;
        Optional<PointsEntry> active = repository.findActiveBonus(userId, pathId, nodeId);
        if (active.isEmpty()) return;
        PointsEntry entry = active.get();
        repository.markBonusCancelled(entry.getId());
        int delta = -entry.getAmount();
        String cancelNote = note != null && !note.isBlank() ? note : "Storno " + (entry.getNote() != null ? entry.getNote() : "Bonus");
        repository.insertBonusCancellation(userId, pathId, nodeId, delta, cancelNote, actorId);
        auditService.log("POINTS_BONUS_CANCELLED", "user:" + userId,
                Map.of("path", pathId, "node", nodeId, "amount", delta, "note", cancelNote));
        leaderboardChanged(userId);
    }

    // Zählt jede bestätigte Punkteänderung; zwischengespeicherte Ranglisten verfallen damit
    private final AtomicLong generation = new AtomicLong();

    /** Stand des Punkte-Journals für Caches: ändert sich nach jedem Commit, der Punkte bucht. */
    public long generation() {
        return generation.get();
    }

    /** Nur der Kurs der Person muss neu laden (und die Lehrkräfte). */
    private void leaderboardChanged(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    generation.incrementAndGet();
                }
            });
        } else {
            generation.incrementAndGet();
        }
        realtime.publishLeaderboardUpdate(userService.courseIdOf(userId));
    }

    public boolean hasAward(Long userId, Long taskId) {
        return repository.existsByUserIdAndTaskIdAndReasonIn(userId, taskId,
                List.of(PointsReason.SOLVED, PointsReason.PARTIAL, PointsReason.REVIEW));
    }

    public long total(Long userId) {
        return repository.total(userId);
    }

    public long forTask(Long userId, Long taskId) {
        return repository.forTask(userId, taskId);
    }

    public Map<Long, Long> totals(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : repository.totals(userIds)) {
            result.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    public Map<Long, Long> byTask(Long userId) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : repository.byTask(userId)) {
            result.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    public List<PointsEntry> journal(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
