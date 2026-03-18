package com.dentalclinic.dto.review;

public class UpdateReviewFeatureRequest {
    private Boolean featuredOnHomepage;
    private Integer displayOrder;
    private Boolean hiddenCustomerName;

    public Boolean getFeaturedOnHomepage() {
        return featuredOnHomepage;
    }

    public void setFeaturedOnHomepage(Boolean featuredOnHomepage) {
        this.featuredOnHomepage = featuredOnHomepage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getHiddenCustomerName() {
        return hiddenCustomerName;
    }

    public void setHiddenCustomerName(Boolean hiddenCustomerName) {
        this.hiddenCustomerName = hiddenCustomerName;
    }
}