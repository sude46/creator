package com.creator.video.infrastructure;

import com.creator.video.domain.ProfileAvatar;
import com.creator.video.domain.ProfileRepository;
import com.creator.video.domain.UserProfile;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisProfileRepository implements ProfileRepository {
    private final ProfileMapper mapper;
    public MyBatisProfileRepository(ProfileMapper mapper) { this.mapper = mapper; }
    public Optional<UserProfile> find(long userId) { return Optional.ofNullable(mapper.find(userId)); }
    public void ensure(long userId) {
        if (mapper.find(userId) != null) return;
        try { mapper.insert(userId); } catch (DuplicateKeyException ignored) { }
    }
    public boolean update(long userId, long version, String displayName, String bio, String website) {
        return mapper.update(userId, version, displayName, bio, website) == 1;
    }
    public void saveAvatar(long userId, byte[] data, String contentType) {
        ensure(userId);
        mapper.saveAvatar(userId, data, contentType);
    }
    public void clearAvatar(long userId) { ensure(userId); mapper.clearAvatar(userId); }
    public Optional<ProfileAvatar> avatar(long userId) { return Optional.ofNullable(mapper.avatar(userId)); }
}
