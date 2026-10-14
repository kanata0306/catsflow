package com.catsflow.catsflow.Entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LineUserLink {
    private Long id;
    private Long userId;
    private String lineUserId;
    private String status;
    private LocalDateTime linkedAt;
    private LocalDateTime updatedAt;
}
