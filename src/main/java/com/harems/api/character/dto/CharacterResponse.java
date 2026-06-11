package com.harems.api.character.dto;

import com.harems.api.character.AccessType;

public record CharacterResponse(
        Long id,
        String slug,
        String name,
        Integer age,
        String archetype,
        AccessType accessType,
        String difficulty,
        String imageUrl,
        String shortDescription,
        String personality,
        String greeting,
        String conquestTip,
        boolean isPremium,
        boolean isVip,
        boolean imageGenerationEnabled
) {
}
