package com.petmatch.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

public class CreatePetPostRequest {

    @NotBlank(message = "Post type is required")
    @Pattern(regexp = "LOST|SIGHTING", message = "Type must be LOST or SIGHTING")
    private String type;

    @NotBlank(message = "Species is required")
    private String species;

    private String color;

    private String size;

    private Boolean hasCollar;

    private String description;

    @NotNull(message = "Event time is required")
    private OffsetDateTime eventTime;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @NotBlank(message = "Public area is required")
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
    public OffsetDateTime getEventTime() { return eventTime; }
    public void setEventTime(OffsetDateTime eventTime) { this.eventTime = eventTime; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getPublicArea() { return publicArea; }
    public void setPublicArea(String publicArea) { this.publicArea = publicArea; }
}