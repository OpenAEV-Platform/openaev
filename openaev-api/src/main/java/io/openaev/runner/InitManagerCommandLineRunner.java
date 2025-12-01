package io.openaev.runner;

import io.openaev.database.model.*;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Command line runner that initializes the starter pack on first application start. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitManagerCommandLineRunner implements CommandLineRunner {
  private final ManagerFactory managerFactory;

  @Override
  public void run(String... args) {
    managerFactory.getManager();
  }
}
