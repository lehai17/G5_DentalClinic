package com.dentalclinic.controller.staff;

import com.dentalclinic.dto.chat.ChatMessageDto;
import com.dentalclinic.dto.chat.ChatThreadDto;
import com.dentalclinic.dto.chat.SendChatMessageRequest;
import com.dentalclinic.model.user.User;
import com.dentalclinic.service.chat.ChatService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/staff/chat")
@PreAuthorize("hasAnyRole('STAFF','ADMIN')")
public class StaffChatController {

    private final ChatService chatService;

    public StaffChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public String chatInbox(@AuthenticationPrincipal UserDetails principal, Model model) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        model.addAttribute("pageTitle", "Chat lễ tân");
        model.addAttribute("threads", chatService.getStaffInbox(current.getId()));
        return "staff/chat";
    }

    @GetMapping("/{threadId}")
    @ResponseBody
    public ResponseEntity<List<ChatMessageDto>> getThreadMessages(@PathVariable Long threadId,
                                                                  @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        List<ChatMessageDto> messages = chatService.getMessagesForStaff(current.getId(), threadId);
        chatService.markMessagesReadForStaff(current.getId(), threadId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping(value = "/{threadId}/reply", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ChatMessageDto> reply(@PathVariable Long threadId,
                                                @RequestBody SendChatMessageRequest request,
                                                @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        String content = request != null ? request.getContent() : null;
        return ResponseEntity.ok(chatService.sendStaffMessage(current.getId(), threadId, content));
    }

    @PostMapping(value = "/{threadId}/reply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<ChatMessageDto> replyWithAttachment(@PathVariable Long threadId,
                                                              @RequestParam(required = false) String content,
                                                              @RequestParam(value = "attachment", required = false) MultipartFile attachment,
                                                              @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        return ResponseEntity.ok(chatService.sendStaffMessage(current.getId(), threadId, content, attachment));
    }

    @GetMapping("/attachments/{messageId}")
    @ResponseBody
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long messageId,
                                                       @AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        var download = chatService.getAttachmentForStaff(current.getId(), messageId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.getContentType() != null && !download.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(download.getContentType());
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(download.getSize())
                .body(download.getResource());
    }

    @GetMapping("/inbox/data")
    @ResponseBody
    public ResponseEntity<List<ChatThreadDto>> inboxData(@AuthenticationPrincipal UserDetails principal) {
        User current = chatService.getCurrentUserByEmail(principal.getUsername());
        return ResponseEntity.ok(chatService.getStaffInbox(current.getId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}

