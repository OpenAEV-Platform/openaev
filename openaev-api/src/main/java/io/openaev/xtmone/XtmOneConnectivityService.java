package io.openaev.xtmone;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class XtmOneConnectivityService {

  private final XtmOneConfig config;
  private final XtmOneService xtmOneService;
  private final TaskScheduler taskScheduler;

  @PostConstruct
  public void init() {
    if (!config.isConfigured()) {
      log.info("[XTM One] Not configured, skipping connectivity checks");
      return;
    }
    log.info("[XTM One] Scheduling connectivity checks (register every 5 min)");
    // Fire once at boot (30s delay for platform readiness), then every 5 min
    taskScheduler.schedule(this::tick, Instant.now().plusSeconds(30));
    taskScheduler.scheduleAtFixedRate(this::tick, Duration.ofMinutes(5));
  }

  private void tick() {
    try {
      xtmOneService.autoRegister();
    } catch (Exception e) {
      log.warn("[XTM One] Connectivity tick error.", e);
    }
  }
}
