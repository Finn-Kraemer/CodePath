package de.codepath.backend.features.admin;

import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Serverseitige Sessions lassen sich gezielt beenden (ADR-3) – etwa am Ende eines Projekttags.
 */
@Service
@RequiredArgsConstructor
public class SessionAdminService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final UserRepository userRepository;

    public int logoutAllStudents() {
        int count = 0;
        for (User student : userRepository.findByRole(UserRole.STUDENT)) {
            count += logout(student.getUsername());
        }
        return count;
    }

    public int logout(String username) {
        var sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().forEach(sessionRepository::deleteById);
        return sessions.size();
    }
}
