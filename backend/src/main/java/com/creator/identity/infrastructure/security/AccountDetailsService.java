package com.creator.identity.infrastructure.security;

import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    public AccountDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var credentials = accounts.findCredentials(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var account = credentials.account();
        return User.withUsername(account.username()).password(credentials.passwordHash())
                .roles(account.role().name()).disabled(account.status() != AccountStatus.ACTIVE).build();
    }
}
