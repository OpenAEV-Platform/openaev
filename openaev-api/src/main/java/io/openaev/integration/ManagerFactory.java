package io.openaev.integration;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ManagerFactory {
  private final List<IntegrationFactory> factories;
  private Manager manager = null;

  public Manager getManager() throws Exception {
    if (this.manager == null) {
      this.manager = new Manager(factories);
    }
    return this.manager;
  }
}
