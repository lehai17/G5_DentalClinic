package com.dentalclinic.service.review;

import com.dentalclinic.dto.review.HomepageReviewDto;
import com.dentalclinic.dto.review.StaffReviewManagementDto;
import com.dentalclinic.dto.review.UpdateReviewFeatureRequest;
import com.dentalclinic.exception.BookingErrorCode;
import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.review.Review;
import com.dentalclinic.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewMarketingService {

    private final ReviewRepository reviewRepository;

    public ReviewMarketingService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<HomepageReviewDto> getHomepageFeaturedReviews() {
        return reviewRepository.findFeaturedHomepageReviews()
                .stream()
                .map(this::toHomepageDto)
                .limit(6)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StaffReviewManagementDto> getAllReviewsForStaff() {
        return reviewRepository.findAllOrderByCreatedAtDesc()
                .stream()
                .map(this::toStaffDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateReviewFeature(Long reviewId, UpdateReviewFeatureRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BookingException(
                        BookingErrorCode.APPOINTMENT_NOT_FOUND,
                        "Không tìm thấy đánh giá."
                ));

        if (request.getFeaturedOnHomepage() != null) {
            review.setFeaturedOnHomepage(request.getFeaturedOnHomepage());
        }

        if (request.getDisplayOrder() != null) {
            review.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getHiddenCustomerName() != null) {
            review.setHiddenCustomerName(request.getHiddenCustomerName());
        }

        reviewRepository.save(review);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BookingException(
                        BookingErrorCode.APPOINTMENT_NOT_FOUND,
                        "Không tìm thấy đánh giá để xóa."
                ));
        reviewRepository.delete(review);
    }

    private HomepageReviewDto toHomepageDto(Review review) {
        HomepageReviewDto dto = new HomepageReviewDto();
        dto.setId(review.getId());
        dto.setCustomerName(formatCustomerName(review));
        dto.setDentistName(review.getDentist() != null ? review.getDentist().getFullName() : "");
        dto.setServiceName(review.getService() != null ? review.getService().getName() : "");
        dto.setDentistRating(review.getDentistRating());
        dto.setServiceRating(review.getServiceRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }

    private StaffReviewManagementDto toStaffDto(Review review) {
        StaffReviewManagementDto dto = new StaffReviewManagementDto();
        dto.setId(review.getId());
        dto.setAppointmentId(review.getAppointment() != null ? review.getAppointment().getId() : null);
        dto.setCustomerName(review.getCustomer() != null ? review.getCustomer().getFullName() : "");
        dto.setDentistName(review.getDentist() != null ? review.getDentist().getFullName() : "");
        dto.setServiceName(review.getService() != null ? review.getService().getName() : "");
        dto.setDentistRating(review.getDentistRating());
        dto.setServiceRating(review.getServiceRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setFeaturedOnHomepage(review.isFeaturedOnHomepage());
        dto.setDisplayOrder(review.getDisplayOrder());
        dto.setHiddenCustomerName(review.isHiddenCustomerName());
        return dto;
    }

    private String formatCustomerName(Review review) {
        String fullName = review.getCustomer() != null ? review.getCustomer().getFullName() : "Khách hàng";
        if (!review.isHiddenCustomerName()) {
            return fullName;
        }

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) return "Khách hàng";
        if (parts.length == 1) return parts[0].charAt(0) + "***";

        StringBuilder sb = new StringBuilder(parts[0]);
        sb.append(" ");
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                sb.append(parts[i].charAt(0)).append(".");
                if (i < parts.length - 1) sb.append(" ");
            }
        }
        return sb.toString();
    }
}