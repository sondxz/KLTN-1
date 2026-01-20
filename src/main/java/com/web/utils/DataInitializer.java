package com.web.utils;

import com.web.entity.Authority;
import com.web.entity.Families;
import com.web.entity.User;
import com.web.repository.AuthorityRepository;
import com.web.repository.FamiliesRepository;
import com.web.repository.UserRepository;
import com.web.utils.SlugGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private FamiliesRepository familiesRepository;

    @Override
    public void run(String... args) throws Exception {
        String password = "admin";
        String email = "admin@gmail.com";
        if(authorityRepository.findByName(Contains.ROLE_ADMIN) == null){
            Authority authority = new Authority();
            authority.setName(Contains.ROLE_ADMIN);
            authorityRepository.save(authority);
        }
        if(authorityRepository.findByName(Contains.ROLE_USER) == null){
            Authority authority = new Authority();
            authority.setName(Contains.ROLE_USER);
            authorityRepository.save(authority);
        }
        if(authorityRepository.findByName(Contains.ROLE_EXPERT) == null){
            Authority authority = new Authority();
            authority.setName(Contains.ROLE_EXPERT);
            authorityRepository.save(authority);
        }
        if (!userRepository.findByEmail(email).isPresent()) {
            User user = new User();
            user.setEmail(email);
            user.setUsername(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setActived(true);
            user.setActivation_key(null); // Đảm bảo không có activation key
            Authority authority = new Authority();
            authority.setName(Contains.ROLE_ADMIN);
            user.setAuthorities(authority);
            user.setFullname("ADMIN");
            userRepository.save(user);
        } else {
            // Đảm bảo tài khoản admin đã tồn tại được kích hoạt
            User adminUser = userRepository.findByEmail(email).get();
            if (!adminUser.getActived() || adminUser.getActivation_key() != null) {
                adminUser.setActived(true);
                adminUser.setActivation_key(null);
                userRepository.save(adminUser);
            }
        }

        // Tạo họ cây mặc định "Chưa phân loại" nếu chưa có
        if (!familiesRepository.existsBySlug(Contains.DEFAULT_FAMILY_SLUG)) {
            Families defaultFamily = new Families();
            defaultFamily.setName(Contains.DEFAULT_FAMILY_NAME);
            defaultFamily.setSlug(Contains.DEFAULT_FAMILY_SLUG);
            defaultFamily.setDescription("Họ cây mặc định cho các cây dược liệu chưa được phân loại");
            familiesRepository.save(defaultFamily);
            logger.info("Đã tạo họ cây mặc định: {}", Contains.DEFAULT_FAMILY_NAME);
        }
    }
}
