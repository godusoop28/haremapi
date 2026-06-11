package com.harems.api.usage;

import com.harems.api.character.Character;
import com.harems.api.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usage_limits", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "character_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Builder.Default
    @Column(nullable = false)
    private Integer messagesUsed = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer imagesUsed = 0;

    @Column(nullable = false)
    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;
}
