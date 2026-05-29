package com.ndcong.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple handler for /favicon.ico requests to avoid 500 errors when the file is missing.
 */
@RestController
public class FaviconController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        // Return 204 No Content so browsers stop requesting and no exception is thrown
        return ResponseEntity.noContent().build();
    }
}
