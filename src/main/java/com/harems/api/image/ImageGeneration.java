package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "image_generations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(length = 500)
    private String userPrompt;

    @Column(nullable = false, length = 3000)
    private String prompt;

    private String imageUrl;

    @Column(length = 50)
    private String provider;

    @Column(length = 200)
    private String providerJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status;

    @Column(length = 1000)
    private String errorMessage;

    @Builder.Default
    @Column(nullable = false)
    private Integer creditsCost = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
