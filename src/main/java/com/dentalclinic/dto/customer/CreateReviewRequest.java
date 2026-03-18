package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateReviewRequest {

    @NotNull(message = "Vui lòng chọn số sao đánh giá bác sĩ.")
    @Min(value = 1, message = "Số sao đánh giá bác sĩ phải từ 1 đến 5.")
    @Max(value = 5, message = "Số sao đánh giá bác sĩ phải từ 1 đến 5.")
    private Integer dentistRating;

    @NotNull(message = "Vui lòng chọn số sao đánh giá dịch vụ.")
    @Min(value = 1, message = "Số sao đánh giá dịch vụ phải từ 1 đến 5.")
    @Max(value = 5, message = "Số sao đánh giá dịch vụ phải từ 1 đến 5.")
    private Integer serviceRating;

    @Size(max = 500, message = "Nội dung đánh giá tối đa 500 ký tự.")
    private String comment;

    public Integer getDentistRating() {
        return dentistRating;
    }

    public void setDentistRating(Integer dentistRating) {
        this.dentistRating = dentistRating;
    }

    public Integer getServiceRating() {
        return serviceRating;
    }

    public void setServiceRating(Integer serviceRating) {
        this.serviceRating = serviceRating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}