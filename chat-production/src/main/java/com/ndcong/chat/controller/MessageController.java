package com.ndcong.chat.controller;

import com.ndcong.chat.dto.message.ChatMessageRequest;
import com.ndcong.chat.entity.Message;
import com.ndcong.chat.entity.MessageReaction;
import com.ndcong.chat.entity.MessageStatus;
import com.ndcong.chat.repository.MessageRepository;
import com.ndcong.chat.repository.MessageReactionRepository;
import com.ndcong.chat.repository.MessageStatusRepository;
import com.ndcong.chat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

	@Autowired
	private MessageService messageService;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private MessageReactionRepository reactionRepository;

	@Autowired
	private MessageStatusRepository statusRepository;

	// Send message via REST (alternative to WebSocket)
	@PostMapping
	public ResponseEntity<?> sendMessage(@RequestBody ChatMessageRequest req) {
		Message saved = messageService.processAndSaveMessage(req);
		// broadcast to subscribers of the conversation
		try {
			messagingTemplate.convertAndSend("/topic/conversation/" + saved.getConversationId(), saved);
		} catch (Exception ignored) {}
		return ResponseEntity.ok(saved);
	}

	@PostMapping("/{id}/reactions")
	public ResponseEntity<?> addReaction(@PathVariable UUID id,
										 @RequestBody java.util.Map<String, String> body,
										 @org.springframework.security.core.annotation.AuthenticationPrincipal com.ndcong.chat.security.UserPrincipal currentUser) {
		String reaction = body.get("reaction");
		if (reaction == null) return ResponseEntity.badRequest().body("reaction required");
		MessageReaction r = MessageReaction.builder()
				.messageId(id)
				.userId(currentUser != null ? currentUser.getId() : null)
				.reaction(reaction)
				.build();
		MessageReaction saved = reactionRepository.save(r);
		return ResponseEntity.ok(saved);
	}

	@PostMapping("/{id}/status")
	public ResponseEntity<?> setStatus(@PathVariable UUID id,
									   @RequestBody java.util.Map<String, String> body,
									   @org.springframework.security.core.annotation.AuthenticationPrincipal com.ndcong.chat.security.UserPrincipal currentUser) {
		String status = body.get("status");
		if (status == null) return ResponseEntity.badRequest().body("status required");
		MessageStatus s = MessageStatus.builder()
				.messageId(id)
				.userId(currentUser != null ? currentUser.getId() : null)
				.status(status)
				.build();
		MessageStatus saved = statusRepository.save(s);
		return ResponseEntity.ok(saved);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getMessage(@PathVariable UUID id) {
		Optional<Message> opt = messageRepository.findById(id);
		return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updateMessage(@PathVariable UUID id, @RequestBody ChatMessageRequest req) {
		Optional<Message> opt = messageRepository.findById(id);
		if (opt.isEmpty()) return ResponseEntity.notFound().build();
		Message m = opt.get();
		if (req.getContent() != null) m.setContent(req.getContent());
		if (req.getMessageType() != null) m.setMessageType(req.getMessageType());
		messageRepository.save(m);
		return ResponseEntity.ok(m);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteMessage(@PathVariable UUID id) {
		if (!messageRepository.existsById(id)) return ResponseEntity.notFound().build();
		messageRepository.deleteById(id);
		return ResponseEntity.ok().build();
	}
}

