package de.codepath.backend.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    Optional<KnowledgeArticle> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<KnowledgeArticle> findAllByOrderByPositionAscIdAsc();

    @Query(value = "select slug from deleted_knowledge_articles", nativeQuery = true)
    Set<String> deletedSlugs();

    @Modifying
    @Query(value = "insert into deleted_knowledge_articles (slug) values (:slug) on conflict do nothing", nativeQuery = true)
    void markDeleted(String slug);

    @Modifying
    @Query(value = "delete from deleted_knowledge_articles where slug = :slug", nativeQuery = true)
    void unmarkDeleted(String slug);
}
