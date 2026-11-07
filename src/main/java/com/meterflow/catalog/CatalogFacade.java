package com.meterflow.catalog;

import com.meterflow.catalog.api.ApiProductView;
import com.meterflow.catalog.api.EntitlementView;
import com.meterflow.catalog.api.PlanView;
import com.meterflow.catalog.service.CatalogService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CatalogFacade {

    private final CatalogService catalogService;

    public CatalogFacade(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public PlanView getPlan(String code) {
        return catalogService.getPlan(code);
    }

    public ApiProductView getApiProduct(String code) {
        return catalogService.getApiProduct(code);
    }

    public Optional<EntitlementView> findEntitlement(Long planId, Long apiProductId) {
        return catalogService.findEntitlement(planId, apiProductId);
    }
}
