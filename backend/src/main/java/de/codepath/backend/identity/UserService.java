package de.codepath.backend.identity;

import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Öffentliche Schnittstelle des identity-Pakets für Konten. */
@Service
@RequiredArgsConstructor
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Nicht angemeldet.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Konto existiert nicht mehr."));
    }

    public User byUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> ApiException.notFound("Konto nicht gefunden."));
    }

    public Optional<User> byId(Long id) {
        return userRepository.findById(id);
    }

    public List<User> students() {
        return userRepository.findByRoleOrderByUsername(UserRole.STUDENT);
    }

    /** Sperrt die Zeile des Nutzers bis zum Ende der Transaktion (verhindert doppelte Punktevergabe). */
    public void lockForUpdate(Long userId) {
        userRepository.findByIdForUpdate(userId);
    }

    public Optional<Course> courseOf(Long userId) {
        return membershipRepository.findById(userId).map(Membership::getCourse);
    }

    public List<Long> memberIds(Long courseId) {
        return membershipRepository.findUserIdsByCourseId(courseId);
    }

    @Transactional
    public void updateDisplayName(User user, String displayName) {
        if (displayName == null || displayName.isBlank()) return;
        if (displayName.length() > 50) throw ApiException.badRequest("Der Anzeigename ist zu lang.");
        user.setDisplayName(displayName.strip());
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, String newPassword) {
        requireStrongEnough(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(String username, String newPassword) {
        changePassword(byUsername(username), newPassword);
    }

    static void requireStrongEnough(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Das Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen haben.");
        }
    }
}
