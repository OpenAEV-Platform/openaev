/*package io.openaev.api.multi_tenancy;


// TODO multi-tenancy: uncomment tests when multi tenancy will work + add tests with URL and tenant
// id with objects linked like scenarios, injects,...
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiTenancyTest extends IntegrationTest {

  @Autowired private TenantComposer tenantComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EndpointRepository endpointRepository;

  @Test
  @DisplayName(
      "Should_save_endpoints_on_tenants_and_return_only_endpoints_for_requested_tenant_when_find_all")
  void
      Should_save_endpoints_on_tenants_and_return_only_endpoints_for_requested_tenant_when_find_all() {

    Tenant tenant1 = TenantFixture.getTenant("tenant1");
    Tenant tenant2 = TenantFixture.getTenant("tenant2");
    tenant1 = tenantComposer.forTenant(tenant1).persist().get();
    tenant2 = tenantComposer.forTenant(tenant2).persist().get();

    TenantContext.setCurrentTenant(tenant1.getId());
    Endpoint endpoint1 = EndpointFixture.createEndpoint("endpoint1");
    endpoint1 = endpointComposer.forEndpoint(endpoint1).persist().get();
    Endpoint endpoint2 = EndpointFixture.createEndpoint("endpoint2");
    endpoint2 = endpointComposer.forEndpoint(endpoint2).persist().get();
    Endpoint endpoint3 = EndpointFixture.createEndpoint("endpoint3");
    endpoint3 = endpointComposer.forEndpoint(endpoint3).persist().get();

    TenantContext.setCurrentTenant(tenant2.getId());
    Endpoint endpoint4 = EndpointFixture.createEndpoint("endpoint4");
    endpoint4 = endpointComposer.forEndpoint(endpoint4).persist().get();
    Endpoint endpoint5 = EndpointFixture.createEndpoint("endpoint5");
    endpoint5 = endpointComposer.forEndpoint(endpoint5).persist().get();

    List<Endpoint> endpointsTenant2 = StreamHelper.fromIterable(endpointRepository.findAll());
    endpointsTenant2 =
        endpointsTenant2.stream().sorted(Comparator.comparing(Endpoint::getName)).toList();

    assertEquals(2, endpointsTenant2.size());
    assertEquals("endpoint4", endpointsTenant2.getFirst().getName());

    TenantContext.setCurrentTenant(tenant1.getId());

    List<Endpoint> endpointsTenant1 = StreamHelper.fromIterable(endpointRepository.findAll());
    endpointsTenant1 =
        endpointsTenant1.stream().sorted(Comparator.comparing(Endpoint::getName)).toList();

    assertEquals(3, endpointsTenant1.size());
    assertEquals("endpoint1", endpointsTenant1.getFirst().getName());
  }

  @Test
  @DisplayName("Should_return_exception_when_trying_to_update_endpoint_with_different_tenant")
  void should_return_exception_when_trying_to_update_endpoint_with_different_tenant() {

    Tenant tenant1 = TenantFixture.getTenant("tenant1");
    Tenant tenant2 = TenantFixture.getTenant("tenant2");
    tenantComposer.forTenant(tenant1).persist();
    tenantComposer.forTenant(tenant2).persist();

    TenantContext.setCurrentTenant(tenant1.getId());
    Endpoint endpoint1 = EndpointFixture.createEndpoint("endpoint1");
    endpointComposer.forEndpoint(endpoint1).persist();

    assertThatThrownBy(
            () -> {
              endpoint1.setTenant(tenant2);
              endpointRepository.save(endpoint1);
            })
        .isInstanceOf(IllegalStateException.class);
  }
}
*/
