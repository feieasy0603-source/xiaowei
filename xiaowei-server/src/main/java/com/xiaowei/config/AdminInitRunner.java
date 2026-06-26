package com.xiaowei.config;

import com.xiaowei.domain.entity.AdminUser;
import com.xiaowei.domain.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitRunner implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        adminUserRepository.findByUsernameAndEnabledTrue("admin").ifPresentOrElse(
                admin -> {
                    if (!passwordEncoder.matches("admin123", admin.getPasswordHash())) {
                        admin.setPasswordHash(passwordEncoder.encode("admin123"));
                        adminUserRepository.save(admin);
                    }
                },
                () -> {
                    AdminUser admin = new AdminUser();
                    admin.setUsername("admin");
                    admin.setPasswordHash(passwordEncoder.encode("admin123"));
                    admin.setNickname("超级管理员");
                    admin.setRole("SUPER_ADMIN");
                    adminUserRepository.save(admin);
                }
        );
    }
}
