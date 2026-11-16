package com.creator.identity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final UserAccountMapper accounts;

    public AccountDetailsService(UserAccountMapper accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var account = accounts.findByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return User.withUsername(account.getUsername()).password(account.getPasswordHash())
                .roles(account.getRole()).disabled(!"ACTIVE".equals(account.getStatus())).build();
    }
}
