package com.petmatch.dto;

import java.time.OffsetDateTime;

public class PetPostResponse {

    private Long id;
    private String type;
    private String species;
    private String color;
    private String size;
    private Boolean hasCollar;
    private String description;
    private OffsetDateTime eventTime;
    private String publicArea;
    private String status;
    private OffsetDateTime createdAt;

    public PetPostResponse(Long id, String type, String species, String color, String size,
                            Boolean hasCollar, String description, OffsetDateTime eventTime,
                            String publicArea, String status, OffsetDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.species = species;
        this.color = color;
        this.size = size;
        this.hasCollar = hasCollar;
        this.description = description;
        this.eventTime = eventTime;
        this.publicArea = publicArea;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getSpecies() { return species; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public Boolean getHasCollar() { return hasCollar; }
    public String getDescription() { return description; }
    public OffsetDateTime getEventTime() { return eventTime; }
    public String getPublicArea() { return publicArea; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}