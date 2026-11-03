package com.catsflow.catsflow.Controller;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.AssetSnapshotService;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssetSnapshotController {

    private final AssetSnapshotService assetSnapshotService;
    private final LineIdTokenVerifier lineIdTokenVerifier;
    private final LineUserLinkService lineUserLinkService;

    public AssetSnapshotController(AssetSnapshotService assetSnapshotService,
            LineIdTokenVerifier lineIdTokenVerifier,
            LineUserLinkService lineUserLinkService) {
        this.assetSnapshotService = assetSnapshotService;
        this.lineIdTokenVerifier = lineIdTokenVerifier;
        this.lineUserLinkService = lineUserLinkService;
    }

    @GetMapping("/api/assets/snapshots")
    public ResponseEntity<Map<String, Object>> getSnapshots(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "LINE認証を確認してください。"));
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "snapshots", assetSnapshotService.findRecentByUserId(userId.get())));
    }

    private Optional<Long> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        Optional<String> lineUserId = lineIdTokenVerifier.verify(authorization.substring(7));
        if (lineUserId.isEmpty()) {
            return Optional.empty();
        }

        return lineUserLinkService.findByLineUserId(lineUserId.get()).map(LineUserLink::getUserId);
    }
}