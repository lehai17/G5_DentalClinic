package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateReviewRequest {
    @NotNull(message = "Vui lòng chọn số sao đánh giá.")
    @Min(value = 1, message = "Số sao phải từ 1 đến 5.")
    @Max(value = 5, message = "Số sao phải từ 1 đến 5.")
    private Integer rating;

    @Size(max = 500, message = "Nội dung đánh giá tối đa 500 ký tự.")
    private String comment;

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
