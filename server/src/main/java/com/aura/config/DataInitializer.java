package com.aura.config;

import com.aura.entity.Environment;
import com.aura.entity.Feature;
import com.aura.entity.FeatureState;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.repository.EnvironmentRepository;
import com.aura.repository.FeatureRepository;
import com.aura.repository.FeatureStateRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureRepository featureRepository;
    private final FeatureStateRepository featureStateRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FeatureRepository featureRepository,
            FeatureStateRepository featureStateRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.featureRepository = featureRepository;
        this.featureStateRepository = featureStateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Seed Admin User
        User admin = userRepository.findByEmail("admin@aura.dev").orElseGet(() -> {
            User newAdmin = User.builder()
                    .email("admin@aura.dev")
                    .name("System Administrator")
                    .password(passwordEncoder.encode("AdminPassword123!"))
                    .role("ADMIN")
                    .build();
            log.info("Created Seed User: Admin (email: admin@aura.dev, role: ADMIN)");
            return userRepository.save(newAdmin);
        });

        // Seed Developer User
        userRepository.findByEmail("developer@aura.dev").orElseGet(() -> {
            User newDeveloper = User.builder()
                    .email("developer@aura.dev")
                    .name("Developer User")
                    .password(passwordEncoder.encode("DevPassword123!"))
                    .role("DEVELOPER")
                    .build();
            log.info("Created Seed User: Developer (email: developer@aura.dev, role: DEVELOPER)");
            return userRepository.save(newDeveloper);
        });

        // Seed Initial Project if none exist
        if (projectRepository.count() == 0) {
            String apiKey = "aura_pk_live_" + UUID.randomUUID().toString().replace("-", "");
            Project project = Project.builder()
                    .name("Aura Platform Core")
                    .description("Core feature flags and remote configs for main platform")
                    .apiKey(apiKey)
                    .user(admin)
                    .build();
            project = projectRepository.save(project);

            Environment dev = environmentRepository.save(Environment.builder()
                    .project(project)
                    .name("Development")
                    .slug("development")
                    .color("#3B82F6")
                    .sortOrder(1)
                    .build());

            Environment staging = environmentRepository.save(Environment.builder()
                    .project(project)
                    .name("Staging")
                    .slug("staging")
                    .color("#F59E0B")
                    .sortOrder(2)
                    .build());

            Environment prod = environmentRepository.save(Environment.builder()
                    .project(project)
                    .name("Production")
                    .slug("production")
                    .color("#10B981")
                    .sortOrder(3)
                    .build());

            // Add sample feature flags
            createFeatureWithStates(project, "dark-mode", "Dark Mode UI Theme", "Enables sleek dark mode theme across applications", true, List.of(dev, staging, prod), List.of(true, true, true));
            createFeatureWithStates(project, "ai-copilot-beta", "AI Copilot Beta", "Experimental AI code assistance integration", false, List.of(dev, staging, prod), List.of(true, true, false));
            createFeatureWithStates(project, "fast-checkout-v2", "Fast Checkout v2", "One-click accelerated checkout flow", false, List.of(dev, staging, prod), List.of(true, false, false));

            log.info("Created seed project 'Aura Platform Core' with default environments and feature flags");
        }
    }

    private void createFeatureWithStates(Project project, String key, String name, String description, boolean defaultEnabled, List<Environment> envs, List<Boolean> enabledList) {
        Feature feature = featureRepository.save(Feature.builder()
                .project(project)
                .key(key)
                .name(name)
                .description(description)
                .defaultEnabled(defaultEnabled)
                .build());

        for (int i = 0; i < envs.size(); i++) {
            featureStateRepository.save(FeatureState.builder()
                    .feature(feature)
                    .environment(envs.get(i))
                    .enabled(enabledList.get(i))
                    .build());
        }
    }
}
