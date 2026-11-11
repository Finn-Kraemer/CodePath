package de.codepath.backend.worksheet;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

/** Aufgabenblätter – nur für die Lehrkraft (/api/admin/**). */
@RestController
@RequiredArgsConstructor
public class WorksheetController {

    private final WorksheetService service;

    @GetMapping("/api/admin/worksheets")
    public List<WorksheetService.WorksheetSummary> all() {
        return service.all();
    }

    @PostMapping("/api/admin/worksheets")
    public WorksheetService.WorksheetSummary create(@RequestBody Map<String, String> body) {
        return service.create(body.get("title"));
    }

    @GetMapping("/api/admin/worksheets/{id}")
    public WorksheetService.WorksheetDetail detail(@PathVariable("id") Long id) {
        return service.detail(id);
    }

    @PutMapping("/api/admin/worksheets/{id}")
    public WorksheetService.WorksheetDetail update(@PathVariable("id") Long id, @RequestBody WorksheetService.WorksheetUpdate update) {
        return service.update(id, update);
    }

    @PostMapping("/api/admin/worksheets/{id}/copy")
    public WorksheetService.WorksheetSummary duplicate(@PathVariable("id") Long id) {
        return service.duplicate(id);
    }

    @DeleteMapping("/api/admin/worksheets/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PDF des gespeicherten Blatts.
     *
     * @param variant  {@code student} (Standard) oder {@code solution}
     * @param download {@code true}: als Datei speichern, sonst im Browser anzeigen
     */
    @GetMapping(value = "/api/admin/worksheets/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable("id") Long id,
                                      @RequestParam(name = "variant", defaultValue = "student") String variant,
                                      @RequestParam(name = "download", defaultValue = "false") boolean download) {
        WorksheetRenderer.Variant v = variant(variant);
        return file(service.pdf(id, v), v, download);
    }

    /** Vorschau des aktuellen Stands im Editor, ohne zu speichern. */
    @PostMapping(value = "/api/admin/worksheets/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@RequestBody WorksheetService.WorksheetUpdate draft,
                                          @RequestParam(name = "variant", defaultValue = "student") String variant) {
        WorksheetRenderer.Variant v = variant(variant);
        return file(service.preview(draft, v), v, false);
    }

    private static WorksheetRenderer.Variant variant(String value) {
        return "solution".equalsIgnoreCase(value) ? WorksheetRenderer.Variant.SOLUTION : WorksheetRenderer.Variant.STUDENT;
    }

    private static ResponseEntity<byte[]> file(WorksheetService.Pdf pdf, WorksheetRenderer.Variant variant, boolean download) {
        String name = fileName(pdf.title()) + (variant == WorksheetRenderer.Variant.SOLUTION ? "_Loesung" : "") + ".pdf";
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.data());
    }

    /** Dateiname aus dem Titel: Umlaute umschreiben, nur Buchstaben, Ziffern, - und _. */
    static String fileName(String title) {
        String value = title.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae")
                .replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        value = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        value = value.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (value.length() > 80) value = value.substring(0, 80);
        return value.isEmpty() ? "Aufgabenblatt" : value;
    }
}
