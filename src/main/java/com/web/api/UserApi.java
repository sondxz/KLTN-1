package com.web.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.web.dto.LoginDto;
import com.web.dto.PasswordDto;
import com.web.dto.StatisticsDto;
import com.web.dto.TokenDto;
import com.web.dto.UserDto;
import com.web.dto.request.UserRequest;
import com.web.entity.User;
import com.web.exception.MessageException;
import com.web.jwt.JwtTokenProvider;
import com.web.mapper.UserMapper;
import com.web.repository.UserRepository;
import com.web.service.GoogleOAuth2Service;
import com.web.service.UserService;
import com.web.utils.MailService;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class UserApi {

    private final UserRepository userRepository;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserUtils userUtils;

    private final MailService mailService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private com.web.service.StatisticsService statisticsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserApi(UserRepository userRepository, JwtTokenProvider jwtTokenProvider, UserUtils userUtils, MailService mailService) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userUtils = userUtils;
        this.mailService = mailService;
    }

    @Autowired
    private GoogleOAuth2Service googleOAuth2Service;

    @PostMapping("/login/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody String credential) throws Exception {
        GoogleIdToken.Payload payload = googleOAuth2Service.verifyToken(credential);
        if(payload == null){
            throw new MessageException("Đăng nhập thất bại");
        }
        TokenDto tokenDto = userService.loginWithGoogle(payload);
        return new ResponseEntity(tokenDto, HttpStatus.OK);
    }

    /*token device get from firebase*/
    @PostMapping("/login")
    public TokenDto authenticate(@RequestBody LoginDto loginDto) throws Exception {
        TokenDto tokenDto = userService.login(loginDto.getUsername(), loginDto.getPassword());
        return tokenDto;
    }

    @PostMapping("/regis")
    public ResponseEntity<?> regisUser(@RequestBody UserRequest userRequest) throws URISyntaxException {
        User user = userMapper.userRequestToUser(userRequest);
        UserDto result= userMapper.userToUserDto(userService.regisUser(user));
        return ResponseEntity
                .created(new URI("/api/register-user/" + user.getUsername()))
                .body(result);
    }

    @PostMapping("/active-account")
    public ResponseEntity<?> activeAccount(@RequestParam String email, @RequestParam String key) throws URISyntaxException {
        userService.activeAccount(key, email);
        return new ResponseEntity<>("kích hoạt thành công", HttpStatus.OK);
    }

    @PostMapping("/send-new-otp")
    public ResponseEntity<?> sendNewOtp(@RequestParam String email) {
        userService.sendNewOtp(email);
        return new ResponseEntity<>("Gửi otp mới thành công", HttpStatus.OK);
    }

    @PostMapping("/user/change-password")
    public ResponseEntity<?> changePassword(@RequestBody PasswordDto passwordDto){
        userService.changePass(passwordDto.getOldPass(), passwordDto.getNewPass());
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> changePassword(@RequestParam String email){
        userService.guiYeuCauQuenMatKhau(email);
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }

    @GetMapping("/admin/get-user-by-role")
    public ResponseEntity<?> getUserByRole(@RequestParam(value = "role", required = false) String role,
                                           @RequestParam(value = "q", required = false) String search,
                                           Pageable pageable){
        Page<User> userDtos = userService.getUserByRole("%"+search+"%",role,pageable);
        return new ResponseEntity<>(userDtos, HttpStatus.OK);
    }

    @GetMapping("/admin/check-role-admin")
    public void checkRoleAdmin(){
    }

    @GetMapping("/user/check-role-user")
    public void checkRoleUser(){
    }

    @PostMapping("/admin/lockOrUnlockUser")
    public ResponseEntity<?> activeOrUnactiveUser(@RequestParam("id") Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + id));
        
        if(user.getActived() == true){
            user.setActived(false);
            userRepository.save(user);
            return ResponseEntity.ok("Đã khóa tài khoản");
        }
        else{
            user.setActived(true);
            userRepository.save(user);
            return ResponseEntity.ok("Đã mở khóa tài khoản");
        }
    }

    @PostMapping("/admin/addaccount")
    public ResponseEntity<?> addaccount(@RequestBody User user) {
        User result= userService.createUser(user);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @GetMapping("/admin/find-user-by-id")
    public ResponseEntity<?> findById(@RequestParam Long id) {
        User result= userService.findById(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping("/admin/find-user-by-username")
    public ResponseEntity<?> findByUsername(@RequestParam String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            return new ResponseEntity<>(userOpt.get(), HttpStatus.OK);
        }
        return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
    }

    @DeleteMapping("/admin/delete-user-by-id")
    public ResponseEntity<?> deleteById(@RequestParam Long id) {
        userService.deleteAccount(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/admin/statistics")
    public ResponseEntity<StatisticsDto> getStatistics() {
        StatisticsDto stats = statisticsService.getStatistics();
        return new ResponseEntity<>(stats, HttpStatus.OK);
    }

    @PostMapping("/public/dat-lai-mat-khau")
    public ResponseEntity<?> datLaiMatKhau(@RequestBody java.util.Map<String, String> request) {
        try {
            String email = request.get("email");
            String key = request.get("key");
            String password = request.get("password");
            
            if (email == null || key == null || password == null) {
                return new ResponseEntity<>(
                    java.util.Map.of("defaultMessage", "Thiếu thông tin bắt buộc: email, key, password"),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            userService.xacNhanDatLaiMatKhau(email, password, key);
            return new ResponseEntity<>(
                java.util.Map.of("message", "Đặt lại mật khẩu thành công"),
                HttpStatus.OK
            );
        } catch (MessageException e) {
            // MessageException sẽ được RestErrorHandler xử lý và trả về 417
            // Nhưng để đảm bảo response đúng format, ta throw lại
            throw e;
        } catch (Exception e) {
            return new ResponseEntity<>(
                java.util.Map.of("defaultMessage", "Có lỗi xảy ra: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Lấy thông tin user hiện tại (từ JWT token)
     */
    @GetMapping("/user/current")
    public ResponseEntity<?> getCurrentUser() {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            return new ResponseEntity<>("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(currentUser, HttpStatus.OK);
    }

    /**
     * Cập nhật thông tin profile của user hiện tại
     */
    @PostMapping("/user/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody java.util.Map<String, String> data) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            return new ResponseEntity<>("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }

        // Cập nhật thông tin cơ bản
        if (data.containsKey("fullname")) {
            currentUser.setFullname(data.get("fullname"));
        }
        if (data.containsKey("phone")) {
            currentUser.setPhone(data.get("phone"));
        }
        if (data.containsKey("address")) {
            currentUser.setAddress(data.get("address"));
        }

        // Xử lý đổi mật khẩu nếu có
        String oldPassword = data.get("oldPassword");
        String newPassword = data.get("newPassword");

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            // Có yêu cầu đổi mật khẩu
            if (oldPassword == null || oldPassword.trim().isEmpty()) {
                return new ResponseEntity<>("Vui lòng nhập mật khẩu hiện tại", HttpStatus.BAD_REQUEST);
            }
            // Kiểm tra mật khẩu cũ
            if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
                return new ResponseEntity<>("Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST);
            }
            // Đổi mật khẩu mới
            currentUser.setPassword(passwordEncoder.encode(newPassword));
        } else {
            // Không đổi mật khẩu nhưng vẫn cần xác nhận mật khẩu hiện tại
            if (oldPassword == null || oldPassword.trim().isEmpty()) {
                return new ResponseEntity<>("Vui lòng nhập mật khẩu hiện tại để xác nhận", HttpStatus.BAD_REQUEST);
            }
            // Kiểm tra mật khẩu hiện tại
            if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
                return new ResponseEntity<>("Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST);
            }
        }

        // Lưu thay đổi
        User updatedUser = userRepository.save(currentUser);
        return new ResponseEntity<>(updatedUser, HttpStatus.OK);
    }
}
