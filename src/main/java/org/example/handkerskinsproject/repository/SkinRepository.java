package org.example.handkerskinsproject.repository;

import org.example.handkerskinsproject.entity.SkinEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkinRepository extends JpaRepository<SkinEntity, Long> {
    Optional<SkinEntity> findByMarketHashName(String marketHashName);
}