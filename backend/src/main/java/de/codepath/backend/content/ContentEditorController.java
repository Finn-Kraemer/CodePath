package de.codepath.backend.content;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Content-Editor für die Lehrkraft (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
public class ContentEditorController {

    private final ContentEditorService editor;
    private final UserService userService;

    @GetMapping
    public List<ContentEditorService.ModuleRow> overview() {
        return editor.overview();
    }

    @PostMapping("/modules")
    public ContentEditorService.ModuleRow createModule(@RequestBody ContentPackage.ModuleFile module) {
        return editor.saveModule(null, module);
    }

    @PutMapping("/modules/{id}")
    public ContentEditorService.ModuleRow updateModule(@PathVariable("id") Long id, @RequestBody ContentPackage.ModuleFile module) {
        return editor.saveModule(id, module);
    }

    public record ReorderRequest(List<Long> taskIds) {
    }

    @PutMapping("/modules/{id}/order")
    public List<ContentEditorService.ModuleRow> reorder(@PathVariable("id") Long moduleId, @RequestBody ReorderRequest request) {
        return editor.reorder(moduleId, request.taskIds());
    }

    @PostMapping("/modules/{id}/tasks")
    public ContentEditorService.TaskEditorView createTask(@PathVariable("id") Long moduleId, @RequestBody ContentEditorService.CreateTaskRequest request) {
        return editor.createTask(moduleId, request, userService.currentUser().getId());
    }

    @GetMapping("/tasks/{id}")
    public ContentEditorService.TaskEditorView task(@PathVariable("id") Long id) {
        return editor.view(id);
    }

    @PutMapping("/tasks/{id}/draft")
    public ContentEditorService.TaskEditorView saveDraft(@PathVariable("id") Long id, @RequestBody ContentPackage.TaskFile draft) {
        return editor.saveDraft(id, draft, userService.currentUser().getId());
    }

    @DeleteMapping("/tasks/{id}/draft")
    public ContentEditorService.TaskEditorView discardDraft(@PathVariable("id") Long id) {
        return editor.discardDraft(id);
    }

    @PostMapping("/run-solution")
    public ContentEditorService.SolutionRun runSolution(@RequestBody ContentEditorService.RunSolutionRequest request) {
        return editor.runSolution(request);
    }

    @PostMapping("/tasks/{id}/check")
    public ContentEditorService.CheckResult check(@PathVariable("id") Long id) {
        return editor.check(id);
    }

    @PostMapping("/tasks/{id}/publish")
    public ContentEditorService.TaskEditorView publish(@PathVariable("id") Long id) {
        return editor.publish(id, userService.currentUser().getId());
    }

    @PostMapping("/tasks/{id}/versions/{versionId}/restore")
    public ContentEditorService.TaskEditorView restore(@PathVariable("id") Long id, @PathVariable("versionId") Long versionId) {
        return editor.restore(id, versionId, userService.currentUser().getId());
    }

    @GetMapping("/tasks/{id}/delete-impact")
    public ContentEditorService.DeleteImpact deleteImpact(@PathVariable("id") Long id) {
        return editor.deleteImpact(id);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, @RequestParam("confirm") String confirm) {
        editor.delete(id, confirm, userService.currentUser().getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{id}/archived")
    public ContentEditorService.TaskEditorView archive(@PathVariable("id") Long id, @RequestParam("value") boolean archived) {
        return editor.archive(id, archived);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=codepath-content.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(editor.exportZip());
    }

    @PostMapping("/import-bundled")
    public ContentImporter.ImportReport importBundled() {
        return editor.importBundled(userService.currentUser().getId());
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContentImporter.ImportReport importZip(@RequestParam("file") MultipartFile file) throws IOException {
        return editor.importZip(file.getInputStream(), userService.currentUser().getId());
    }
}
