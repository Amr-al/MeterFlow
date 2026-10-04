package com.meterflow.catalog;

import com.meterflow.catalog.api.PlanView;
import com.meterflow.catalog.service.CatalogService;
import org.springframework.stereotype.Component;

@Component
public class CatalogFacade {
    private final CatalogService catalogService;

    public CatalogFacade(CatalogService catalogService){
        this.catalogService= catalogService;
    }

    public PlanView getPlan(String code) {
        return catalogService.getPlan(code);
    }
}
