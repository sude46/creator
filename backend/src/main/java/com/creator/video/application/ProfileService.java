package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.ProfileAvatar;
import com.creator.video.domain.ProfileRepository;
import com.creator.video.domain.UserProfile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private static final int MAX_DISPLAY_NAME = 32;
    private static final int MAX_BIO = 200;
    private static final int MAX_WEBSITE = 200;
    private static final int MAX_AVATAR_BYTES = 200 * 1024;
    private final ProfileRepository profiles;
    private final AccountQueryService accounts;
    private final AuditLog audit;

    public ProfileService(ProfileRepository profiles, AccountQueryService accounts, AuditLog audit) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Form mine(Actor actor) {
        active(actor);
        return form(profiles.find(actor.id()).orElse(null));
    }

    @Transactional(timeout = 5)
    public Form update(Actor actor, long version, String displayName, String bio, String website) {
        active(actor);
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        String about = bio == null ? "" : bio.strip();
        String link = website == null ? "" : website.strip();
        if ((name != null && (name.codePointCount(0, name.length()) > MAX_DISPLAY_NAME
                || name.codePoints().anyMatch(Character::isISOControl)))
                || about.length() > MAX_BIO || about.codePoints().anyMatch(character ->
                    Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t')
                || !StandardCharsets.UTF_8.newEncoder().canEncode(about)) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE);
        }
        link = validWebsite(link);
        profiles.ensure(actor.id());
        var current = profiles.find(actor.id()).orElseThrow();
        if (version < 0 || current.version() != version) throw new BusinessException(ErrorCode.STALE_WRITE);
        if (!profiles.update(actor.id(), version, name, about, link)) throw new BusinessException(ErrorCode.STALE_WRITE);
        audit.record(actor, "PROFILE_UPDATED", "USER", actor.id());
        return form(profiles.find(actor.id()).orElseThrow());
    }

    @Transactional(timeout = 5)
    public Form uploadAvatar(Actor actor, byte[] data) {
        active(actor);
        if (data == null || data.length == 0 || data.length > MAX_AVATAR_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR);
        }
        String contentType = sniff(data);
        profiles.saveAvatar(actor.id(), data, contentType);
        audit.record(actor, "PROFILE_AVATAR_UPDATED", "USER", actor.id());
        return form(profiles.find(actor.id()).orElseThrow());
    }

    @Transactional(timeout = 5)
    public Form removeAvatar(Actor actor) {
        active(actor);
        profiles.clearAvatar(actor.id());
        audit.record(actor, "PROFILE_AVATAR_REMOVED", "USER", actor.id());
        return form(profiles.find(actor.id()).orElse(null));
    }

    @Transactional(readOnly = true)
    public ProfileAvatar avatar(long authorId) {
        if (authorId <= 0) throw new BusinessException(ErrorCode.NOT_FOUND);
        return profiles.avatar(authorId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Summary summary(long userId) {
        var profile = profiles.find(userId).orElse(null);
        return profile == null ? new Summary(null, "", "", false, 0)
                : new Summary(profile.displayName(), profile.bio(), profile.website(), profile.hasAvatar(), profile.version());
    }

    private Form form(UserProfile profile) {
        return profile == null ? new Form(null, "", "", false, 0)
                : new Form(profile.displayName(), profile.bio(), profile.website(), profile.hasAvatar(), profile.version());
    }

    private String validWebsite(String value) {
        if (value.isEmpty()) return "";
        if (value.length() > MAX_WEBSITE) throw new BusinessException(ErrorCode.INVALID_PROFILE);
        try {
            var uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getHost().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_PROFILE);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE);
        }
        return value;
    }

    private String sniff(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessException(ErrorCode.INVALID_AVATAR);
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }

    public record Form(String displayName, String bio, String website, boolean hasAvatar, long version) { }
    public record Summary(String displayName, String bio, String website, boolean hasAvatar, long version) { }
}
