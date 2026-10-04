package com.meterflow.catalog.api;

import com.meterflow.catalog.domain.Plan;
import com.meterflow.catalog.service.CatalogService;
import com.meterflow.common.error.MeterFlowException;
import io.micrometer.core.instrument.Meter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class CatalogAdminController {

    private final CatalogService catalogService;

    public CatalogAdminController(CatalogService catalogService){
        this.catalogService= catalogService;
    }

    @PostMapping("/api-products")
    public ResponseEntity<ApiProductResponse> createApiProduct(
            @Valid @RequestBody CreateApiProductRequest request) {

        ApiProductResponse response = catalogService.createApiProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api-products")
    public ResponseEntity<Page<ApiProductResponse>> getApiProductsByPage(@Min(0) @RequestParam(defaultValue = "0") int page){
        Page<ApiProductResponse> responses = catalogService.getAllApiProducts(page);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/api-products/{code}")
    public ResponseEntity<ApiProductResponse> updateApiProductStatus(
            @PathVariable String code,
            @Valid @RequestBody UpdateApiProductRequest request) {

        ApiProductResponse response =
                catalogService.updateApiProductStatus(request, code);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/plans")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request){
        PlanResponse plan = catalogService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> getPlans(){
        return ResponseEntity.ok(catalogService.getPlans());
    }

    @GetMapping("/plans/{code}")
    public ResponseEntity<PlanResponse> getPlanByCode(@PathVariable("code") String code){

        return ResponseEntity.ok(catalogService.getPlanByCode(code));
    }

    @PatchMapping("/plans/{code}")
    public ResponseEntity<PlanResponse> updatePlan(@Valid @RequestBody UpdatePlanRequest request,@PathVariable("code") String code ){
        return ResponseEntity.ok(catalogService.updatePlan(request,code));
    }

    @PutMapping("/plans/{planCode}/entitlements/{productCode}")
    public ResponseEntity<PlanEntitlementResponse> updatePlanEntitlement(
            @Valid @RequestBody UpsertPlanEntitlementRequest request,
            @PathVariable String planCode,
            @PathVariable String productCode) {

        PlanEntitlementResponse response =
                catalogService.updatePlanEntitlement(
                        request,
                        planCode,
                        productCode
                );

        return ResponseEntity.ok(response);
    }

}
