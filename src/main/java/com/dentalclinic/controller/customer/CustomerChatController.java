package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.chat.ChatMessageDto;
import com.dentalclinic.dto.chat.ChatThreadDto;
import com.dentalclinic.dto.chat.SendChatMessageRequest;
import com.dentalclinic.model.user.User;
import com.dentalclinic.service.chat.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/customer/chat")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerChatController {

    private final ChatService chatService;

    public CustomerChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/thread")
    public ResponseEntity<ChatThreadDto> getOrCreateThread(@AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        return ResponseEntity.ok(chatService.createOrGetThread(current.getId()));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(@RequestParam Long threadId,
                                                            @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        List<ChatMessageDto> messages = chatService.getMessagesForCustomer(current.getId(), threadId);
        chatService.markMessagesReadForCustomer(current.getId(), threadId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/send")
    public ResponseEntity<ChatMessageDto> sendMessage(@RequestBody SendChatMessageRequest request,
                                                      @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        if (request == null || request.getThreadId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(chatService.sendCustomerMessage(current.getId(), request.getThreadId(), request.getContent()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@RequestParam Long threadId,
                                                         @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        long unread = chatService.getUnreadCountForCustomer(current.getId(), threadId);
        return ResponseEntity.ok(Map.of("unreadCount", unread));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
