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
}
