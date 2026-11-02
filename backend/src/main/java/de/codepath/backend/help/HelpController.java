package de.codepath.backend.help;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Hilfe-Ruf „Ich hänge fest“: Schüler unter /api/help, Lehrkräfte unter /api/admin/help. */
@RestController
@RequiredArgsConstructor
public class HelpController {

    private final HelpService helpService;
    private final UserService userService;

    /** Eigener offener Ruf mit Position in der Warteschlange (ohne Namen anderer). */
    @GetMapping("/api/help")
    public HelpService.HelpState mine() {
        return helpService.mine(userService.currentUser());
    }

    /** Hilfe rufen oder den offenen Ruf aktualisieren. Der Entwurf wird nicht bewertet und zählt nicht als Versuch. */
    @PostMapping("/api/help")
    public HelpService.HelpState call(@RequestBody HelpService.HelpCall call) {
        return helpService.call(userService.currentUser(), call);
    }

    /** „Hat sich erledigt“ */
    @DeleteMapping("/api/help")
    public HelpService.HelpState cancel() {
        return helpService.cancel(userService.currentUser());
    }

    @GetMapping("/api/admin/help")
    public HelpService.Queue queue() {
        return helpService.queue();
    }

    @PostMapping("/api/admin/help/{id}/claim")
    public HelpService.QueueEntry claim(@PathVariable("id") Long id) {
        return helpService.claim(userService.currentUser(), id);
    }

    @PostMapping("/api/admin/help/{id}/resolve")
    public HelpService.QueueEntry resolve(@PathVariable("id") Long id) {
        return helpService.resolve(userService.currentUser(), id);
    }

    @PostMapping("/api/admin/help/{id}/message")
    public HelpService.QueueEntry message(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        return helpService.message(userService.currentUser(), id, body.get("text"));
    }
}
