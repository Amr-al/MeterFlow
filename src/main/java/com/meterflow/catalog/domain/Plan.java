package com.meterflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "requests_per_minute", nullable = false)
    private int requestsPerMinute;

    @Column(name = "monthly_quota", nullable = false)
    private long monthlyQuota;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Plan() {
    }

    public Plan(
            String code,
            String displayName,
            int requestsPerMinute,
            long monthlyQuota,
            Instant createdAt) {

        this.code = code;
        this.displayName = displayName;
        this.requestsPerMinute = requestsPerMinute;
        this.monthlyQuota = monthlyQuota;
        this.active = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public long getMonthlyQuota() {
        return monthlyQuota;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void rename(String displayName, Instant updatedAt) {
        this.displayName = displayName;
        this.updatedAt = updatedAt;
    }

    public void updateLimits(
            int requestsPerMinute,
            long monthlyQuota,
            Instant updatedAt) {

        this.requestsPerMinute = requestsPerMinute;
        this.monthlyQuota = monthlyQuota;
        this.updatedAt = updatedAt;
    }

    public void activate(Instant updatedAt) {
        this.active = true;
        this.updatedAt = updatedAt;
    }

    public void deactivate(Instant updatedAt) {
        this.active = false;
        this.updatedAt = updatedAt;
    }
}