package com.creator.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.creator.common.BusinessException;

@Service
public class IdentityService {
    private final UserAccountMapper accounts;
    private final PasswordEncoder encoder;

    public IdentityService(UserAccountMapper accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        validatePassword(request.password());
        return create(request.username(), request.password(), "USER");
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                    "密码需包含字母和数字，长度 10～64 位，UTF-8 编码不超过 72 字节");
        }
    }

    public UserView create(String username, String password, String role) {
        var account = new UserAccount();
        account.setUsername(username.toLowerCase(Locale.ROOT));
        account.setPasswordHash(encoder.encode(password));
        account.setRole(role);
        account.setStatus("ACTIVE");
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "该用户名已被使用");
        }
        return UserView.from(account);
    }
}
