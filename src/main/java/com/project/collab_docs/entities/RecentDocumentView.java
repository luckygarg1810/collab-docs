package com.project.collab_docs.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks when a user last opened a document, for the per-user "Recent"
 * list. One row per (user, document) pair, upserted on every open — not
 * an append-only event log, since only the latest open time matters here.
 */
@Entity
@Table(name = "recent_document_views", uniqueConstraints = @UniqueConstraint(columnNames = { "document_id",
        "user_id" }, name = "uk_document_user_recent_view"), indexes = {
        @Index(name = "idx_recent_view_user_last_opened", columnList = "user_id,last_opened_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentDocumentView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_opened_at", nullable = false)
    private LocalDateTime lastOpenedAt;
}
