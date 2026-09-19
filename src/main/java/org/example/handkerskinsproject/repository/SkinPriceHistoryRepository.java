package org.example.handkerskinsproject.repository;

import org.example.handkerskinsproject.entity.SkinEntity;
import org.example.handkerskinsproject.entity.SkinPriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkinPriceHistoryRepository extends JpaRepository<SkinPriceHistoryEntity, Long> {
    Optional<SkinPriceHistoryEntity> findFirstBySkinOrderByIdDesc(SkinEntity skin);
}