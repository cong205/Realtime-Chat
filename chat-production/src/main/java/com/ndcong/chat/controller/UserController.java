package com.ndcong.chat.controller;

import com.ndcong.chat.entity.User;
import com.ndcong.chat.repository.UserRepository;
import com.ndcong.chat.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// Simple request classes to avoid adding extra DTO files
	public static class RegisterRequest {
		public String username;
		public String email;
		public String password;
	}

	public static class UpdateProfileRequest {
		public String fullName;
		public String avatarUrl;
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
		if (req.username == null || req.password == null || req.email == null) {
			return ResponseEntity.badRequest().body("username, email and password are required");
		}

		if (userRepository.findByUsername(req.username).isPresent()) {
			return ResponseEntity.badRequest().body("Username already taken");
		}

		User u = User.builder()
				.username(req.username)
				.email(req.email)
				.passwordHash(passwordEncoder.encode(req.password))
				.fullName(Objects.toString(req.username, ""))
				.build();

		User saved = userRepository.save(u);

		Map<String, Object> resp = new HashMap<>();
		resp.put("id", saved.getId());
		resp.put("username", saved.getUsername());
		resp.put("email", saved.getEmail());
		resp.put("fullName", saved.getFullName());
		resp.put("avatarUrl", saved.getAvatarUrl());

		return ResponseEntity.ok(resp);
	}

	@GetMapping("/me")
	public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserPrincipal currentUser) {
		if (currentUser == null) return ResponseEntity.status(401).body("Unauthorized");
		Optional<User> opt = userRepository.findById(currentUser.getId());
		if (opt.isEmpty()) return ResponseEntity.notFound().build();
		User u = opt.get();
		Map<String, Object> resp = new HashMap<>();
		resp.put("id", u.getId());
		resp.put("username", u.getUsername());
		resp.put("email", u.getEmail());
		resp.put("fullName", u.getFullName());
		resp.put("avatarUrl", u.getAvatarUrl());
		return ResponseEntity.ok(resp);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getById(@PathVariable UUID id) {
		return userRepository.findById(id)
				.map(u -> {
					Map<String, Object> resp = new HashMap<>();
					resp.put("id", u.getId());
					resp.put("username", u.getUsername());
					resp.put("email", u.getEmail());
					resp.put("fullName", u.getFullName());
					resp.put("avatarUrl", u.getAvatarUrl());
					return ResponseEntity.ok(resp);
				})
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PutMapping("/me")
	public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserPrincipal currentUser,
										   @RequestBody UpdateProfileRequest req) {
		if (currentUser == null) return ResponseEntity.status(401).body("Unauthorized");
		Optional<User> opt = userRepository.findById(currentUser.getId());
		if (opt.isEmpty()) return ResponseEntity.notFound().build();
		User u = opt.get();
		if (req.fullName != null) u.setFullName(req.fullName);
		if (req.avatarUrl != null) u.setAvatarUrl(req.avatarUrl);
		userRepository.save(u);
		Map<String, Object> resp = new HashMap<>();
		resp.put("id", u.getId());
		resp.put("username", u.getUsername());
		resp.put("email", u.getEmail());
		resp.put("fullName", u.getFullName());
		resp.put("avatarUrl", u.getAvatarUrl());
		return ResponseEntity.ok(resp);
	}

	@GetMapping("/search")
	public ResponseEntity<?> search(@RequestParam("q") String q) {
		if (q == null || q.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
		// Simple search implementation: scan all users and filter (TODO: add indexed search)
		List<User> all = userRepository.findAll();
		List<Map<String, Object>> found = all.stream()
				.filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(q.toLowerCase())
						|| (u.getEmail() != null && u.getEmail().toLowerCase().contains(q.toLowerCase())))
				.map(u -> {
					Map<String, Object> m = new HashMap<>();
					m.put("id", u.getId());
					m.put("username", u.getUsername());
					m.put("email", u.getEmail());
					m.put("fullName", u.getFullName());
					m.put("avatarUrl", u.getAvatarUrl());
					return m;
				})
				.collect(Collectors.toList());
		return ResponseEntity.ok(found);
	}
}

