package de.codepath.backend.progress;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Antworten an das Frontend für Schüler. Enthalten nie eine Lösung, bevor sie gelöst oder aufgedeckt ist. */
public final class TaskViews {

    private TaskViews() {
    }

    public record ModuleSummary(Long id, String slug, String title, String description, String icon,
                                boolean isUnlocked, boolean isOpen, LocalDateTime availableUntil,
                                int totalTasks, int completedTasks, long pointsEarned, int maxPoints) {
    }

    public record TaskSummary(Long id, String slug, String title, String type, String difficulty, int points,
                              String status, boolean isCompleted, boolean isLocked, long pointsEarned) {
    }

    public record ModuleDetail(ModuleSummary module, List<TaskSummary> tasks) {
    }

    public record TaskDetail(
            Long id, String slug, String title, String type, String difficulty, int points, int version,
            String moduleSlug, String moduleTitle,
            String learningGoal, String story, String assignment, String knowledgeArticle,
            Map<String, Object> config,
            String status, boolean isCompleted, boolean isLocked, boolean isOpen,
            int failedAttempts, Integer maxAttempts, Integer attemptsLeft,
            int hintsTotal, List<String> hints, int hintPenaltyPercent, int firstTryBonusPercent,
            boolean revealAllowed, boolean solutionRevealed,
            Map<String, Object> solution, String explanation,
            long pointsEarned,
            Map<String, Object> lastAnswer, String lastFeedback,
            String reviewComment,
            LocalDateTime deadline) {
    }

    public record Profile(String username, String displayName, String role, String courseName, long totalPoints,
                          long completedTasksCount, long pendingSubmissionsCount) {
    }

    public record LeaderboardEntry(String username, String displayName, long points, int rank) {
    }

    public record Leaderboard(List<LeaderboardEntry> topThree, List<LeaderboardEntry> entries, LeaderboardEntry ownEntry) {
    }
}
