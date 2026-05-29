package com.ndcong.chat.controller;

import com.ndcong.chat.entity.MessageAttachment;
import com.ndcong.chat.repository.MessageAttachmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private MessageAttachmentRepository attachmentRepository;

    @GetMapping("/{id}")
    public ResponseEntity<?> getAttachment(@PathVariable UUID id) {
        return attachmentRepository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
