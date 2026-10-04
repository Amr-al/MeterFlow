package com.meterflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "plan_entitlement")
public class PlanEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_product_id", nullable = false)
    private ApiProduct apiProduct;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "credit_cost", nullable = false)
    private long creditCost;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PlanEntitlement() {
    }

    public PlanEntitlement(
            Plan plan,
            ApiProduct apiProduct,
            boolean allowed,
            long creditCost) {

        this.plan = plan;
        this.apiProduct = apiProduct;
        this.allowed = allowed;
        this.creditCost = creditCost;
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public ApiProduct getApiProduct() {
        return apiProduct;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getCreditCost() {
        return creditCost;
    }

    public Long getVersion() {
        return version;
    }

    public void updateEntitlement(
            boolean allowed,
            long creditCost) {

        this.allowed = allowed;
        this.creditCost = creditCost;
    }
}