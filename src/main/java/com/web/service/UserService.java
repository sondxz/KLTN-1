package com.web.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.web.dto.CustomUserDetails;
import com.web.dto.TokenDto;
import com.web.dto.UserDto;
import com.web.entity.Authority;
import com.web.entity.User;
import com.web.enums.UserType;
import com.web.exception.MessageException;
import com.web.jwt.JwtTokenProvider;
import com.web.mapper.UserMapper;
import com.web.repository.AuthorityRepository;
import com.web.repository.ExpertRepository;
import com.web.repository.UserRepository;
import com.web.utils.CommonPage;
import com.web.utils.Contains;
import com.web.utils.MailService;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpertRepository expertRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MailService mailService;

    @Autowired
    private UserUtils userUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CommonPage commonPage;


    
    public TokenDto login(String username, String password) throws Exception {
        Optional<User> users = userRepository.findByUsername(username);
        // check infor user
        if(users.isPresent()){
            if(users.get().getUserType() != null){
                if(users.get().getUserType().equals(UserType.GOOGLE)){
                    throw new MessageException("Hãy đăng nhập bằng google");
                }
            }
        }
        checkUser(users);
        if(passwordEncoder.matches(password, users.get().getPassword())){
            CustomUserDetails customUserDetails = new CustomUserDetails(users.get());
            String token = jwtTokenProvider.generateToken(customUserDetails);
            TokenDto tokenDto = new TokenDto();
            tokenDto.setToken(token);
            tokenDto.setUser(userMapper.userToUserDto(users.get()));
            return tokenDto;
        }
        else{
            throw new MessageException("Mật khẩu không chính xác", 400);
        }
    }

    
    public TokenDto loginWithGoogle(GoogleIdToken.Payload payload) {
        User user = new User();
        user.setEmail(payload.getEmail());
        user.setFullname(payload.get("name").toString());
        user.setActived(true);
        user.setUsername(payload.getEmail());
        user.setAuthorities(authorityRepository.findByName(Contains.ROLE_USER));
        user.setCreatedDate(LocalDateTime.now());
        user.setUserType(UserType.GOOGLE);
        Optional<User> users = userRepository.findByEmail(user.getEmail());
        // check infor user

        if(users.isPresent()){
            if(users.get().getActived() == false){
                throw new MessageException("Tài khoản đã bị khóa");
            }
            CustomUserDetails customUserDetails = new CustomUserDetails(users.get());
            String token = jwtTokenProvider.generateToken(customUserDetails);
            TokenDto tokenDto = new TokenDto();
            tokenDto.setToken(token);
            tokenDto.setUser(userMapper.userToUserDto(users.get()));
            return tokenDto;
        }
        else{
            User u = userRepository.save(user);
            CustomUserDetails customUserDetails = new CustomUserDetails(u);
            String token = jwtTokenProvider.generateToken(customUserDetails);
            TokenDto tokenDto = new TokenDto();
            tokenDto.setToken(token);
            tokenDto.setUser(userMapper.userToUserDto(u));
            return tokenDto;
        }
    }


    
    public User regisUser(User user) {
        Optional<User> existingUserOpt = userRepository.findByEmail(user.getEmail());
        
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            
            // Nếu tài khoản đã được kích hoạt (actived = true và activation_key = null)
            if (existingUser.getActived() != null && existingUser.getActived() && existingUser.getActivation_key() == null) {
                throw new MessageException("Email đã được sử dụng", 400);
            }
            
            // Nếu tài khoản chưa được kích hoạt, cho phép cập nhật lại thông tin và gửi mã mới
            if (existingUser.getActivation_key() != null && (existingUser.getActived() == null || !existingUser.getActived())) {
                // Cập nhật thông tin user và tạo mã kích hoạt mới
                existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
                existingUser.setFullname(user.getFullname());
                existingUser.setUsername(user.getUsername());
                existingUser.setPhone(user.getPhone());
                existingUser.setAddress(user.getAddress());
                existingUser.setActivation_key(userUtils.randomKey());
                existingUser.setCreatedDate(LocalDateTime.now());
                
                User result = userRepository.save(existingUser);
                
                // Gửi lại email kích hoạt
                mailService.sendEmail(user.getEmail(), "Xác nhận tài khoản của bạn","Cảm ơn bạn đã tin tưởng và sử dụng dịch vụ của chúng tôi:<br>" +
                        "Để kích hoạt tài khoản của bạn, hãy nhập mã xác nhận bên dưới để xác thực tài khoản của bạn<br><br>" +
                        "<a style=\"background-color: #2f5fad; padding: 10px; color: #fff; font-size: 18px; font-weight: bold;\">"+existingUser.getActivation_key()+"</a>",false, true);
                return result;
            }
        }
        
        // Tạo tài khoản mới
        user.setCreatedDate(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActived(false);
        user.setUserType(UserType.EMAIL);
        user.setActivation_key(userUtils.randomKey());
        Authority authority = authorityRepository.findById(Contains.ROLE_USER)
                .orElseThrow(() -> new MessageException("Không tìm thấy quyền ROLE_USER trong hệ thống"));
        user.setAuthorities(authority);
        User result = userRepository.save(user);
        
        // Gửi email kích hoạt với nội dung cải thiện
        String emailContent = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #16a34a;'>Xác nhận tài khoản DuocLieuVN</h2>" +
                "<p>Cảm ơn bạn đã đăng ký tài khoản!</p>" +
                "<p>Để kích hoạt tài khoản của bạn, vui lòng nhập mã xác nhận bên dưới:</p>" +
                "<div style='background-color: #16a34a; color: #fff; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; margin: 20px 0; border-radius: 5px;'>" +
                user.getActivation_key() + "</div>" +
                "<p style='color: #666; font-size: 12px;'>Mã này có hiệu lực trong 24 giờ.</p>" +
                "<p style='color: #666; font-size: 12px;'>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.</p>" +
                "</div>";
        
        mailService.sendEmail(user.getEmail(), "Xác nhận tài khoản DuocLieuVN", emailContent, false, true);
        return result;
    }

    // kich hoat tai khoan
    
    public void activeAccount(String activationKey, String email) {
        Optional<User> user = userRepository.getUserByActivationKeyAndEmail(activationKey, email);
        user.ifPresent(exist->{
            exist.setActivation_key(null);
            exist.setActived(true);
            userRepository.save(exist);
            return;
        });
        if(user.isEmpty()){
            throw new MessageException("email hoặc mã xác nhận không chính xác", 404);
        }
    }

    public void sendNewOtp(String email) {
        // Validate email format
        if (email == null || email.trim().isEmpty()) {
            throw new MessageException("Email không được để trống");
        }
        
        // Kiểm tra format email cơ bản
        if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
            throw new MessageException("Email không hợp lệ");
        }
        
        Optional<User> user = userRepository.findByEmail(email);
        if(user.isEmpty()){
            throw new MessageException("Email không tồn tại");
        }
        if(user.get().getActived()){
            throw new MessageException("Tài khoản không thể thực hiện chức năng này");
        }
        if(user.get().getActived() == false && user.get().getActivation_key() == null){
            throw new MessageException("Tài khoản đã bị khóa");
        }
        String key = userUtils.randomKey();
        user.get().setActivation_key(key);
        userRepository.save(user.get());
        
        // Gửi email với nội dung cải thiện
        String emailContent = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #16a34a;'>Xác nhận tài khoản DuocLieuVN</h2>" +
                "<p>Cảm ơn bạn đã đăng ký tài khoản!</p>" +
                "<p>Để kích hoạt tài khoản của bạn, vui lòng nhập mã xác nhận bên dưới:</p>" +
                "<div style='background-color: #16a34a; color: #fff; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; margin: 20px 0; border-radius: 5px;'>" +
                key + "</div>" +
                "<p style='color: #666; font-size: 12px;'>Mã này có hiệu lực trong 24 giờ.</p>" +
                "<p style='color: #666; font-size: 12px;'>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.</p>" +
                "</div>";
        
        mailService.sendEmail(email, "Xác nhận tài khoản DuocLieuVN", emailContent, false, true);
    }

    
    public Boolean checkUser(Optional<User> users){
        if(users.isPresent() == false){
            throw new MessageException("Không tìm thấy tài khoản", 404);
        }
        else if(users.get().getActivation_key() != null && users.get().getActived() == false){
            throw new MessageException("Tài khoản chưa được kích hoạt", 300);
        }
        else if(users.get().getActived() == false && users.get().getActivation_key() == null){
            throw new MessageException("Tài khoản đã bị khóa", 500);
        }
        return true;
    }

    
    public Page<User> getUserByRole(String search, String role, Pageable pageable) {
        Page<User> page = null;
        if(role != null){
            page = userRepository.getUserByRole(search,role, pageable);
        }
        else{
            page = userRepository.findAll(search,pageable);
        }
        return page;
    }

    
    public void changePass(String oldPass, String newPass) {
        User user = userUtils.getUserWithAuthority();
        if(passwordEncoder.matches(oldPass, user.getPassword())){
            user.setPassword(passwordEncoder.encode(newPass));
            userRepository.save(user);
        }
        else{
            throw new MessageException("Mật khẩu không chính xác", 500);
        }
    }

    
    public void forgotPassword(String email) {
        Optional<User> users = userRepository.findByEmail(email);
        // check infor user
        checkUser(users);
        String randomPass = userUtils.randomPass();
        users.get().setPassword(passwordEncoder.encode(randomPass));
        userRepository.save(users.get());
        mailService.sendEmail(email, "Quên mật khẩu","Cảm ơn bạn đã tin tưởng và xử dụng dịch vụ của chúng tôi:<br>" +
                "Chúng tôi đã tạo một mật khẩu mới từ yêu cầu của bạn<br>" +
                "Tuyệt đối không được chia sẻ mật khẩu này với bất kỳ ai. Bạn hãy thay đổi mật khẩu ngay sau khi đăng nhập<br><br>" +
                "<a style=\"background-color: #2f5fad; padding: 10px; color: #fff; font-size: 18px; font-weight: bold;\">"+randomPass+"</a>",false, true);

    }

    public void guiYeuCauQuenMatKhau(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        checkUser(user);
        String random = userUtils.randomKey();
        user.get().setRememberKey(random);
        userRepository.save(user.get());

        String baseUrl = System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8080");

        mailService.sendEmail(email, "Đặt lại mật khẩu","Cảm ơn bạn đã tin tưởng và xử dụng dịch vụ của chúng tôi:<br>" +
                "Chúng tôi đã tạo một mật khẩu mới từ yêu cầu của bạn<br>" +
                "Hãy lick vào bên dưới để đặt lại mật khẩu mới của bạn<br><br>" +
                "<a href='"+baseUrl+"/reset-password.html?email="+email+"&key="+random+"' style=\"background-color: #2f5fad; padding: 10px; color: #fff; font-size: 18px; font-weight: bold;\">Đặt lại mật khẩu</a>",false, true);

    }

    public void xacNhanDatLaiMatKhau(String email, String password, String key) {
        Optional<User> user = userRepository.findByEmail(email);
        checkUser(user);
        if(user.get().getRememberKey().equals(key)){
            user.get().setPassword(passwordEncoder.encode(password));
            userRepository.save(user.get());
        }
        else{
            throw new MessageException("Mã xác thực không chính xác");
        }
    }

    public User createUser(User user) {
        if (user.getId() != null) {
            // === UPDATE USER ===
            Optional<User> optional = userRepository.findById(user.getId());
            if (optional.isEmpty()) {
                throw new MessageException("Không tìm thấy người dùng với ID: " + user.getId());
            }
            
            User existing = optional.get();
            
            // Kiểm tra email trùng - chỉ kiểm tra nếu email thay đổi
            if (!existing.getEmail().equals(user.getEmail())) {
                // Kiểm tra email trùng trong User (trừ user hiện tại)
                // Sử dụng findByEmailAndId để tìm user khác có cùng email
                Optional<User> userWithEmail = userRepository.findByEmailAndId(user.getEmail(), user.getId());
                if (userWithEmail.isPresent()) {
                    throw new MessageException("Email đã được sử dụng bởi người dùng khác");
                }
                
                // Kiểm tra email trùng trong Expert
                if (expertRepository.findByEmailOrContactEmail(user.getEmail()).isPresent()) {
                    throw new MessageException("Email đã được sử dụng bởi chuyên gia");
                }
            }
            
            // Kiểm tra username trùng - chỉ kiểm tra nếu username thay đổi
            if (!existing.getUsername().equals(user.getUsername())) {
                Optional<User> userWithUsername = userRepository.findByUsername(user.getUsername());
                if (userWithUsername.isPresent() && !userWithUsername.get().getId().equals(user.getId())) {
                    throw new MessageException("Tên đăng nhập đã được sử dụng");
                }
            }
            
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword(existing.getPassword());
            }
            else {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            user.setUserType(existing.getUserType());
            user.setActivation_key(existing.getActivation_key());
            user.setRememberKey(existing.getRememberKey());
        }
        else {
            // === CREATE NEW USER ===
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                throw new MessageException("Mật khẩu không được để trống khi tạo tài khoản mới");
            }
            
            // Kiểm tra email trùng trong User
            if(userRepository.findByEmail(user.getEmail()).isPresent()){
                throw new MessageException("Email đã được sử dụng bởi người dùng khác");
            }
            
            // Kiểm tra email trùng trong Expert
            if (expertRepository.findByEmailOrContactEmail(user.getEmail()).isPresent()) {
                throw new MessageException("Email đã được sử dụng bởi chuyên gia");
            }
            
            // Kiểm tra username trùng trong User
            if(userRepository.findByUsername(user.getUsername()).isPresent()){
                throw new MessageException("Tên đăng nhập đã được sử dụng");
            }
            
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setUserType(UserType.EMAIL);
        }

        return userRepository.save(user);
    }

    public User findById(Long id){
        return userRepository.findById(id).orElse(null);
    }

    public void deleteAccount(Long id){
        try {
            userRepository.deleteById(id);
        }
        catch (Exception e){
            throw new MessageException("Tài khoản này không thể xóa do có liên kết dữ liệu, hãy khóa tài khoản");
        }
    }

    /**
     * Ghi danh sách người dùng ra CSV.
     */
    public void writeUsersToCsv(Writer writer, String q, String role) {
        try {
            String search = (q != null) ? "%" + q.trim() + "%" : "%%";
            Page<User> page = getUserByRole(search, role, Pageable.unpaged());
            List<User> list = page.getContent();
            writer.write("ID,FULLNAME,USERNAME,EMAIL,ROLE,TRANG_THAI,NGAY_TAO\n");
            for (User u : list) {
                String line = String.format(
                        "%d,%s,%s,%s,%s,%s,%s\n",
                        u.getId(),
                        escapeCsv(u.getFullname()),
                        escapeCsv(u.getUsername()),
                        escapeCsv(u.getEmail()),
                        u.getAuthorities() != null ? escapeCsv(u.getAuthorities().getName()) : "",
                        Boolean.TRUE.equals(u.getActived()) ? "ACTIVE" : "INACTIVE",
                        u.getCreatedDate() != null ? u.getCreatedDate().toString() : ""
                );
                writer.write(line);
            }
            writer.flush();
        } catch (IOException e) {
            throw new MessageException("Lỗi khi xuất dữ liệu người dùng: " + e.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }
}
