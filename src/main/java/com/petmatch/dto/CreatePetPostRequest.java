package com.petmatch.dto;

import jakarta.validation.constraints.*;

public class CreatePetPostRequest {

    @NotBlank(message = "帖子类型不能为空")
    @Pattern(regexp = "LOST|SIGHTING", message = "类型必须是 LOST 或 SIGHTING")
    private String type;

    @NotBlank(message = "物种不能为空")
    private String species;

    private String color;

    private String size;

    private Boolean hasCollar;

    private String description;

    @NotNull(message = "事发时间不能为空")
    private String eventTime;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度范围不正确")
    @DecimalMax(value = "90.0", message = "纬度范围不正确")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度范围不正确")
    @DecimalMax(value = "180.0", message = "经度范围不正确")
    private Double longitude;

    @NotBlank(message = "区域不能为空")
    private String publicArea;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public Boolean getHasCollar() { return hasCollar; }
    public void setHasCollar(Boolean hasCollar) { this.hasCollar = hasCollar; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getPublicArea() { return publicArea; }
    public void setPublicArea(String publicArea) { this.publicArea = publicArea; }
}