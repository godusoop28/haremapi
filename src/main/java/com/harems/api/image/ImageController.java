package com.harems.api.image;

import com.harems.api.image.dto.ImageGenerationRequest;
import com.harems.api.image.dto.ImageGenerationResponse;
import com.harems.api.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageGenerationService imageGenerationService;

    @PostMapping("/generate")
    public ImageGenerationResponse generate(@AuthenticationPrincipal UserPrincipal principal,
                                             @Valid @RequestBody ImageGenerationRequest request) {
        return imageGenerationService.generate(principal.getUser(), request);
    }
}
