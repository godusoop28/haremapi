package com.harems.api.admin;

import com.harems.api.admin.dto.AdminConversationResponse;
import com.harems.api.admin.dto.AdminImageGenerationResponse;
import com.harems.api.admin.dto.AdminUserResponse;
import com.harems.api.common.exception.ResourceNotFoundException;
import com.harems.api.conversation.Conversation;
import com.harems.api.conversation.ConversationRepository;
import com.harems.api.image.ImageGeneration;
import com.harems.api.image.ImageGenerationRepository;
import com.harems.api.message.MessageRepository;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileRepository;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.PlanType;
import com.harems.api.subscription.Subscription;
import com.harems.api.subscription.SubscriptionRepository;
import com.harems.api.subscription.SubscriptionStatus;
import com.harems.api.user.User;
import com.harems.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final SubscriptionRepository subscriptionRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ImageGenerationRepository imageGenerationRepository;

    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toAdminUserResponse)
                .toList();
    }

    public AdminUserResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateUserPlan(Long id, PlanType plan) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Profile profile = profileService.getProfile(user);
        profileService.applyPlan(profile, plan);

        subscriptionRepository.save(Subscription.builder()
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startsAt(LocalDateTime.now())
                .endsAt(profile.getPlanExpiresAt())
                .paymentReference("ADMIN_OVERRIDE")
                .build());

        return toAdminUserResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public List<AdminConversationResponse> getAllConversations() {
        return conversationRepository.findAll().stream()
                .map(this::toAdminConversationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminImageGenerationResponse> getAllImageGenerations() {
        return imageGenerationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminImageGenerationResponse)
                .toList();
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado."));
        return toAdminUserResponse(user, profile);
    }

    private AdminUserResponse toAdminUserResponse(User user, Profile profile) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                profile.getPlan(),
                profile.getPlanExpiresAt(),
                profile.getImageCredits(),
                profile.getMessagesUsed(),
                profile.isAgeVerified(),
                user.getCreatedAt()
        );
    }

    private AdminConversationResponse toAdminConversationResponse(Conversation conversation) {
        long messageCount = messageRepository.findByConversationOrderByCreatedAtAsc(conversation).size();
        return new AdminConversationResponse(
                conversation.getId(),
                conversation.getUser().getEmail(),
                conversation.getCharacter().getSlug(),
                conversation.getCharacter().getName(),
                messageCount,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private AdminImageGenerationResponse toAdminImageGenerationResponse(ImageGeneration generation) {
        return new AdminImageGenerationResponse(
                generation.getId(),
                generation.getUser().getEmail(),
                generation.getCharacter().getSlug(),
                generation.getPrompt(),
                generation.getImageUrl(),
                generation.getStatus().name(),
                generation.getCreatedAt()
        );
    }
}
