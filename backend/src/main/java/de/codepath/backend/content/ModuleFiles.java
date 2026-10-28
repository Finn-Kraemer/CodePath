package de.codepath.backend.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Umwandlung zwischen {@link Module} und module.json, mit Prüfung von Berufsfeld und Weiterlern-Links. */
final class ModuleFiles {

    static final int MAX_LINKS = 10;

    private ModuleFiles() {
    }

    static ContentPackage.ModuleFile toFile(Module m) {
        List<ContentPackage.Link> links = m.getLearnMore() == null ? List.of() : m.getLearnMore().stream()
                .map(l -> new ContentPackage.Link(l.get("title"), l.get("url"))).toList();
        return new ContentPackage.ModuleFile(m.getSlug(), m.getTitle(), m.getDescription(), m.getIcon(), m.getSortOrder(),
                m.getCareer(), links);
    }

    /** Was an Berufsfeld und Links nicht stimmt; leer, wenn alles passt. */
    static List<String> problems(ContentPackage.ModuleFile file) {
        List<String> problems = new ArrayList<>();
        if (file.career() != null && file.career().length() > 300) problems.add("Das Berufsfeld ist zu lang (höchstens 300 Zeichen).");
        List<ContentPackage.Link> links = file.learnMore() == null ? List.of() : file.learnMore();
        if (links.size() > MAX_LINKS) problems.add("Höchstens " + MAX_LINKS + " Links zum Weiterlernen.");
        for (ContentPackage.Link link : links) {
            if (link == null || link.title() == null || link.title().isBlank() || link.title().length() > 200) {
                problems.add("Jeder Link braucht einen Titel (höchstens 200 Zeichen).");
            } else if (link.url() == null || !link.url().strip().matches("https?://\\S+") || link.url().length() > 500) {
                problems.add("Der Link „" + link.title() + "“ muss mit http:// oder https:// beginnen.");
            }
        }
        return problems;
    }

    /** Übernimmt alle Felder (vorher {@link #problems} prüfen). */
    static void apply(Module module, ContentPackage.ModuleFile file) {
        module.setSlug(file.slug());
        module.setTitle(file.title());
        module.setDescription(file.description());
        module.setIcon(file.icon());
        module.setSortOrder(file.sortOrder() != null ? file.sortOrder() : 0);
        module.setCareer(file.career() == null || file.career().isBlank() ? null : file.career().strip());
        module.setLearnMore(links(file));
    }

    static List<Map<String, String>> links(ContentPackage.ModuleFile file) {
        List<Map<String, String>> links = new ArrayList<>();
        if (file.learnMore() == null) return links;
        for (ContentPackage.Link link : file.learnMore()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("title", link.title().strip());
            entry.put("url", link.url().strip());
            links.add(entry);
        }
        return links;
    }

    /** Stimmt das Modul mit der Datei überein? (für den Import-Bericht) */
    static boolean same(Module module, ContentPackage.ModuleFile file) {
        Module probe = new Module();
        apply(probe, file);
        return Objects.equals(module.getTitle(), probe.getTitle())
                && Objects.equals(module.getDescription(), probe.getDescription())
                && Objects.equals(module.getIcon(), probe.getIcon())
                && Objects.equals(module.getSortOrder(), probe.getSortOrder())
                && Objects.equals(module.getCareer(), probe.getCareer())
                && Objects.equals(module.getLearnMore(), probe.getLearnMore());
    }
}
