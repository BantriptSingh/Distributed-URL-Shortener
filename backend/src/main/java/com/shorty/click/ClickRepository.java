package com.shorty.click;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickRepository extends JpaRepository<ClickEntity, Long> {

    boolean existsByStreamId(String streamId);

    long countByUrlId(Long urlId);

    long countByUrlIdAndBotFalse(Long urlId);

    long countByUrlIdAndBotTrue(Long urlId);
}
