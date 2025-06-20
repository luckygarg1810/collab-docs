package com.project.collab_docs.response;

import com.project.collab_docs.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
    private Long id;
    private String title;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String yjsRoomId;
    private String ownerEmail;
    private String ownerName;
    private Boolean isTemplate;
    private Visibility visibility;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
