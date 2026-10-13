# MeterFlow Postman collection

1. In Postman, select **Import** and choose `MeterFlow.postman_collection.json`.
2. Start MeterFlow with its database configured, then set the collection variable `baseUrl` to your server URL (default: `http://localhost:8080`, without a trailing slash).
3. Send requests in folder order, or run the collection with the Collection Runner. Successful create requests automatically save the customer ID, subscription ID, API key ID, raw API key, and normalized product/plan codes.

No separate Postman environment is required. Admin and customer endpoints currently allow unauthenticated requests. The data-plane ping uses `X-API-Key: {{apiKey}}` automatically.

The collection contains all 19 controller endpoints and the exposed Actuator health/info endpoints, JSON request bodies, and expected-status assertions. API product pagination starts at page 0, with 10 products per page.

The final cleanup folder revokes the created API key and cancels the active subscription. Skip that folder if you want to keep using them. Cleanup does not delete the customer, plan, or product. Before repeating a full run, change `productCode` and `planCode` to unused codes to avoid duplicate-resource conflicts. To work with existing records, set the corresponding collection variables and skip creation requests.

The raw API key is only returned at creation and is saved in the local collection variable `apiKey`. Clear it before exporting or sharing your collection. Avoid defining environment variables with the same names, because they override collection variables.
