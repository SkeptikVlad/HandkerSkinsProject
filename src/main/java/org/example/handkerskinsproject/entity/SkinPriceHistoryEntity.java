package org.example.handkerskinsproject.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skin_price_history")
@Getter
@Setter
@NoArgsConstructor
public class SkinPriceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skin_id", nullable = false)
    private SkinEntity skin;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "top_order")
    private BigDecimal topOrder;

    @Column(name = "volume")
    private Integer volume;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SkinPriceHistoryEntity(SkinEntity skin, BigDecimal minPrice, BigDecimal topOrder, Integer volume) {
        this.skin = skin;
        this.minPrice = minPrice;
        this.topOrder = topOrder;
        this.volume = volume;
    }
}