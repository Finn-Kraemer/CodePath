package de.codepath.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MediaRepository extends JpaRepository<MediaFile, String> {

    /** Ohne die Bilddaten – für Listen. */
    interface Info {
        String getKey();
        String getContentType();
        String getOriginalName();
        int getSize();
        Integer getWidth();
        Integer getHeight();
        java.time.LocalDateTime getCreatedAt();
    }

    @Query("select m.key as key, m.contentType as contentType, m.originalName as originalName, m.size as size, "
            + "m.width as width, m.height as height, m.createdAt as createdAt from MediaFile m order by m.createdAt desc")
    List<Info> listInfo();

    @Query("select m.key from MediaFile m where m.key in :keys")
    List<String> existingKeys(Collection<String> keys);

    /** Wird der Schlüssel in irgendeiner Aufgabenversion verwendet (Konfiguration oder Texte)? */
    @Query(value = "select count(*) from task_versions where config::text like concat('%', :key, '%') "
            + "or coalesce(story, '') like concat('%', :key, '%') or coalesce(assignment, '') like concat('%', :key, '%') "
            + "or coalesce(explanation, '') like concat('%', :key, '%') or hints::text like concat('%', :key, '%')",
            nativeQuery = true)
    long countReferences(String key);

    /** Verwendung in Aufgaben – je Bild und Aufgabe eine Zeile; latest = auch in der aktuellen Version. */
    interface TaskReference {
        String getKey();
        Long getTaskId();
        String getTitle();
        String getModuleTitle();
        Boolean getLatest();
    }

    @Query(value = "select r.media_key as key, r.task_id as taskId, r.latest as latest, mo.title as moduleTitle, "
            + "(select x.title from task_versions x where x.task_id = r.task_id order by x.version desc limit 1) as title "
            + "from (select m.media_key, v.task_id, bool_or(v.id = t.current_version_id) as latest "
            + "from media m join task_versions v on (v.config::text like concat('%', m.media_key, '%') "
            + "or coalesce(v.story, '') like concat('%', m.media_key, '%') or coalesce(v.assignment, '') like concat('%', m.media_key, '%') "
            + "or coalesce(v.explanation, '') like concat('%', m.media_key, '%') or v.hints::text like concat('%', m.media_key, '%')) "
            + "join tasks t on t.id = v.task_id group by m.media_key, v.task_id) r "
            + "join tasks t on t.id = r.task_id join modules mo on mo.id = t.module_id order by mo.title, title",
            nativeQuery = true)
    List<TaskReference> taskReferences();
}
