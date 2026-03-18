package com.dentalclinic.controller.staff;

import com.dentalclinic.dto.review.UpdateReviewFeatureRequest;
import com.dentalclinic.service.review.ReviewMarketingService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/staff/reviews")
public class StaffReviewController {

    private final ReviewMarketingService reviewMarketingService;

    public StaffReviewController(ReviewMarketingService reviewMarketingService) {
        this.reviewMarketingService = reviewMarketingService;
    }

    @GetMapping
    public String reviewManagementPage(Model model) {
        model.addAttribute("reviews", reviewMarketingService.getAllReviewsForStaff());
        return "staff/reviews";
    }

    @PostMapping("/{id}/feature")
    @ResponseBody
    public ResponseEntity<?> updateReviewFeature(@PathVariable Long id,
                                                 @RequestBody UpdateReviewFeatureRequest request) {
        reviewMarketingService.updateReviewFeature(id, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã cập nhật trạng thái review."
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        reviewMarketingService.deleteReview(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa đánh giá."
        ));
    }
}