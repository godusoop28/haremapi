package com.harems.api.admin;

import com.harems.api.admin.dto.AdminConversationResponse;
import com.harems.api.admin.dto.AdminImageGenerationResponse;
import com.harems.api.admin.dto.AdminUserResponse;
import com.harems.api.admin.dto.UpdateUserPlanRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints. Access is restricted to users with role ADMIN
 * (see SecurityConfig: "/admin/**" requires ROLE_ADMIN).
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public List<AdminUserResponse> getUsers() {
        return adminService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public AdminUserResponse getUser(@PathVariable Long id) {
        return adminService.getUser(id);
    }

    @PutMapping("/users/{id}/plan")
    public AdminUserResponse updateUserPlan(@PathVariable Long id,
                                             @Valid @RequestBody UpdateUserPlanRequest request) {
        return adminService.updateUserPlan(id, request.plan());
    }

    @GetMapping("/conversations")
    public List<AdminConversationResponse> getConversations() {
        return adminService.getAllConversations();
    }

    @GetMapping("/image-generations")
    public List<AdminImageGenerationResponse> getImageGenerations() {
        return adminService.getAllImageGenerations();
    }
}
