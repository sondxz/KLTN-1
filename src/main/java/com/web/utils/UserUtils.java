package com.web.utils;

import com.web.config.SecurityUtils;
import com.web.dto.CustomUserDetails;
import com.web.entity.User;
import com.web.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserUtils implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserUtils.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.get() == null) {
            throw new UsernameNotFoundException(username);
        }
        return new CustomUserDetails(user.get());
    }

    public User getUserWithAuthority(){
        try {
            Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
            if (!currentUserLogin.isPresent()) {
                return null;
            }
            Long id = Long.valueOf(currentUserLogin.get());
            return userRepository.findById(id).orElse(null);
        }
        catch (Exception e){
            logger.error("Error in getUserWithAuthority", e);
            return null;
        }
    }

    /**
     * Lấy role của user hiện tại
     * @return role name (ROLE_ADMIN, ROLE_EXPERT, ROLE_USER) hoặc null nếu chưa đăng nhập
     */
    public String getCurrentUserRole(){
        try {
            User user = getUserWithAuthority();
            if(user != null && user.getAuthorities() != null){
                return user.getAuthorities().getName();
            }
            return null;
        }
        catch (Exception e){
            return null;
        }
    }

    public String randomKey(){
        String str = "12345667890";
        Integer length = str.length()-1;
        StringBuilder stringBuilder = new StringBuilder("");
        for(int i=0; i<6; i++){
            Integer ran = (int)(Math.random()*length);
            stringBuilder.append(str.charAt(ran));
        }
        return String.valueOf(stringBuilder);
    }

    public String randomPass(){
        String str = "qwert1yui2op3as4dfg5hj6klzx7cvb8nmQ9WE0RTYUIOPASDFGHJKLZXCVBNM";
        Integer length = str.length()-1;
        StringBuilder stringBuilder = new StringBuilder("");
        for(int i=0; i<7; i++){
            Integer ran = (int)(Math.random()*length);
            stringBuilder.append(str.charAt(ran));
        }
        return String.valueOf(stringBuilder);
    }
}

