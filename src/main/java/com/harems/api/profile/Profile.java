package com.harems.api.profile;

import com.harems.api.subscription.PlanType;
import com.harems.api.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private boolean ageVerified = true;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private PlanType plan = PlanType.FREE;

    private LocalDateTime planExpiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer imageCredits = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer messagesUsed = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
