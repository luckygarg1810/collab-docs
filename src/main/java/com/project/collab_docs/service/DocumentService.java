package com.project.collab_docs.service;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.Visibility;
import com.project.collab_docs.repository.DocumentRepository;
import com.project.collab_docs.response.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private static final String BLANK_TIPTAP_JSON =
            "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"\"}]}]}";


    @Transactional
    public Document createBlankDocument(String title, User owner) {
        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        Document document = Document.builder()
                .title(title)
                .content(BLANK_TIPTAP_JSON)
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .contentType("application/json") // Tiptap JSON
                .fileSize(0L)
                .visibility(Visibility.PRIVATE) // Default to private
                .isDeleted(false)
                .build();

        Document savedDocument = documentRepository.save(document);
        log.info("Created blank document with ID: {} for user: {}", savedDocument.getId(), owner.getEmail());

        return savedDocument;
    }

    @Transactional
    public Document uploadDocument(MultipartFile file, String title, User owner) throws IOException {
        // Validate file
        validateUploadedFile(file);

        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        // Extract content based on file type
        String content = extractContentFromFile(file);

        Document document = Document.builder()
                .title(title != null && !title.trim().isEmpty() ? title : getFileNameWithoutExtension(file.getOriginalFilename()))
                .fileName(!file.getOriginalFilename().isEmpty() ? file.getOriginalFilename() : "Untitled File")
                .contentType(file.getContentType())
                .content(content)
                .fileSize(file.getSize())
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .visibility(Visibility.PRIVATE) // Default to private
                .isDeleted(false)
                .build();

        Document savedDocument = documentRepository.save(document);
        log.info("Uploaded document with ID: {} for user: {}", savedDocument.getId(), owner.getEmail());

        return savedDocument;
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (!contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") && !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Only DOCX and PDF files are supported");
        }

        // 20MB limit
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("File size cannot exceed 10MB");
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
                .build();
    }


    private String extractContentFromFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();

        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)) {
            return extractContentFromDocx(file);
        } else if ("application/pdf".equals(contentType)) {
            return extractContentFromPdf(file);
        }
        throw new IllegalArgumentException("Unsupported file type: " + contentType);
    }

    private String extractContentFromDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            StringBuilder tiptapJson = new StringBuilder();
            tiptapJson.append("{\"type\":\"doc\",\"content\":[");

            boolean firstParagraph = true;
            boolean hasContent = false;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();

                // Skip empty paragraphs but preserve structure
                if (text == null) {
                    text = "";
                }

                if (!firstParagraph) {
                    tiptapJson.append(",");
                }
                firstParagraph = false;

                // Create paragraph with proper JSON escaping
                tiptapJson.append("{\"type\":\"paragraph\",\"content\":[");

                if (!text.trim().isEmpty()) {
                    tiptapJson.append("{\"type\":\"text\",\"text\":\"")
                            .append(escapeJson(text))
                            .append("\"}");
                    hasContent = true;
                } else {
                    // Empty paragraph
                    tiptapJson.append("{\"type\":\"text\",\"text\":\"\"}");
                }

                tiptapJson.append("]}");
            }

            tiptapJson.append("]}");

            // If no content was found, return blank document
            if (!hasContent) {
                log.warn("No content extracted from DOCX file, returning blank document");
                return BLANK_TIPTAP_JSON;
            }

            String result = tiptapJson.toString();
            log.info("Extracted content from DOCX: {} characters", result.length());
            log.debug("DOCX content preview: {}", result.substring(0, Math.min(200, result.length())));

            return result;
        } catch (Exception e) {
            log.error("Error extracting content from DOCX: {}", e.getMessage(), e);
            throw new IOException("Failed to extract content from DOCX file", e);
        }
    }

    private String extractContentFromPdf(MultipartFile file) throws IOException {
        // For now, return a placeholder since PDF text extraction requires additional libraries
        // In a real implementation, you would use libraries like PDFBox or iText
        log.warn("PDF content extraction not fully implemented. Creating placeholder content.");

        return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"PDF content extraction is not yet implemented. Please convert to DOCX format.\"}]}]}";
    }

    private String escapeJson(String text) {
        if (text == null) return "";

        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

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
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (!document.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Only the document owner can change visibility");
        }

        if (!isValidVisibility(visibility)) {
            throw new IllegalArgumentException("Invalid visibility value. Must be 'private', 'shared', or 'public'");
        }

        document.setVisibility(visibility);
        documentRepository.save(document);
        log.info("Updated document {} visibility to: {}", documentId, visibility);
    }

    private boolean isValidVisibility(Visibility visibility) {
        return Visibility.PRIVATE.equals(visibility) || Visibility.PUBLIC.equals(visibility) || Visibility.SHARED.equals(visibility);
    }

    @Transactional
    public void softDeleteDocument(Long documentId, User user) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Check if user is the owner of the document
        if (!document.getOwner().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Only the document owner can delete this document");
        }

        // Perform soft delete
        document.setIsDeleted(true);
        documentRepository.save(document);

        log.info("Soft deleted document with ID: {} by user: {}", documentId, user.getEmail());
    }


    private String generateUniqueYjsRoomId() {
        String roomId;
        do {
            roomId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        } while (documentRepository.existsByYjsRoomId(roomId));
        return roomId;
    }
}
