package com.dentalclinic.dto.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BlogForm {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title max 255 characters")
    private String title;

    @NotBlank(message = "Summary must not be blank")
    @Size(max = 1000, message = "Summary max 1000 characters")
    private String summary;

    @NotBlank(message = "Content must not be blank")
    private String content;

    @Size(max = 500, message = "Image URL max 500 characters")
    private String imageUrl;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}