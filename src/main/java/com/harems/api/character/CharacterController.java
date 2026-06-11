package com.harems.api.character;

import com.harems.api.character.dto.CharacterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public List<CharacterResponse> getCharacters() {
        return characterService.getActiveCharacters();
    }

    @GetMapping("/{slug}")
    public CharacterResponse getCharacter(@PathVariable String slug) {
        return characterService.getBySlug(slug);
    }
}
