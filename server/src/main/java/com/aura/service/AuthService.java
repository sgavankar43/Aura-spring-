package com.aura.service;

import com.aura.dto.*;
import com.aura.entity.ProjectMember;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.ProjectMemberRepository;
import com.aura.repository.UserRepository;
import com.aura.security.JwtTokenProvider;
import com.aura.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .name(request.getName().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ADMIN")
                .build();

        user = userRepository.save(user);

        // Auto-link and activate any pending project member invites for this email
        List<ProjectMember> pendingMemberships = projectMemberRepository.findByEmail(email);
        for (ProjectMember member : pendingMemberships) {
            member.setUser(user);
            member.setStatus("ACTIVE");
            member.setInviteToken(null);
            projectMemberRepository.save(member);
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(mapToUserDto(user))
                .message("User registered successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public InviteInfoDto getInviteInfo(String token) {
        ProjectMember member = projectMemberRepository.findByInviteToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invite link is invalid or has already been accepted"));

        return InviteInfoDto.builder()
                .token(token)
                .projectId(member.getProject().getId())
                .projectName(member.getProject().getName())
                .email(member.getEmail())
                .name(member.getName())
                .role(member.getRole())
                .status(member.getStatus())
                .build();
    }

    @Transactional
    public AuthResponse acceptInvite(AcceptInviteRequest request) {
        ProjectMember member = projectMemberRepository.findByInviteToken(request.getToken())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invite link is invalid or has already been accepted"));

        String email = member.getEmail().trim().toLowerCase();
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isEmpty()) {
            String displayName = request.getName() != null && !request.getName().isBlank()
                    ? request.getName().trim()
                    : (member.getName() != null ? member.getName() : email.split("@")[0]);

            user = User.builder()
                    .email(email)
                    .name(displayName)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role("ADMIN")
                    .build();
            user = userRepository.save(user);
        } else {
            user = existingUser.get();
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            if (request.getName() != null && !request.getName().isBlank()) {
                user.setName(request.getName().trim());
            }
            user = userRepository.save(user);
        }

        // Activate all project memberships matching this email
        List<ProjectMember> memberships = projectMemberRepository.findByEmail(email);
        for (ProjectMember m : memberships) {
            m.setUser(user);
            m.setStatus("ACTIVE");
            m.setInviteToken(null);
            projectMemberRepository.save(m);
        }

        String jwtToken = tokenProvider.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(jwtToken)
                .user(mapToUserDto(user))
                .message("Invite accepted successfully. Welcome to Aura!")
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(mapToUserDto(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return mapToUserDto(user);
    }

    public UserDto mapToUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
