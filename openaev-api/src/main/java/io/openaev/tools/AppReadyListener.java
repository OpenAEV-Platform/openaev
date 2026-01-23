package io.openaev.tools;

import java.time.Duration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class AppReadyListener {

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    Duration timeTaken = event.getTimeTaken();

    long minutes = timeTaken.toMinutes();
    long seconds = timeTaken.toSecondsPart();
    System.out.println("✅ Application is ready!");
    System.out.printf("⏱️ Startup time: %d min %d sec%n", minutes, seconds);
  }
}
