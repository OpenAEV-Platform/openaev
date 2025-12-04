package io.openaev.integration;

import java.util.List;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service
public class ManagerFactory {
  @Getter private final Manager manager;

  public ManagerFactory(
      List<IntegrationFactory> factories) {
    manager = new Manager(factories);
  }
}
