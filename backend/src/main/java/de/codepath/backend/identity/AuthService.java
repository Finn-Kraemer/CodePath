package de.codepath.backend.identity;

import de.codepath.backend.platform.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login mit serverseitiger Session (ADR-3). Registrierung nur mit Beitrittscode eines aktiven Kurses.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CourseService courseService;
    private final UserService userService;
    private final LoginAttemptLimiter limiter;

    @Transactional
    public AuthResponse register(AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Course course = courseService.activeCourseByJoinCode(request.joinCode());
        UserService.requireStrongEnough(request.password());
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("Dieser Benutzername ist schon vergeben.");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.STUDENT);
        user.setDisplayName(request.username());
        userRepository.save(user);
        courseService.join(user, course);

        return login(request, httpRequest, httpResponse);
    }

    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String ip = httpRequest.getRemoteAddr();
        if (limiter.isBlocked(request.username(), ip)) {
            throw ApiException.tooManyRequests("Zu viele Fehlversuche. Bitte in einer Minute erneut versuchen.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            limiter.recordFailure(request.username(), ip);
            throw e;
        }
        limiter.recordSuccess(request.username());

        // Neue Session-ID nach dem Login verhindert Session Fixation
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return toResponse(userRepository.findByUsername(authentication.getName()).orElseThrow());
    }

    public AuthResponse toResponse(User user) {
        String courseName = userService.courseOf(user.getId()).map(Course::getName).orElse(null);
        return new AuthResponse(user.getUsername(), user.getRole().name(), user.getVisibleName(), courseName);
    }
}
