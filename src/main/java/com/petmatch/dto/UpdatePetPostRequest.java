package com.petmatch.dto;

import jakarta.validation.constraints.Size;

public class UpdatePetPostRequest {

    @Size(max = 50, message = "毛色不能超过 50 个字符")
    private String color;

    @Size(max = 20, message = "体型不能超过 20 个字符")
    private String size;

    private Boolean hasCollar;

    @Size(max = 2000, message = "描述不能超过 2000 个字符")
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
