package de.codepath.backend.worksheet;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Renderer ohne Datenbank: Randfälle dürfen nie zu einem Fehler führen. */
public class WorksheetRendererTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static String text(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) sb.append(extractor.getTextFromPage(page)).append('\n');
            return sb.toString();
        }
    }

    static byte[] render(Object content, WorksheetRenderer.Variant variant) {
        JsonNode node = JSON.valueToTree(content);
        return WorksheetRenderer.render("Test 🚀", node, variant, key -> null);
    }

    @Test
    void incompleteAndOddBlocksStillRender() throws Exception {
        Map<String, Object> content = Map.of(
                "settings", Map.of("pageSize", "A5", "orientation", "landscape", "font", "serif", "fontSize", 99,
                        "lineSpacing", "breit", "accent", "kaputt", "nameField", false, "numberLabel", "Nr."),
                "blocks", List.of(
                        Map.of("type", "task"),
                        Map.of("type", "choice", "options", List.of(Map.of(), Map.of("text", "B", "correct", "ja"))),
                        Map.of("type", "gap", "text", "Nur [[eine]] Lücke → fertig", "wordBank", true),
                        Map.of("type", "truefalse"),
                        Map.of("type", "matching", "pairs", List.of(Map.of("left", "x"))),
                        Map.of("type", "order", "items", List.of(Map.of("text", "eins"), "zwei")),
                        Map.of("type", "table", "rows", List.of(List.of(), List.of("a", 1, true))),
                        Map.of("type", "table"),
                        Map.of("type", "code", "code", "\tif x:\n\t\treturn 1"),
                        Map.of("type", "image", "key", "0".repeat(64) + ".png"),
                        Map.of("type", "image"),
                        Map.of("type", "box", "style", "unbekannt"),
                        Map.of("type", "space", "size", 1000),
                        Map.of("type", "heading", "level", 7, "text", "Ende 😀")));
        for (WorksheetRenderer.Variant variant : WorksheetRenderer.Variant.values()) {
            String text = text(render(content, variant));
            assertTrue(text.contains("Test 🚀"), "Emoji kommt aus der Ausweichschrift");
            assertTrue(text.contains("Nr. 1"));
            assertTrue(text.contains("[Bild nicht verfügbar]"));
            assertTrue(text.contains("→"));
            assertFalse(text.contains("Name:"));
        }
    }

    @Test
    void emptyContentGivesOnePage() throws Exception {
        try (PdfReader reader = new PdfReader(render(Map.of(), WorksheetRenderer.Variant.STUDENT))) {
            assertEquals(1, reader.getNumberOfPages());
        }
    }

    public static int pages(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            return reader.getNumberOfPages();
        }
    }

    @Test
    void noEmptyPagesFromTrailingLayoutBlocks() throws Exception {
        Map<String, Object> task = Map.of("type", "task", "prompt", "Erste Seite", "size", 2);
        // Umbruch, Trennlinie und Freiraum am Ende ergeben keine leere Seite
        assertEquals(1, pages(render(Map.of("blocks", List.of(task, Map.of("type", "pagebreak"), Map.of("type", "divider"),
                Map.of("type", "space", "size", 3), Map.of("type", "pagebreak"))), WorksheetRenderer.Variant.STUDENT)));
        // Folgt nur Inhalt der anderen Fassung, endet das Schülerblatt nach Seite 1, die Lösung nicht
        Map<String, Object> content = Map.of("blocks", List.of(task, Map.of("type", "pagebreak"),
                Map.of("type", "text", "text", "Erwartungshorizont", "show", "solution")));
        assertEquals(1, pages(render(content, WorksheetRenderer.Variant.STUDENT)));
        assertEquals(2, pages(render(content, WorksheetRenderer.Variant.SOLUTION)));
        // Mehrere Umbrüche hintereinander und Trennlinie oben auf der Seite: eine neue Seite, ohne Linie davor
        assertEquals(2, pages(render(Map.of("blocks", List.of(task, Map.of("type", "pagebreak"), Map.of("type", "pagebreak"),
                Map.of("type", "divider"), Map.of("type", "text", "text", "Zweite Seite"))), WorksheetRenderer.Variant.STUDENT)));
    }

    @Test
    void shuffledOrderIsTheSameInBothVariants() throws Exception {
        List<Map<String, Object>> pairs = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) pairs.add(Map.of("left", "L" + i, "right", "Rechts" + i));
        Map<String, Object> content = Map.of("blocks", List.of(
                Map.of("type", "text", "text", "Nur Lösung", "show", "solution"),
                Map.of("type", "matching", "prompt", "Ordne zu.", "pairs", pairs)));
        String student = text(render(content, WorksheetRenderer.Variant.STUDENT));
        String solution = text(render(content, WorksheetRenderer.Variant.SOLUTION));
        List<String> orderStudent = new java.util.ArrayList<>(pairs.stream().map(p -> (String) p.get("right")).toList());
        List<String> orderSolution = new java.util.ArrayList<>(orderStudent);
        orderStudent.sort(java.util.Comparator.comparingInt(student::indexOf));
        orderSolution.sort(java.util.Comparator.comparingInt(solution::indexOf));
        assertEquals(orderStudent, orderSolution);
    }

    @Test
    void shuffleIsStableAndNeverTheIdentity() {
        for (int n = 2; n < 8; n++) {
            for (long seed = 0; seed < 50; seed++) {
                List<Integer> order = WorksheetRenderer.shuffled(n, seed);
                assertEquals(order, WorksheetRenderer.shuffled(n, seed));
                boolean identity = true;
                for (int i = 0; i < n; i++) identity &= order.get(i) == i;
                assertFalse(identity, "n=" + n + " seed=" + seed);
            }
        }
        assertEquals("2,5", WorksheetRenderer.points(2.5).replace(" ", ""));
        assertEquals("3", WorksheetRenderer.points(3));
    }

    @Test
    void fileNamesAreSafe() {
        assertEquals("Uebung_Binaerzahlen_Klasse_8b", WorksheetController.fileName("Übung: Binärzahlen / Klasse 8b"));
        assertEquals("Aufgabenblatt", WorksheetController.fileName("🚀"));
    }
}
