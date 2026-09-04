package io.openaev.utils.fixtures;

import static java.lang.String.valueOf;

import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.Tenant;
import jakarta.validation.constraints.NotBlank;
import java.util.Random;

public class KillChainPhaseFixture {

  private static final Random RANDOM = new Random();

  public static KillChainPhase getKillChainPhase(
      @NotBlank final String name, @NotBlank final Long order, @NotBlank final String tenantId) {
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setName(name);
    killChainPhase.setShortName(name);
    killChainPhase.setKillChainName("mitre-attack");
    killChainPhase.setOrder(order);
    killChainPhase.setExternalId(valueOf(RANDOM.nextInt()));
    killChainPhase.setTenant(new Tenant(tenantId));
    return killChainPhase;
  }
}
