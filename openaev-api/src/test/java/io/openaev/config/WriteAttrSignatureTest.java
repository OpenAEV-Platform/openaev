package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.config.WriteAttrSignature.Relation;
import io.openaev.database.model.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The relation classification and signature grammar shared by the recorder, gate and baseline. */
class WriteAttrSignatureTest {

  @Test
  @DisplayName("the default tenant, a null tenant and any other tenant are distinct relations")
  void classifiesTheWrittenTenant() {
    assertEquals(Relation.DEFAULT, WriteAttrSignature.relationOf(Tenant.DEFAULT_TENANT_UUID));
    assertEquals(
        Relation.OTHER, WriteAttrSignature.relationOf("11111111-2222-3333-4444-555555555555"));
    assertEquals(Relation.NULL, WriteAttrSignature.relationOf(null));
    assertEquals(
        Relation.NULL,
        WriteAttrSignature.relationOf(WriteAttrSignature.NULL_TENANT_MARKER),
        "the trigger raises a null tenant as the literal NULL");
  }

  @Test
  @DisplayName("the signature is table, relation and entry frame without its line number")
  void buildsTheSignature() {
    assertEquals(
        "scenarios DEFAULT io.openaev.rest.scenario.ScenarioApi.createScenario",
        WriteAttrSignature.of(
            "scenarios",
            Relation.DEFAULT,
            "io.openaev.rest.scenario.ScenarioApi.createScenario:105"));
  }

  @Test
  @DisplayName("a Spring CGLIB proxy suffix is stripped so the key does not churn between runs")
  void stripsProxySuffix() {
    assertEquals(
        "domains OTHER io.openaev.service.tenants.TenantService.create",
        WriteAttrSignature.of(
            "domains",
            Relation.OTHER,
            "io.openaev.service.tenants.TenantService$$SpringCGLIB$$0.create:184"),
        "the $$SpringCGLIB$$N proxy decoration carries a counter and is not stable enough for a"
            + " frozen baseline");
  }

  @Test
  @DisplayName("a legitimate nested class (single $) is preserved when a proxy suffix is stripped")
  void keepsNestedClassNames() {
    assertEquals(
        "domains OTHER io.openaev.Outer$Inner.write",
        WriteAttrSignature.of(
            "domains", Relation.OTHER, "io.openaev.Outer$Inner$$SpringCGLIB$$0.write:12"));
  }
}
