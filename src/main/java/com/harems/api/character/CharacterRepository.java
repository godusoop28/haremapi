package com.harems.api.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    Optional<Character> findBySlug(String slug);

    List<Character> findByActiveTrue();

    boolean existsBySlug(String slug);
}
