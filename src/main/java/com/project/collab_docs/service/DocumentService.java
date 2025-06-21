package com.project.collab_docs.service;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.Visibility;
import com.project.collab_docs.repository.DocumentRepository;
import com.project.collab_docs.response.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.HTMLSettings;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.fit.pdfdom.PDFDomTree;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    // Default blank HTML content for new documents
    private static final String BLANK_HTML_CONTENT =
            "<div><p><br></p></div>";

    @Transactional
    public Document createBlankDocument(String title, User owner) {
        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        Document document = Document.builder()
                .title(title)
                .fileName(title + ".docx")
                .content(BLANK_HTML_CONTENT)
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .contentType("text/html") // Storing as HTML
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
                .contentType("text/html")
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

    /**
     * Extract content from DOCX file using docx4j library and convert to HTML
     * This preserves rich text formatting including bold, italic, fonts, colors, etc.
     */

    private String extractContentFromDocx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            // Load the DOCX document
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(inputStream);
            // Get the main document part
            MainDocumentPart mainDocumentPart = wordPackage.getMainDocumentPart();

            // Check if document has content
            if (mainDocumentPart == null || mainDocumentPart.getContent().isEmpty()) {
                log.warn("DOCX document appears to be empty, returning blank content");
                return BLANK_HTML_CONTENT;
            }

            // Configure HTML settings for conversion
            HTMLSettings htmlSettings = Docx4J.createHTMLSettings();
            htmlSettings.setImageDirPath("images"); // Directory for images (if any)
            htmlSettings.setImageTargetUri("images"); // URI for images
            htmlSettings.setOpcPackage(wordPackage);

            // Convert to HTML
            ByteArrayOutputStream htmlOutputStream = new ByteArrayOutputStream();
            Docx4J.toHTML(htmlSettings, htmlOutputStream, Docx4J.FLAG_EXPORT_PREFER_XSL);

            String htmlContent = htmlOutputStream.toString("UTF-8");
            // Clean up the HTML content
            String cleanedHtml = cleanUpHtmlContent(htmlContent);

            log.info("Successfully extracted and converted DOCX to HTML: {} characters", cleanedHtml.length());
            log.debug("HTML content preview: {}", cleanedHtml.substring(0, Math.min(500, cleanedHtml.length())));

            return cleanedHtml;
        } catch (Docx4JException e) {
            log.error("Error processing DOCX with docx4j: {}", e.getMessage(), e);
            throw new IOException("Failed to process DOCX file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error extracting content from DOCX: {}", e.getMessage(), e);
            throw new IOException("Failed to extract content from DOCX file", e);
        }
    }
    private String cleanUpHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return BLANK_HTML_CONTENT;
        }

        // Remove HTML document structure, keep only body content
        String bodyContent = extractBodyContent(htmlContent);
        // If no meaningful content found, return blank
        if (bodyContent.trim().isEmpty() || bodyContent.matches("\\s*<[^>]*>\\s*")) {
            return BLANK_HTML_CONTENT;
        }

        // Wrap in a div to ensure proper structure
        return "<div>" + bodyContent + "</div>";
    }
    private String extractBodyContent(String htmlContent) {
        if (htmlContent == null) {
            return "";
        }

        // Find body tag content
        String lowerHtml = htmlContent.toLowerCase();
        int bodyStart = lowerHtml.indexOf("<body");
        if (bodyStart == -1) {
            // No body tag, return content as is (might be fragment)
            return htmlContent;
        }

        // Find the end of opening body tag
        int bodyContentStart = htmlContent.indexOf('>', bodyStart) + 1;
        if (bodyContentStart <= bodyStart) {
            return htmlContent;
        }

        // Find closing body tag
        int bodyEnd = lowerHtml.lastIndexOf("</body>");
        if (bodyEnd == -1) {
            return htmlContent.substring(bodyContentStart);
        }

        return htmlContent.substring(bodyContentStart, bodyEnd);
    }

    /**
     * Extract rich text content from PDF file using PDF2Dom library
     * This preserves formatting including fonts, colors, positioning, etc.
     */
    private String extractContentFromPdf(MultipartFile file) throws IOException {

        PDDocument pdDocument = null;
        try(InputStream inputStream = file.getInputStream()){
          pdDocument = PDDocument.load(inputStream);

            // Check if document has pages
            if (pdDocument.getNumberOfPages() == 0) {
                log.warn("PDF document appears to be empty, returning blank content");
                return BLANK_HTML_CONTENT;
            }
            PDFDomTree pdfDomTree = new PDFDomTree();
            // Convert PDF to DOM
            StringWriter htmlWriter = new StringWriter();
            pdfDomTree.writeText(pdDocument, htmlWriter);

            String htmlContent = htmlWriter.toString();
            // Clean up and process the HTML content
            String cleanedHtml = cleanUpPdfHtmlContent(htmlContent);

            log.info("Successfully extracted and converted PDF to HTML: {} characters from {} pages",
                    cleanedHtml.length(), pdDocument.getNumberOfPages());
            log.debug("HTML content preview: {}", cleanedHtml.substring(0, Math.min(500, cleanedHtml.length())));

            return cleanedHtml;
        }catch (IOException e) {
            log.error("Error processing PDF file: {}", e.getMessage(), e);
            throw new IOException("Failed to process PDF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error extracting content from PDF: {}", e.getMessage(), e);
            throw new IOException("Failed to extract content from PDF file", e);
        } finally {
            // Ensure PDF document is properly closed
            if (pdDocument != null) {
                try {
                    pdDocument.close();
                } catch (IOException e) {
                    log.warn("Error closing PDF document: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Clean up HTML content extracted from PDF
     * PDF2Dom generates HTML with specific styling that needs to be processed
     */
    private String cleanUpPdfHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return BLANK_HTML_CONTENT;
        }
        try {
            // Remove unnecessary whitespace and clean up the HTML
            String cleanedContent = htmlContent
                    .replaceAll("\\s+", " ") // Replace multiple whitespaces with single space
                    .replaceAll("(?i)<meta[^>]*>", "") // Remove meta tags
                    .replaceAll("(?i)<title[^>]*>.*?</title>", "") // Remove title tags
                    .trim();
            // Extract body content if present
            String bodyContent = extractBodyContent(cleanedContent);

            // If no meaningful content found, return blank
            if (bodyContent.trim().isEmpty() ||
                    bodyContent.matches("\\s*<[^>]*>\\s*") ||
                    bodyContent.replace("&nbsp;", "").trim().isEmpty()) {
                return BLANK_HTML_CONTENT;
            }

            // Wrap in a div to ensure proper structure for the editor
            return "<div>" + bodyContent + "</div>";
        }catch (Exception e) {
            log.warn("Error cleaning up PDF HTML content, returning original: {}", e.getMessage());
            return "<div>" + htmlContent + "</div>";
        }
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
