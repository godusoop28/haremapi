package com.harems.api.character;

import com.harems.api.character.dto.CharacterResponse;
import com.harems.api.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;

    public List<CharacterResponse> getActiveCharacters() {
        return characterRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    public CharacterResponse getBySlug(String slug) {
        return toResponse(getCharacterEntityBySlug(slug));
    }

    public Character getCharacterEntityBySlug(String slug) {
        return characterRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Personaje no encontrado."));
    }

    public CharacterResponse toResponse(Character character) {
        return new CharacterResponse(
                character.getId(),
                character.getSlug(),
                character.getName(),
                character.getAge(),
                character.getArchetype(),
                character.getAccessType(),
                character.getDifficulty(),
                character.getImageUrl(),
                character.getShortDescription(),
                character.getPersonality(),
                character.getGreeting(),
                character.getConquestTip(),
                character.isPremium(),
                character.isVip(),
                character.isImageGenerationEnabled()
        );
    }
}
