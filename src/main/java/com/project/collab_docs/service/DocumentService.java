package com.project.collab_docs.service;

import com.project.collab_docs.dto.request.UpdateTitleRequest;
import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.DocumentPermission;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.DocumentFilter;
import com.project.collab_docs.enums.Role;
import com.project.collab_docs.enums.Visibility;
import com.project.collab_docs.exception.PermissionDeniedException;
import com.project.collab_docs.exception.ResourceNotFoundException;
import com.project.collab_docs.repository.DocumentRepository;
import com.project.collab_docs.repository.DocumentPermissionRepository;
import com.project.collab_docs.repository.DocumentVersionRepository;
import com.project.collab_docs.repository.StarredDocumentRepository;
import com.project.collab_docs.repository.RecentDocumentViewRepository;
import com.project.collab_docs.repository.UserRepository;
import com.project.collab_docs.entities.StarredDocument;
import com.project.collab_docs.entities.RecentDocumentView;
import com.project.collab_docs.dto.response.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.fit.pdfdom.PDFDomTree;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final DocumentPermissionRepository permissionRepository;
    private final DocumentVersionRepository versionRepository;
    private final StarredDocumentRepository starredDocumentRepository;
    private final RecentDocumentViewRepository recentDocumentViewRepository;
    private final DocumentEventPublisher documentEventPublisher;

    @Value("${app.recycle-bin.retention-days:15}")
    private int recycleBinRetentionDays;

    @Transactional
    public Document createBlankDocument(String title, Long userId) {
        // Fetch user from database to get managed entity
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        // Note: Blank documents start with no content
        // TipTap editor with Yjs will initialize the collaborative document
        // Yjs snapshots are saved automatically by the Yjs service
        Document document = Document.builder()
                .title(title)
                .fileName(title + ".docx")
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .contentType("application/octet-stream") // Yjs binary format
                .fileSize(0L)
                .visibility(Visibility.PRIVATE) // Default to private
                .isDeleted(false)
                .build();

        Document savedDocument = documentRepository.save(document);

        // AUTO-GRANT OWNER PERMISSION
        permissionService.grantPermission(
                savedDocument.getId(),
                owner.getId(),
                Role.OWNER,
                owner.getId());

        log.info("Created blank document with ID: {} for user: {} with OWNER permission",
                savedDocument.getId(), owner.getEmail());

        return savedDocument;
    }

    /**
     * Used by the yjs-service's periodic per-session hardcheck (every 15
     * min) to confirm a still-open WebSocket's user still has access —
     * defense-in-depth for any permission change that doesn't go through a
     * code path that publishes a document-events broadcast. Takes the
     * yjsRoomId (not the numeric document id) because that's all a
     * WebSocket connection in yjs-service actually knows about the
     * document it's attached to. A missing/deleted room resolves to no
     * access, which correctly triggers a kick rather than an error.
     */
    @Transactional(readOnly = true)
    public boolean hasViewerAccess(String yjsRoomId, Long userId) {
        return documentRepository.findByYjsRoomIdAndIsDeletedFalse(yjsRoomId)
                .map(document -> permissionService.hasPermission(document.getId(), userId, Role.VIEWER))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId, User requestingUser) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check VIEWER permission using RBAC
        if (!permissionService.hasPermission(documentId, requestingUser.getId(), Role.VIEWER)) {
            throw new PermissionDeniedException("Access denied. You don't have permission to view this document");
        }

        log.info("Document {} accessed by user: {} with role: {}",
                documentId, requestingUser.getEmail(),
                permissionService.getEffectiveRole(documentId, requestingUser.getId()));
        return document;
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getUserDocuments(User user, DocumentFilter filter, int page, int size) {
        try {
            log.info("Get Document API called");
            // Validate pagination parameters
            if (page < 0) {
                throw new IllegalArgumentException("Page number cannot be negative");
            }
            if (size <= 0 || size > 100) {
                throw new IllegalArgumentException("Page size must be between 1 and 100");
            }

            // Get the requested subset of documents the user can access (RBAC-checked)
            List<Document> accessibleDocuments = switch (filter) {
                case OWNED -> permissionService.getUserDocumentsByRole(user.getId(), Role.OWNER);
                case SHARED -> permissionService.getSharedDocumentsForUser(user.getId());
                case ALL -> permissionService.getUserAccessibleDocuments(user.getId());
                case STARRED -> {
                    // Intersect with current access so a star left behind after
                    // access was revoked doesn't resurface a document the user
                    // can no longer open.
                    var accessibleIds = permissionService.getUserAccessibleDocuments(user.getId()).stream()
                            .map(Document::getId)
                            .collect(Collectors.toSet());
                    yield starredDocumentRepository.findStarredDocumentsByUserId(user.getId()).stream()
                            .filter(doc -> accessibleIds.contains(doc.getId()))
                            .toList();
                }
                case RECENT -> recentDocumentViewRepository
                        .findTop10ByUserIdOrderByLastOpenedAtDesc(user.getId()).stream()
                        .map(RecentDocumentView::getDocument)
                        .filter(doc -> !doc.getIsDeleted()
                                && permissionService.hasPermission(doc.getId(), user.getId(), Role.VIEWER))
                        .toList();
                // Not RBAC-based like the others — the owner's own deleted
                // documents, full stop. A collaborator who lost access via
                // deletion doesn't see it in their own trash; only the owner does.
                case TRASH -> documentRepository.findByOwnerAndIsDeletedTrueOrderByDeletedAtDesc(user);
            };

            // Manual pagination since we're working with a List
            int start = page * size;
            int end = Math.min(start + size, accessibleDocuments.size());

            var paginatedDocs = accessibleDocuments.subList(
                    Math.min(start, accessibleDocuments.size()),
                    end);

            // Convert to DocumentResponse, resolving the caller's role on each
            // (owner vs. shared-with) so the frontend can rely on the backend
            // for ownership instead of comparing emails client-side.
            var documentResponses = paginatedDocs.stream()
                    .map(doc -> mapToDocumentResponse(doc, user.getId()))
                    .toList();

            // Create Page object
            Pageable pageable = PageRequest.of(page, size);
            long total = accessibleDocuments.size();

            Page<DocumentResponse> documentResponsePage = new org.springframework.data.domain.PageImpl<>(
                    documentResponses, pageable, total);

            log.info("Retrieved {} accessible documents for user: {} (page: {}, size: {})",
                    total, user.getEmail(), page, size);

            return documentResponsePage;

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for getUserDocuments: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving documents for user {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve user documents", e);
        }
    }



    @Transactional
    public Document uploadDocument(MultipartFile file, String title, User owner) throws IOException {
        // Validate file
        validateUploadedFile(file);

        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        // Safely handle potential null filename
        String originalFilename = file.getOriginalFilename();
        String fileName = (originalFilename != null && !originalFilename.isEmpty())
                ? originalFilename
                : "Untitled File";

        String originalContentType = file.getContentType();

        Document document = Document.builder()
                .title(title != null && !title.trim().isEmpty() ? title : getFileNameWithoutExtension(fileName))
                .fileName(fileName)
                .contentType(originalContentType)
                .fileSize(file.getSize())
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .visibility(Visibility.PRIVATE)
                .isDeleted(false)
                .build();

        Document savedDocument = documentRepository.save(document);

        // AUTO-GRANT OWNER PERMISSION
        permissionService.grantPermission(
                savedDocument.getId(),
                owner.getId(),
                Role.OWNER,
                owner.getId());

        log.info("Uploaded document '{}' (ID: {}) for user: {}",
                fileName, savedDocument.getId(), owner.getEmail());

        return savedDocument;
    }

    /**
     * Converts an uploaded file's bytes to TipTap-compatible HTML.
     * DOCX files go through Pandoc (installed in the Docker image) for
     * near-perfect semantic HTML. PDF files go through pdf2dom which
     * uses font-size heuristics to recover headings and paragraph structure.
     * Returns null on failure so the editor still opens (just blank).
     */
    public String extractHtmlFromFile(byte[] fileBytes, String contentType, String fileName) {
        try {
            boolean isDocx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                    || (fileName != null && fileName.toLowerCase().endsWith(".docx"));
            boolean isPdf = "application/pdf".equals(contentType)
                    || (fileName != null && fileName.toLowerCase().endsWith(".pdf"));

            if (isDocx) {
                return convertDocxWithPandoc(fileBytes);
            } else if (isPdf) {
                return convertPdfWithPdf2dom(fileBytes);
            }
            log.warn("Unsupported content type for extraction: {}", contentType);
            return null;
        } catch (Exception e) {
            log.error("Content extraction failed for file '{}': {}", fileName, e.getMessage(), e);
            return null; // graceful degradation — editor opens blank
        }
    }

    // ── DOCX conversion ──────────────────────────────────────────────────────

    /**
     * Runs Pandoc (installed in the Docker image) as a subprocess.
     * Pandoc reads DOCX at the semantic OOXML level — style names become
     * proper heading tags, list continuation numbering is preserved, and
     * output is clean HTML5 with no inline CSS.
     * Temp files are always deleted in the finally block.
     */
    private String convertDocxWithPandoc(byte[] fileBytes) throws IOException, InterruptedException {
        Path tempInput = Files.createTempFile("collab_upload_", ".docx");
        Path tempOutput = Files.createTempFile("collab_converted_", ".html");
        try {
            Files.write(tempInput, fileBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "pandoc",
                    "--from=docx",
                    "--to=html5",
                    "--wrap=none",          // no soft line-wraps in output
                    "--no-highlight",       // skip code syntax highlighting divs
                    "--output=" + tempOutput.toAbsolutePath(),
                    tempInput.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Pandoc timed out after 60 seconds");
            }
            if (process.exitValue() != 0) {
                String stderr = new String(process.getInputStream().readAllBytes());
                throw new IOException("Pandoc exited with code " + process.exitValue() + ": " + stderr);
            }

            String rawHtml = Files.readString(tempOutput);
            log.info("Pandoc converted DOCX to HTML ({} chars)", rawHtml.length());
            return sanitizeHtmlForTipTap(rawHtml, false);

        } finally {
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutput);
        }
    }

    // ── PDF conversion ───────────────────────────────────────────────────────

    /**
     * Uses pdf2dom (built on PDFBox) to convert PDF to an HTML DOM,
     * then serialises the body to a string and sanitises it.
     * pdf2dom infers heading levels from font size relative to the
     * document's median body-text size — not perfect but readable.
     */
    private String convertPdfWithPdf2dom(byte[] fileBytes) throws Exception {
        try (PDDocument pdDocument = PDDocument.load(fileBytes)) {
            PDFDomTree parser = new PDFDomTree();
            org.w3c.dom.Document dom = parser.createDOM(pdDocument);

            // Serialise just the <body> children to an HTML string
            org.w3c.dom.NodeList bodyNodes = dom.getElementsByTagName("body");
            if (bodyNodes.getLength() == 0) {
                throw new IOException("pdf2dom produced no body element");
            }
            org.w3c.dom.Node bodyNode = bodyNodes.item(0);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "html");

            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(bodyNode), new StreamResult(sw));
            String rawHtml = sw.toString();

            log.info("pdf2dom converted PDF to HTML ({} chars)", rawHtml.length());
            return sanitizeHtmlForTipTap(rawHtml, true);
        }
    }

    // ── Shared HTML sanitizer ────────────────────────────────────────────────

    /**
     * Strips everything TipTap doesn't understand and normalises the markup:
     *
     * 1. Removes <img>, <figure>, <figcaption>, <head>, <script>, <style>
     * 2. Keeps only elements the installed TipTap extensions can handle
     * 3. Strips all CSS class/style attributes (TipTap uses its own schema)
     * 4. For PDF output (isPdf=true): collapses adjacent inline spans with
     *    the same font-weight/style into single <strong>/<em> elements
     *
     * The allowlist maps to: StarterKit (p, h1-h6, ul, ol, li, blockquote,
     * pre, code, hr, br), Underline (u), Link (a[href]), Highlight (mark),
     * TextAlign (handled via data-text-align attr), and basic table nodes.
     */
    private String sanitizeHtmlForTipTap(String rawHtml, boolean isPdf) {
        // Parse with Jsoup — handles both full HTML documents and fragments
        org.jsoup.nodes.Document doc = Jsoup.parse(rawHtml);

        if (isPdf) {
            // pdf2dom wraps every character run in a <span style="...">
            // Promote spans with font-weight:bold → <strong>, font-style:italic → <em>
            for (Element span : doc.select("span[style]")) {
                String style = span.attr("style").toLowerCase();
                boolean bold   = style.contains("font-weight:bold") || style.contains("font-weight: bold");
                boolean italic = style.contains("font-style:italic") || style.contains("font-style: italic");
                if (bold && italic) {
                    span.tagName("strong"); // wrap in strong; italic handled below
                } else if (bold) {
                    span.tagName("strong");
                } else if (italic) {
                    span.tagName("em");
                } else {
                    // plain span — unwrap it (keep text, remove the tag)
                    span.unwrap();
                }
            }
            // pdf2dom uses <div class="page"> wrappers — replace with plain divs
            // so the content flows as normal paragraphs
            for (Element div : doc.select("div.page")) {
                div.removeAttr("class");
                div.removeAttr("style");
            }
        }

        // Build the Jsoup safelist: only elements TipTap extensions understand
        Safelist safelist = Safelist.none()
                .addTags("p", "h1", "h2", "h3", "h4", "h5", "h6",
                         "strong", "em", "u", "s", "code", "pre",
                         "ul", "ol", "li",
                         "blockquote", "hr", "br",
                         "a", "mark",
                         "table", "thead", "tbody", "tr", "th", "td")
                .addAttributes("a", "href", "title", "target")
                .addAttributes("th", "colspan", "rowspan")
                .addAttributes("td", "colspan", "rowspan")
                .addProtocols("a", "href", "http", "https", "mailto");

        String clean = Jsoup.clean(doc.body().html(), safelist);

        // Final pass: remove empty paragraphs that pdf2dom tends to generate
        clean = clean.replaceAll("<p>\\s*</p>", "");
        clean = clean.trim();

        return clean;
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        boolean isDocx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                || originalFilename.endsWith(".docx");
        boolean isPdf = "application/pdf".equals(contentType)
                || originalFilename.endsWith(".pdf");

        // Some browsers send .docx as application/octet-stream or application/zip
        // so we use the extension as a reliable fallback
        if (!isDocx && !isPdf) {
            throw new IllegalArgumentException("Only DOCX and PDF files are supported");
        }

        // 20MB limit
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("File size cannot exceed 20MB");
        }
    }

    public DocumentResponse mapToDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .yjsRoomId(document.getYjsRoomId())
                .ownerEmail(document.getOwner().getEmail())
                .ownerName(document.getOwner().getFirstName() + " " + document.getOwner().getLastName())
                .isTemplate(document.getIsTemplate())
                .visibility(document.getVisibility())
                .isDeleted(document.getIsDeleted())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .isStarred(false) // freshly created document, can't be starred yet
                .build();
    }

    /**
     * Map to DocumentResponse with the requesting user's effective role resolved.
     */
    public DocumentResponse mapToDocumentResponse(Document document, Long userId) {
        Role effectiveRole;
        if (document.getOwner().getId().equals(userId)) {
            // Owners have no DocumentPermission row — they are always OWNER.
            effectiveRole = Role.OWNER;
        } else {
            effectiveRole = permissionRepository
                    .findByDocumentIdAndUserId(document.getId(), userId)
                    .map(DocumentPermission::getRole)
                    .orElse(Role.VIEWER);
        }
        boolean isStarred = starredDocumentRepository.existsByDocumentIdAndUserId(document.getId(), userId);
        LocalDateTime lastOpenedAt = recentDocumentViewRepository
                .findByUserIdAndDocumentId(userId, document.getId())
                .map(RecentDocumentView::getLastOpenedAt)
                .orElse(null);
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .yjsRoomId(document.getYjsRoomId())
                .ownerEmail(document.getOwner().getEmail())
                .ownerName(document.getOwner().getFirstName() + " " + document.getOwner().getLastName())
                .isTemplate(document.getIsTemplate())
                .visibility(document.getVisibility())
                .isDeleted(document.getIsDeleted())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .userRole(effectiveRole)
                .isStarred(isStarred)
                .lastOpenedAt(lastOpenedAt)
                .deletedAt(document.getDeletedAt())
                .build();
    }

    @Transactional
    public void recordDocumentOpen(Long documentId, User user) {
        if (!permissionService.hasPermission(documentId, user.getId(), Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }
        recentDocumentViewRepository.recordOpen(user.getId(), documentId);
    }

    @Transactional
    public void starDocument(Long documentId, User user) {
        if (!permissionService.hasPermission(documentId, user.getId(), Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }
        if (starredDocumentRepository.existsByDocumentIdAndUserId(documentId, user.getId())) {
            return; // already starred — idempotent
        }
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        starredDocumentRepository.save(StarredDocument.builder()
                .document(document)
                .user(user)
                .build());
    }

    @Transactional
    public void unstarDocument(Long documentId, User user) {
        starredDocumentRepository.deleteByDocumentIdAndUserId(documentId, user.getId());
    }

    // Note: HTML content extraction methods removed
    // The frontend (TipTap editor) handles file conversion directly
    // This provides better control over the document format and allows
    // immediate Yjs collaboration setup without backend processing

    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "Untitled Document";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    @Transactional
    public void saveYjsSnapshot(String yjsRoomId, byte[] snapshot) {
        documentRepository.findByYjsRoomIdAndIsDeletedFalse(yjsRoomId)
                .ifPresent(document -> {
                    document.setYjsSnapshot(snapshot);
                    documentRepository.save(document);
                    log.info("Saved Yjs snapshot for document: {}", document.getId());
                });
    }

    @Transactional
    public void updateDocumentVisibility(Long documentId, Visibility visibility, User owner) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check OWNER permission using RBAC
        if (!permissionService.hasPermission(documentId, owner.getId(), Role.OWNER)) {
            throw new PermissionDeniedException("Only the document owner can change visibility");
        }

        if (!isValidVisibility(visibility)) {
            throw new IllegalArgumentException("Invalid visibility value. Must be 'private', 'shared', or 'public'");
        }

        document.setVisibility(visibility);
        documentRepository.save(document);
        log.info("Updated document {} visibility to: {}", documentId, visibility);
    }

    private boolean isValidVisibility(Visibility visibility) {
        return Visibility.PRIVATE.equals(visibility) || Visibility.PUBLIC.equals(visibility)
                || Visibility.SHARED.equals(visibility);
    }

    @Transactional
    public void softDeleteDocument(Long documentId, User user) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check OWNER permission using RBAC
        if (!permissionService.hasPermission(documentId, user.getId(), Role.OWNER)) {
            throw new PermissionDeniedException("Only the document owner can delete this document");
        }

        // Perform soft delete
        document.setIsDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        documentEventPublisher.publishDocumentDeleted(documentId, document.getYjsRoomId());

        log.info("Soft deleted document with ID: {} by user: {}", documentId, user.getEmail());
    }

    /**
     * Restore a document from the Recycle Bin. Checked directly against
     * document.getOwner() rather than PermissionService — the RBAC-lookup
     * paths (getUserAccessibleDocuments etc.) all filter isDeleted=false,
     * so they can't see this document to begin with while it's trashed.
     * Collaborator permissions were never touched by the soft delete, so
     * restoring needs nothing beyond flipping these two fields — every
     * previous collaborator's access is already still there.
     */
    @Transactional
    public void restoreDocument(Long documentId, User user) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new PermissionDeniedException("Only the document owner can restore this document");
        }
        if (!Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new IllegalArgumentException("Document is not in the Recycle Bin");
        }

        document.setIsDeleted(false);
        document.setDeletedAt(null);
        documentRepository.save(document);

        log.info("Restored document with ID: {} by user: {}", documentId, user.getEmail());
    }

    /**
     * Lets a non-owner collaborator remove themselves from a document —
     * "leave this shared document." Straight permission-row delete, no
     * Recycle Bin involved (that's an owner-only concept for the document
     * itself, not per-collaborator access). Publishes PERMISSION_REVOKED so
     * an active editing session for this exact user gets closed in real
     * time if they're mid-session when they do this from another tab.
     */
    @Transactional
    public void selfRevokeAccess(Long documentId, User user) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (document.getOwner().getId().equals(user.getId())) {
            throw new PermissionDeniedException(
                    "Owners can't remove their own document this way — delete it instead");
        }

        permissionRepository.deleteByDocumentIdAndUserId(documentId, user.getId());
        documentEventPublisher.publishPermissionRevoked(documentId, document.getYjsRoomId(), user.getId());

        log.info("User {} removed their own access to document {}", user.getEmail(), documentId);
    }

    /**
     * Owner-triggered immediate permanent delete — skips the 15-day wait.
     * Requires the document to already be in the Recycle Bin (soft-deleted),
     * so this can't be used to bypass the normal delete-then-purge flow.
     */
    @Transactional
    public void permanentlyDeleteDocument(Long documentId, User user) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new PermissionDeniedException("Only the document owner can permanently delete this document");
        }
        if (!Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new IllegalArgumentException("Document must be in the Recycle Bin before it can be permanently deleted");
        }

        hardDeleteDocument(document);
        log.info("Permanently deleted document with ID: {} by user: {}", documentId, user.getEmail());
    }

    /**
     * Daily sweep of the Recycle Bin: anything soft-deleted more than
     * recycleBinRetentionDays ago is hard-deleted. Also runs once at
     * application startup — convenient for a local/single-instance setup
     * where waiting for the cron window to test this is impractical. Not a
     * pattern to keep once this ever runs as multiple replicas — every
     * instance re-running a destructive sweep on every restart is fine only
     * because it's idempotent (nothing left to purge the second time).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTrash() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(recycleBinRetentionDays);
        List<Document> expired = documentRepository.findByIsDeletedTrueAndDeletedAtBefore(cutoff);

        if (expired.isEmpty()) {
            log.info("Recycle Bin purge: nothing older than {} days to purge", recycleBinRetentionDays);
            return;
        }

        for (Document document : expired) {
            hardDeleteDocument(document);
        }
        log.info("Recycle Bin purge: permanently deleted {} document(s) older than {} days",
                expired.size(), recycleBinRetentionDays);
    }

    /**
     * Cascades through every table that references a document before
     * deleting the document row itself — none of these foreign keys have
     * ON DELETE CASCADE, so this has to be explicit and in this order.
     */
    private void hardDeleteDocument(Document document) {
        Long documentId = document.getId();
        versionRepository.deleteByDocumentId(documentId);
        permissionRepository.deleteByDocumentId(documentId);
        starredDocumentRepository.deleteByDocumentId(documentId);
        recentDocumentViewRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
    }

    private String generateUniqueYjsRoomId() {
        String roomId;
        do {
            roomId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        } while (documentRepository.existsByYjsRoomId(roomId));
        return roomId;
    }

    @Transactional(readOnly = true)
    public byte[] getYjsSnapshot(String yjsRoomId) {
        try {
            // Validate input
            if (yjsRoomId == null || yjsRoomId.trim().isEmpty()) {
                throw new IllegalArgumentException("YJS room ID cannot be null or empty");
            }

            String cleanRoomId = yjsRoomId.trim();
            // Find document by YJS room ID
            Document document = documentRepository.findByYjsRoomIdAndIsDeletedFalse(cleanRoomId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Document not found or has been deleted for room ID: " + cleanRoomId));

            // Get the YJS snapshot
            byte[] snapshot = document.getYjsSnapshot();
            if (snapshot == null || snapshot.length == 0) {
                log.info("No YJS snapshot found for room ID: {}, returning empty snapshot", cleanRoomId);
                // Return empty byte array if no snapshot exists
                return new byte[0];
            }

            log.info("Retrieved YJS snapshot for room ID: {}, size: {} bytes",
                    cleanRoomId, snapshot.length);

            return snapshot;
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for YJS snapshot: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving YJS snapshot for room ID {}: {}", yjsRoomId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve YJS snapshot", e);
        }
    }

    public void updateTitle(Long documentId, User user, UpdateTitleRequest request) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId).
                orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if(!permissionService.hasPermission(documentId, user.getId(), Role.OWNER)){
            throw new PermissionDeniedException("Only owner can change title!");
        }

        document.setTitle(request.getTitle());
        documentRepository.save(document);
        log.info("Updated document {} title to: {}", documentId, request.getTitle());
    }
}
