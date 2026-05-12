package com.bigfake.payments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Security configuration for the payments service.
 *
 * TODO: PAY-3890 - Migrate away from deprecated WebSecurityConfigurerAdapter
 * TODO: PAY-4050 - Implement OAuth2/JWT instead of basic auth
 * TODO: PAY-4051 - Move credentials to vault/secrets manager
 */
@Configuration
@EnableWebSecurity
@SuppressWarnings("deprecation")
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // TODO: This is overly permissive - tighten up for production (PAY-3891)
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                .antMatchers("/api/webhooks/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .httpBasic();

        // Allow H2 console frames - NOT for production
        http.headers().frameOptions().disable();
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        // TODO: PAY-4051 - These should NOT be hardcoded
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails serviceAccount = User.builder()
                .username("payment-gateway")
                .password(passwordEncoder().encode("gateway-secret-2023"))
                .roles("SERVICE")
                .build();

        return new InMemoryUserDetailsManager(admin, serviceAccount);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
