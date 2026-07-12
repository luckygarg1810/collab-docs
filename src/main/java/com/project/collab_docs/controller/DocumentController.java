package com.project.collab_docs.controller;

import com.project.collab_docs.dto.request.UpdateTitleRequest;
import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.dto.request.CreateDocumentRequest;
import com.project.collab_docs.dto.request.UpdateVisibilityRequest;
import com.project.collab_docs.dto.response.DocumentResponse;
import com.project.collab_docs.dto.response.MessageResponse;
import com.project.collab_docs.enums.DocumentFilter;
import com.project.collab_docs.security.CustomUserDetails;
import com.project.collab_docs.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<?> getDocument(@RequestParam(value = "document_id") Long documentId,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        Document document = documentService.getDocumentById(documentId, user);
        // Pass userId so response includes the caller's effective role
        DocumentResponse response = documentService.mapToDocumentResponse(document, user.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/documents")
    public ResponseEntity<?> getUserDocuments(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") DocumentFilter filter,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();
        Page<DocumentResponse> documents = documentService.getUserDocuments(
                user, filter, page, size);

        return ResponseEntity.ok(documents);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createBlankDocument(
            @Valid @RequestBody CreateDocumentRequest request,
            Authentication authentication) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getId(); // Use getId() method from CustomUserDetails

            String title = request.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = "Untitled Document";
            }

            Document document = documentService.createBlankDocument(title.trim(), userId);
            DocumentResponse response = documentService.mapToDocumentResponse(document);
            log.info("Created blank document '{}' for user ID: {}", title, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating blank document: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to create document!"));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            Authentication authentication) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            Document document = documentService.uploadDocument(file, title, user);
            DocumentResponse response = documentService.mapToDocumentResponse(document);
            log.info("Uploaded document '{}' for user: {}", document.getTitle(), user.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Document upload validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (IOException e) {
            log.error("Error processing uploaded file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to process uploaded file!"));
        } catch (Exception e) {
            log.error("Error uploading document: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to upload document!"));
        }
    }

    @PutMapping("/{documentId}/visibility")
    public ResponseEntity<?> updateDocumentVisibility(
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateVisibilityRequest request,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        documentService.updateDocumentVisibility(documentId, request.getVisibility(), user);
        log.info("Updated document {} visibility to: {} by user: {}",
                documentId, request.getVisibility(), user.getEmail());
        return ResponseEntity.ok(new MessageResponse("Document visibility updated successfully!"));
    }

    @PutMapping("/{documentId}/title")
    public ResponseEntity<?> editTitle(@Valid @RequestBody UpdateTitleRequest request,
                                       @PathVariable Long documentId, Authentication authentication){
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        documentService.updateTitle(documentId, user, request);
        log.info("Updated document {} title to: {} by user: {}",
                documentId, request.getTitle(), user.getEmail());
        return ResponseEntity.ok(new MessageResponse("Document title updated successfully"));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable Long documentId,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        documentService.softDeleteDocument(documentId, user);
        log.info("Deleted document {} by user: {}", documentId, user.getEmail());
        return ResponseEntity.ok(new MessageResponse("Document deleted successfully!"));
    }

    @PostMapping("/yjs-snapshot")
    public ResponseEntity<?> saveYjsSnapshot(
            @RequestParam("yjsRoomId") String yjsRoomId,
            @RequestBody byte[] snapshot) {
        try {
            documentService.saveYjsSnapshot(yjsRoomId, snapshot);
            log.info("Saved Yjs snapshot for room: {} (size: {} bytes)", yjsRoomId, snapshot.length);
            return ResponseEntity.ok(new MessageResponse("Yjs snapshot saved successfully!"));

        } catch (Exception e) {
            log.error("Error saving Yjs snapshot: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to save Yjs snapshot!"));
        }
    }

    @GetMapping("/yjs-snapshot/{yjsRoomId}")
    public ResponseEntity<?> getYjsSnapshot(@PathVariable String yjsRoomId) {
        try {
            byte[] snapshot = documentService.getYjsSnapshot(yjsRoomId);

            // Return 204 No Content if snapshot is empty
            if (snapshot == null || snapshot.length == 0) {
                log.info("No snapshot found for room: {}", yjsRoomId);
                return ResponseEntity.noContent().build();
            }

            log.info("Retrieved Yjs snapshot for room: {} (size: {} bytes)", yjsRoomId, snapshot.length);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .body(snapshot);
        } catch (IllegalArgumentException e) {
            log.warn("Yjs snapshot retrieval failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving Yjs snapshot: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to retrieve snapshot!"));
        }
    }
}
