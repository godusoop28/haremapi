package com.harems.api.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageGenerationRepository extends JpaRepository<ImageGeneration, Long> {

    List<ImageGeneration> findAllByOrderByCreatedAtDesc();
}
