package com.project.collab_docs.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A user's star/bookmark on a document. Purely a per-user UI preference —
 * not an RBAC concern, so kept separate from DocumentPermission.
 */
@Entity
@Table(name = "starred_documents", uniqueConstraints = @UniqueConstraint(columnNames = { "document_id",
        "user_id" }, name = "uk_document_user_star"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StarredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
