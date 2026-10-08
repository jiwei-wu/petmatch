package com.petmatch.domain;

import jakarta.persistence.*;


import java.time.OffsetDateTime;

@Entity
@Table(name = "pet_posts")
public class PetPost {

    public enum Type { LOST, SIGHTING }
    public enum Status { OPEN, MATCHED, RESOLVED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String species;

    private String color;

    private String size;

    @Column(name = "has_collar")
    private Boolean hasCollar;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "microchip_hash")
    private String microchipHash;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

   @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;
    
    @Column(name = "public_area", nullable = false)
    private String publicArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected PetPost() {
    }

    public PetPost(Long userId, Type type, String species, String color, String size,
                Boolean hasCollar, String description, String microchipHash,
                OffsetDateTime eventTime, Double latitude, Double longitude, String publicArea,
                OffsetDateTime expiresAt) {
        this.userId = userId;
        this.type = type;
        this.species = species;
        this.color = color;
        this.size = size;
        this.hasCollar = hasCollar;
        this.description = description;
        this.microchipHash = microchipHash;
        this.eventTime = eventTime;
        this.latitude = latitude;
        this.longitude = longitude;
        this.publicArea = publicArea;
        this.status = Status.OPEN;
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }

    // Getters
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Type getType() { return type; }
    public String getSpecies() { return species; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public Boolean getHasCollar() { return hasCollar; }
    public String getDescription() { return description; }
    public String getMicrochipHash() { return microchipHash; }
    public OffsetDateTime getEventTime() { return eventTime; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getPublicArea() { return publicArea; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }

    // 状态变更的专用方法(不是无脑 setter)
    public void markResolved() {
        this.status = Status.RESOLVED;
    }

    public void markExpired() {
        this.status = Status.EXPIRED;
    }
}