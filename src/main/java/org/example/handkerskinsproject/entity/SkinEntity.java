package org.example.handkerskinsproject.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skins")
@Getter
@Setter
@NoArgsConstructor
public class SkinEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_hash_name", nullable = false, unique = true)
    private String marketHashName;

    public SkinEntity(String marketHashName) {
        this.marketHashName = marketHashName;
    }
}