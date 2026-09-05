package com.shorty.url;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlRepository extends JpaRepository<UrlEntity, Long> {

    @Query("SELECT u FROM UrlEntity u WHERE LOWER(u.shortCode) = LOWER(:code)")
    Optional<UrlEntity> findByShortCodeIgnoreCase(@Param("code") String code);

    @Query("SELECT COUNT(u) > 0 FROM UrlEntity u WHERE LOWER(u.shortCode) = LOWER(:code)")
    boolean existsByShortCodeIgnoreCase(@Param("code") String code);

    Page<UrlEntity> findByOwnerId(Long ownerId, Pageable pageable);
}
