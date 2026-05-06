package com.web.config;

import com.web.jwt.JWTConfigurer;
import com.web.jwt.JwtTokenProvider;
import com.web.repository.UserRepository;
import com.web.utils.Contains;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private final JwtTokenProvider tokenProvider;

    private final UserRepository userRepository;

    private final CorsFilter corsFilter;

    public WebSecurityConfig(JwtTokenProvider tokenProvider, UserRepository userRepository, CorsFilter corsFilter) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.corsFilter = corsFilter;
    }

    @Bean(BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {

    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http
                .csrf()
                .disable()
                .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling()
                .and()
                .headers()
                .and()
                .authorizeRequests()
                .antMatchers(
                        "/",
                        "/index",
                        "/login",
                        "/regis",
                        "/forgot",
                        "/reset-password",
                        "/reset-password.html",
                        "/confirm",
                        "/css/**",
                        "/js/**",
                        "/image/**",
                        "/webjars/**",
                        "/ws/**"
                ).permitAll()
                .antMatchers(
                        "/api/authenticate",
                        "/api/login",
                        "/api/login/**",
                        "/api/register",
                        "/api/regis",
                        "/api/active-account",
                        "/api/forgot-password",
                        "/api/send-new-otp",
                        "/api/chat",
                        "/api/chat/**",
                        "/api/message/upload-file",
                        "/api/public/**",
                        "/api/*/public/**",
                        "/api/expert/public/**",
                        "/api/folk-remedies/public/**"
                ).permitAll()
                .antMatchers("/api/admin/get-user-by-role", "/api/admin/addaccount", 
                             "/api/admin/delete-user-by-id", "/api/admin/lockOrUnlockUser", "/api/admin/export-users",
                             "/api/admin/check-role-admin").hasAuthority(Contains.ROLE_ADMIN)
                .antMatchers("/api/admin/find-user-by-id", "/api/admin/find-user-by-username").hasAnyAuthority(Contains.ROLE_ADMIN, Contains.ROLE_EXPERT)
                .antMatchers("/api/expert/admin/**").hasAuthority(Contains.ROLE_ADMIN)
                .antMatchers("/api/comments/admin/**").hasAuthority(Contains.ROLE_ADMIN)
                .antMatchers("/api/admin/**").hasAnyAuthority(Contains.ROLE_ADMIN, Contains.ROLE_EXPERT)
                .antMatchers("/api/expert/**").hasAnyAuthority(Contains.ROLE_EXPERT, Contains.ROLE_ADMIN)
                .antMatchers("/api/user/**").hasAnyAuthority(Contains.ROLE_USER, Contains.ROLE_ADMIN, Contains.ROLE_EXPERT)
                .antMatchers(org.springframework.http.HttpMethod.GET, "/admin/list-user", "/admin/create-user", 
                             "/admin/list-expert", "/admin/create-expert", "/admin/list-comment").permitAll()
                .antMatchers(org.springframework.http.HttpMethod.GET, "/admin/**").permitAll()
                .antMatchers(org.springframework.http.HttpMethod.GET, "/plant/**", "/articles/**", "/article-detail/**", "/plant-detail/**", "/experts/**", "/expert-detail/**", "/research/**", "/research-detail/**", "/about/**", "/index", "/", "/create-plant", "/create-article", "/my-account", "/user/messages", "/folk-remedies/**").permitAll()
                .antMatchers("/admin/list-user", "/admin/create-user").hasAuthority(Contains.ROLE_ADMIN)
                .antMatchers("/admin/list-expert", "/admin/create-expert").hasAuthority(Contains.ROLE_ADMIN)
                .antMatchers("/admin/list-comment").hasAuthority(Contains.ROLE_ADMIN)
                .antMatchers("/admin/**").hasAnyAuthority(Contains.ROLE_ADMIN, Contains.ROLE_EXPERT)
                .anyRequest().authenticated()
                .and()
                .apply(securityConfigurerAdapter())
                .and()
                .logout().logoutUrl("/logout").logoutSuccessUrl("/login");
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // WebSocket sẽ được xử lý authentication trong WebSocketSecurityConfig
        // Không ignore để vẫn có thể xử lý JWT token
    }
    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider, userRepository);
    }

}

