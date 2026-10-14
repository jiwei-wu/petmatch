package com.petmatch.dto;

import jakarta.validation.constraints.Size;

public class UpdatePetPostRequest {

    @Size(max = 50, message = "Colour must be at most 50 characters")
    private String color;

    @Size(max = 20, message = "Size must be at most 20 characters")
    private String size;

    private Boolean hasCollar;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    private String status;

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public Boolean getHasCollar() { return hasCollar; }
    public void setHasCollar(Boolean hasCollar) { this.hasCollar = hasCollar; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
