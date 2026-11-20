package com.creator.video.domain;

import java.util.Optional;

public interface ProfileRepository {
    Optional<UserProfile> find(long userId);
    void ensure(long userId);
    boolean update(long userId, long version, String displayName, String bio, String website);
    void saveAvatar(long userId, byte[] data, String contentType);
    void clearAvatar(long userId);
    Optional<ProfileAvatar> avatar(long userId);
}
