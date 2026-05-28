package com.ndcong.chat.controller;

import com.ndcong.chat.entity.MessageAttachment;
import com.ndcong.chat.repository.MessageAttachmentRepository;
import com.ndcong.chat.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private MessageAttachmentRepository attachmentRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("messageId") UUID messageId) {
        
        try {
            // 1. Lưu file xuống ổ cứng
            String fileUrl = fileStorageService.storeFile(file);

            // 2. Phân tích thông tin file
            String contentType = file.getContentType();
            String fileType = "file";
            if (contentType != null) {
                if (contentType.startsWith("image/")) fileType = "image";
                else if (contentType.startsWith("video/")) fileType = "video";
            }

            // 3. Lưu thông tin vào SQL Server
            MessageAttachment attachment = MessageAttachment.builder()
                    .messageId(messageId)
                    .fileName(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .fileType(fileType)
                    .mimeType(contentType)
                    .fileSize(file.getSize())
                    .build();
            
            attachmentRepository.save(attachment);

            // Trả về thông tin cho Client
            return ResponseEntity.ok(attachment);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body("Không thể upload file: " + ex.getMessage());
        }
    }
}