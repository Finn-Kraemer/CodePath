package de.codepath.backend.users;

import de.codepath.backend.features.tasks.PracticeSubmissionRepository;
import de.codepath.backend.features.tasks.SubmissionStatus;
import de.codepath.backend.features.tasks.UserTaskCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserTaskCompletionRepository completionRepository;
    private final PracticeSubmissionRepository practiceSubmissionRepository;
    private final PasswordEncoder passwordEncoder;

    public User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public ProfileResponse getProfile(User user) {
        long completedCount = completionRepository.countByUserId(user.getId());
        long pendingCount = practiceSubmissionRepository.countByUser_IdAndStatus(user.getId(), SubmissionStatus.PENDING);

        return ProfileResponse.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .totalPoints(user.getTotalPoints())
                .completedTasksCount(completedCount)
                .pendingSubmissionsCount(pendingCount)
                .build();
    }

    @Transactional
    public void updateDisplayName(User user, String newDisplayName) {
        if (newDisplayName == null || newDisplayName.isBlank()) return;
        user.setDisplayName(newDisplayName);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
