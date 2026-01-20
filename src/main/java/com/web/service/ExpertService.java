package com.web.service;

import com.web.entity.Article;
import com.web.entity.Authority;
import com.web.entity.Expert;
import com.web.entity.User;
import com.web.enums.ArticleStatus;
import com.web.enums.UserType;
import com.web.exception.MessageException;
import com.web.repository.ArticleRepository;
import com.web.repository.AuthorityRepository;
import com.web.repository.ExpertRepository;
import com.web.repository.UserRepository;
import com.web.utils.Contains;
import com.web.utils.SlugGenerator;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ExpertService {

    @Autowired
    private ExpertRepository expertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public Expert create(Expert expert, String password) {
        // Kiểm tra email đã tồn tại chưa
        String email = expert.getEmail() != null && !expert.getEmail().trim().isEmpty() 
                ? expert.getEmail() 
                : (expert.getContactEmail() != null && !expert.getContactEmail().trim().isEmpty() 
                    ? expert.getContactEmail() 
                    : null);
        
        if (email == null || email.trim().isEmpty()) {
            throw new MessageException("Email không được để trống");
        }
        
        email = email.trim().toLowerCase();

        // Kiểm tra user đã tồn tại chưa
        if (userRepository.findByEmail(email).isPresent()) {
            throw new MessageException("Email đã được sử dụng");
        }

        // Kiểm tra password
        if (password == null || password.trim().isEmpty()) {
            throw new MessageException("Mật khẩu không được để trống");
        }

        // Tạo User account với role EXPERT
        User user = new User();
        user.setEmail(email);
        user.setUsername(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullname(expert.getName());
        user.setPhone(expert.getPhone());
        user.setActived(true);
        user.setUserType(UserType.EMAIL);
        
        // Set role EXPERT
        Authority expertAuthority = authorityRepository.findByName(Contains.ROLE_EXPERT);
        if (expertAuthority == null) {
            throw new MessageException("Role EXPERT chưa được khởi tạo");
        }
        user.setAuthorities(expertAuthority);
        user.setCreatedDate(LocalDateTime.now());

        // Lưu User trước
        User savedUser = userRepository.save(user);

        // Link Expert với User
        expert.setUser(savedUser);
        expert.setStatus(1);
        
        // Lưu Expert
        Expert savedExpert = expertRepository.save(expert);
        
        return savedExpert;
    }

    public Page<Expert> getAll(String search, Pageable pageable) {
        return expertRepository.findAllByParam(
                (search == null || search.trim().isEmpty()) ? null : search.trim(),
                pageable
        );
    }

    public Page<Expert> getAllPublic(String search, String specialization, Pageable pageable) {
        // Hiển thị tất cả Expert (không chỉ Expert có user)
        // Vì có thể có Expert cũ chưa có user account
        return expertRepository.findAllByParam(
                (search == null || search.trim().isEmpty()) ? null : search.trim(),
                specialization,
                pageable
        );
    }

    public Expert findById(Long id) {
        return expertRepository.findById(id)
                .orElseThrow(() -> new MessageException("Không tìm thấy chuyên gia"));
    }


    @Transactional
    public Expert update(Long id, Expert expert, String password) {
        Expert existingExpert = findById(id);
        
        // Xác định email mới (ưu tiên email, nếu không có thì dùng contactEmail)
        String newEmail = null;
        if (expert.getEmail() != null && !expert.getEmail().trim().isEmpty()) {
            newEmail = expert.getEmail().trim().toLowerCase();
        } else if (expert.getContactEmail() != null && !expert.getContactEmail().trim().isEmpty()) {
            newEmail = expert.getContactEmail().trim().toLowerCase();
        }
        
        // Nếu có email mới và Expert đã có User account
        if (newEmail != null && existingExpert.getUser() != null) {
            User existingUser = existingExpert.getUser();
            String currentEmail = existingUser.getEmail() != null ? existingUser.getEmail().trim().toLowerCase() : null;
            
            // Nếu email thay đổi, kiểm tra email mới có trùng với user khác không
            if (currentEmail == null || !newEmail.equals(currentEmail)) {
                // Kiểm tra email mới có trùng với user khác không (trừ chính user này)
                Optional<User> existingUserWithEmail = userRepository.findByEmail(newEmail);
                if (existingUserWithEmail.isPresent() && !existingUserWithEmail.get().getId().equals(existingUser.getId())) {
                    throw new MessageException("Email đã được sử dụng bởi tài khoản khác");
                }
                
                // Cập nhật email cho User
                existingUser.setEmail(newEmail);
                existingUser.setUsername(newEmail);
                userRepository.save(existingUser);
            }
        } else if (newEmail != null && existingExpert.getUser() == null) {
            // Nếu Expert chưa có User account nhưng có email mới, kiểm tra email có trùng không
            if (userRepository.findByEmail(newEmail).isPresent()) {
                throw new MessageException("Email đã được sử dụng");
            }
        }
        
        // Update thông tin expert
        if (expert.getName() != null) existingExpert.setName(expert.getName());
        if (expert.getSlug() != null) existingExpert.setSlug(expert.getSlug());
        if (expert.getTitle() != null) existingExpert.setTitle(expert.getTitle());
        if (expert.getEmail() != null) existingExpert.setEmail(expert.getEmail());
        if (expert.getContactEmail() != null) existingExpert.setContactEmail(expert.getContactEmail());
        if (expert.getPhone() != null) existingExpert.setPhone(expert.getPhone());
        if (expert.getSpecialization() != null) existingExpert.setSpecialization(expert.getSpecialization());
        if (expert.getInstitution() != null) existingExpert.setInstitution(expert.getInstitution());
        if (expert.getAvatar() != null) existingExpert.setAvatar(expert.getAvatar());
        if (expert.getEducation() != null) existingExpert.setEducation(expert.getEducation());
        if (expert.getBio() != null) existingExpert.setBio(expert.getBio());
        if (expert.getAchievements() != null) existingExpert.setAchievements(expert.getAchievements());
        
        // Update password nếu có
        if (password != null && !password.trim().isEmpty() && existingExpert.getUser() != null) {
            User user = existingExpert.getUser();
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
        }
        
        return expertRepository.save(existingExpert);
    }

    public void delete(Long id) {
        Expert expert = findById(id);
        // Xóa User account nếu có
        if (expert.getUser() != null) {
            userRepository.delete(expert.getUser());
        }
        expertRepository.deleteById(id);
    }
    
    // Lấy danh sách Expert từ User có role EXPERT
    public Page<Expert> getAllPublicFromUsers(String search, String specialization, Pageable pageable) {
        // Lấy từ Expert có user (tức là đã có tài khoản)
        return expertRepository.findAllByParamWithUser(
                (search == null || search.trim().isEmpty()) ? null : search.trim(),
                specialization,
                pageable
        );
    }
}
