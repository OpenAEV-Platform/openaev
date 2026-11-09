package io.openaev.architecture.fixture;

import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.InjectUtils;
import java.util.List;

/** Code units that create an inject or give it an owner, in each shape the rule must tell apart. */
public class InjectCreationTenantFixtures {

  public Inject createsAndStamps(Tenant tenant) {
    Inject inject = new Inject();
    inject.setTenant(tenant);
    return inject;
  }

  public Inject createsAndLeavesTheTenantToTheListener() {
    return new Inject();
  }

  public Inject createsAndAttachesThroughTheHelper(Scenario scenario) {
    Inject inject = new Inject();
    InjectUtils.attachToScenario(inject, scenario);
    return inject;
  }

  public void attachesThroughTheHelper(Inject inject, Scenario scenario) {
    InjectUtils.attachToScenario(inject, scenario);
  }

  public void attachesAndLeavesTheTenantToTheListener(Inject inject, Scenario scenario) {
    inject.setScenario(scenario);
  }

  public void attachesInALambdaAndLeavesTheTenantToTheListener(
      List<Inject> injects, Scenario scenario) {
    injects.forEach(inject -> inject.setScenario(scenario));
  }

  /** Neither creates nor attaches: out of the rule's reach. */
  public String unrelated(Inject inject) {
    return inject.getTitle();
  }
}
