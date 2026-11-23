package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.CreatorVideoRepository;
import com.creator.video.domain.SubtitleLanguage;
import com.creator.video.domain.SubtitleRepository;
import com.creator.video.domain.VideoQueryRepository;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubtitleService {
    public static final int MAX_BYTES = 524288;
    private static final List<String> BLOCKED = List.of("<script", "<iframe", "<object", "<embed", "<style", "javascript:");
    private final SubtitleRepository subtitles;
    private final CreatorVideoRepository videos;
    private final VideoQueryRepository publicVideos;
    private final AccountQueryService accounts;
    private final AuditLog audit;

    public SubtitleService(SubtitleRepository subtitles, CreatorVideoRepository videos,
            VideoQueryRepository publicVideos, AccountQueryService accounts, AuditLog audit) {
        this.subtitles = subtitles;
        this.videos = videos;
        this.publicVideos = publicVideos;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(timeout = 5)
    public SubtitleRepository.Metadata upload(Actor actor, long videoId, String languageCode, byte[] content) {
        active(actor);
        owned(actor, videoId, true);
        var language = language(languageCode);
        if (content == null || content.length == 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (content.length > MAX_BYTES) throw new BusinessException(ErrorCode.REQUEST_TOO_LARGE);
        validate(content);
        subtitles.save(videoId, language.code(), language.label(), content, content.length);
        audit.record(actor, "SUBTITLE_UPDATED", "VIDEO", videoId, language.code());
        return subtitles.metadata(videoId, language.code()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(timeout = 5)
    public SubtitleRepository.Metadata upload(Actor actor, long videoId, byte[] content) {
        return upload(actor, videoId, "zh-CN", content);
    }

    @Transactional(readOnly = true)
    public List<SubtitleRepository.Metadata> list(Actor actor, long videoId) {
        active(actor);
        owned(actor, videoId, false);
        return subtitles.list(videoId);
    }

    @Transactional(readOnly = true)
    public SubtitleRepository.Metadata metadata(Actor actor, long videoId) {
        return metadata(actor, videoId, "zh-CN");
    }

    @Transactional(readOnly = true)
    public SubtitleRepository.Metadata metadata(Actor actor, long videoId, String languageCode) {
        active(actor);
        owned(actor, videoId, false);
        var language = language(languageCode);
        return subtitles.metadata(videoId, language.code())
                .orElse(new SubtitleRepository.Metadata(videoId, language.code(), language.label(), 0, null));
    }

    @Transactional(timeout = 5)
    public void delete(Actor actor, long videoId) {
        delete(actor, videoId, "zh-CN");
    }

    @Transactional(timeout = 5)
    public void delete(Actor actor, long videoId, String languageCode) {
        active(actor);
        owned(actor, videoId, true);
        var language = language(languageCode);
        if (subtitles.metadata(videoId, language.code()).isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        subtitles.delete(videoId, language.code());
        audit.record(actor, "SUBTITLE_DELETED", "VIDEO", videoId, language.code());
    }

    public List<SubtitleRepository.Metadata> publicList(long videoId) {
        publicVideos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return subtitles.list(videoId);
    }

    public byte[] publicContent(long videoId) {
        return publicContent(videoId, "zh-CN");
    }

    public byte[] publicContent(long videoId, String languageCode) {
        publicVideos.findPublic(videoId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        var language = language(languageCode);
        return subtitles.find(videoId, language.code()).map(subtitle -> subtitle.content())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private SubtitleLanguage language(String code) {
        return SubtitleLanguage.parse(code).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
    }

    private void validate(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (!text.startsWith("WEBVTT") || (text.length() > 6 && !Character.isWhitespace(text.charAt(6)))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (text.codePoints().anyMatch(code -> code == 0
                || (Character.isISOControl(code) && code != '\n' && code != '\r' && code != '\t'))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (BLOCKED.stream().anyMatch(lower::contains)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private void owned(Actor actor, long videoId, boolean lock) {
        videos.findOwned(actor.id(), videoId, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
