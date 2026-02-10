package io.openaev.tools;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Slf4j
public class AppReadyListener {

  private static final String BOLD_WHITE_ON_GREEN = "\u001B[1;97;42m";
  private static final String GREEN = "\u001B[32m";
  private static final String RESET = "\u001B[0m";

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    Duration timeTaken = event.getTimeTaken();

    long minutes = timeTaken.toMinutes();
    long seconds = timeTaken.toSecondsPart();

    String startup = String.format("         Startup time: %d min %d sec", minutes, seconds);
    String paddedStartup = String.format("%-44s", startup);

    log.info("");
    log.info("{}╔════════════════════════════════════════════╗{}", GREEN, RESET);
    log.info("{}║                                            ║{}", GREEN, RESET);
    log.info(
        "{}║{}            APPLICATION IS READY            {}{}║{}",
        GREEN,
        BOLD_WHITE_ON_GREEN,
        RESET,
        GREEN,
        RESET);
    log.info("{}║                                            ║{}", GREEN, RESET);
    log.info("{}║{}{}{}{}║{}", GREEN, BOLD_WHITE_ON_GREEN, paddedStartup, RESET, GREEN, RESET);
    log.info("{}║                                            ║{}", GREEN, RESET);
    log.info("{}╚════════════════════════════════════════════╝{}", GREEN, RESET);
    log.info("");
  }
}
