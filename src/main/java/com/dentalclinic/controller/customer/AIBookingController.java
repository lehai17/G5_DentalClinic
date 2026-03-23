package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.ai.AIBookingRequest;
import com.dentalclinic.dto.ai.AIBookingSuggestionResponse;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.ai.AIBookingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/customer/ai-booking")
public class AIBookingController {

    private static final String SESSION_USER_ID = "userId";

    private final AIBookingService aiBookingService;
    private final UserRepository userRepository;

    public AIBookingController(AIBookingService aiBookingService,
                               UserRepository userRepository) {
        this.aiBookingService = aiBookingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/suggest")
    public ResponseEntity<?> suggest(@Valid @RequestBody AIBookingRequest request,
                                     HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Chưa đăng nhập."));
        }

        AIBookingSuggestionResponse response = aiBookingService.suggest(userId, request);
        return ResponseEntity.ok(response);
    }

    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        Long userId = null;
        if (uid instanceof Long) userId = (Long) uid;
        else if (uid instanceof Number) userId = ((Number) uid).longValue();

        if (userId == null || !userRepository.existsById(userId)) {
            return null;
        }
        return userId;
    }
}
