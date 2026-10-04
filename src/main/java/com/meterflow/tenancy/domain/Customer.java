package com.meterflow.tenancy.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Customer() {
    }

    public Customer(String name, Instant createdAt) {
        this.name = name;
        this.status = CustomerStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CustomerStatus getStatus() {
        return status;
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

    public void rename(String name, Instant updatedAt) {
        this.name = name;
        this.updatedAt = updatedAt;
    }

    public void suspend(Instant updatedAt) {
        this.status = CustomerStatus.SUSPENDED;
        this.updatedAt = updatedAt;
    }

    public void activate(Instant updatedAt) {
        this.status = CustomerStatus.ACTIVE;
        this.updatedAt = updatedAt;
    }

}
