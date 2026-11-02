package de.codepath.backend.identity;

import de.codepath.backend.platform.events.LeaderboardAudience;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Kursmitglieder als Empfänger kursbezogener Ranglisten-Updates (eine Abfrage je Versand). */
@Component
@RequiredArgsConstructor
class CourseAudience implements LeaderboardAudience {

    private final MembershipRepository membershipRepository;

    @Override
    public Set<String> members(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) return Set.of();
        return new HashSet<>(membershipRepository.findUsernamesByCourseIds(courseIds));
    }
}
