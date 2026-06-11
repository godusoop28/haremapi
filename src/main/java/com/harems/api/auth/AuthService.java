package com.harems.api.auth;

import com.harems.api.auth.dto.AuthResponse;
import com.harems.api.auth.dto.LoginRequest;
import com.harems.api.auth.dto.RegisterRequest;
import com.harems.api.auth.dto.UserResponse;
import com.harems.api.common.exception.BadRequestException;
import com.harems.api.common.exception.EmailAlreadyExistsException;
import com.harems.api.common.exception.InvalidCredentialsException;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileRepository;
import com.harems.api.security.JwtService;
import com.harems.api.security.UserPrincipal;
import com.harems.api.subscription.PlanType;
import com.harems.api.user.Role;
import com.harems.api.user.User;
import com.harems.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.admin.bootstrap-email}")
    private String adminBootstrapEmail;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.ageVerified() == null || !request.ageVerified()) {
            throw new BadRequestException("Debes confirmar que eres mayor de edad para registrarte.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Este correo ya está registrado.");
        }

        Role role = adminBootstrapEmail.equalsIgnoreCase(request.email()) ? Role.ADMIN : Role.USER;

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .build();
        user = userRepository.save(user);

        Profile profile = Profile.builder()
                .user(user)
                .ageVerified(true)
                .plan(PlanType.FREE)
                .planExpiresAt(null)
                .imageCredits(0)
                .messagesUsed(0)
                .build();
        profileRepository.save(profile);

        String token = jwtService.generateToken(new UserPrincipal(user), Map.of("role", user.getRole().name()));

        return new AuthResponse(token, toUserResponse(user, profile));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException("Credenciales inválidas.");
        }

        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas."));

        String token = jwtService.generateToken(new UserPrincipal(user), Map.of("role", user.getRole().name()));

        return new AuthResponse(token, toUserResponse(user, profile));
    }

    public UserResponse getCurrentUser(User user) {
        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas."));

        return toUserResponse(user, profile);
    }

    private UserResponse toUserResponse(User user, Profile profile) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                profile.getPlan(),
                profile.isAgeVerified()
        );
    }
}
