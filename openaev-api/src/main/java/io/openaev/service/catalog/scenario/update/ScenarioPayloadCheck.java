package io.openaev.service.catalog.scenario.update;

import java.util.List;

/** The payload versions a scenario content runs that its tenant has yet to install, or revoked. */
public record ScenarioPayloadCheck(List<Version> toInstall, List<Version> revoked) {

  public record Version(String name, String version) {}
}
