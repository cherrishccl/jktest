package com.oxchains.themis.order.auth;

import com.oxchains.themis.order.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * create by huohuo
 * @author huohuo
 */
public class JwtAuthentication implements Authentication {

    private String token;
    private User user;
    private Map<String, Object> details;

    JwtAuthentication(User user, String token, Map<String, Object> details) {
        this.user = user;
        this.token = token;
        this.details = details;
    }

    public Optional<User> user(){
        return Optional.ofNullable(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
       return null;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getDetails() {
        return details;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (!isAuthenticated){
            user = null;
        }
    }

    @Override
    public String getName() {
        return user.getUsername();
    }

    @Override
    public String toString() {
        return token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}