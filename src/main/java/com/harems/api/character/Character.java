package com.harems.api.character;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "characters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private String archetype;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false, length = 1000)
    private String shortDescription;

    @Column(nullable = false, length = 2000)
    private String personality;

    @Column(nullable = false, length = 1000)
    private String greeting;

    @Column(nullable = false, length = 4000)
    private String chatSystemPrompt;

    @Column(nullable = false, length = 2000)
    private String imagePromptBase;

    @Column(nullable = false, length = 1000)
    private String conquestTip;

    @Builder.Default
    @Column(nullable = false)
    private boolean isPremium = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean isVip = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean imageGenerationEnabled = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
