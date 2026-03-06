package de.codepath.backend.users;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ProfileResponse getProfile(@RequestParam(required = false) String username) {
        User requester = userService.getCurrentUser();
        if (username != null && requester.getRole() == UserRole.ADMIN) {
            User target = userService.getUserByUsername(username);
            return userService.getProfile(target);
        }
        return userService.getProfile(requester);
    }

    @PatchMapping("/profile/display-name")
    public ResponseEntity<Void> updateDisplayName(@RequestBody Map<String, String> body) {
        User user = userService.getCurrentUser();
        userService.updateDisplayName(user, body.get("displayName"));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/profile/password")
    public ResponseEntity<Void> changePassword(@RequestBody Map<String, String> body) {
        User user = userService.getCurrentUser();
        userService.changePassword(user, body.get("password"));
        return ResponseEntity.ok().build();
    }
}
