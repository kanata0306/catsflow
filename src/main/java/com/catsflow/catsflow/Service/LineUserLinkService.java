package com.catsflow.catsflow.Service;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Repository.LineUserLinkRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LineUserLinkService {

    private final LineUserLinkRepository lineUserLinkRepository;

    public LineUserLinkService(LineUserLinkRepository lineUserLinkRepository) {
        this.lineUserLinkRepository = lineUserLinkRepository;
    }

    /** LINEユーザーを他のCatsflowアカウントから意図せず付け替えない。 */
    public LineUserLink linkUser(Long userId, String lineUserId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (lineUserId == null || lineUserId.isBlank()) {
            throw new IllegalArgumentException("lineUserId must not be blank");
        }

        Optional<LineUserLink> existingLink = lineUserLinkRepository.findByLineUserId(lineUserId);
        if (existingLink.isPresent()) {
            if (!existingLink.get().getUserId().equals(userId)) {
                throw new IllegalStateException("LINE account is already linked to another Catsflow user");
            }
            return existingLink.get();
        }

        LineUserLink link = new LineUserLink();
        link.setUserId(userId);
        link.setLineUserId(lineUserId);
        link.setStatus("linked");

        return lineUserLinkRepository.save(link);
    }

    /**
     * LINEユーザーIDから紐付け済みのCatsflowユーザーを返す。
     */
    public Optional<LineUserLink> findByLineUserId(String lineUserId) {
        if (lineUserId == null || lineUserId.isBlank()) {
            return Optional.empty();
        }
        return lineUserLinkRepository.findByLineUserId(lineUserId);
    }

    /**
     * 連携解除時は紐付けレコードを削除する。
     */
    public void unlink(String lineUserId) {
        if (lineUserId == null || lineUserId.isBlank()) {
            return;
        }
        lineUserLinkRepository.deleteByLineUserId(lineUserId);
    }
}
