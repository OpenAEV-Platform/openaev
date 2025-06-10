package io.openbas.injectors.caldera;

import io.openbas.database.repository.InjectRepository;
import io.openbas.injectors.caldera.config.CalderaInjectorConfig;
import io.openbas.injectors.caldera.service.CalderaInjectorService;
import io.openbas.injectors.caldera.service.CalderaResultCollectorService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.caldera", name = "enable")
@RequiredArgsConstructor
@Service
public class CalderaResultCollector {

  private final CalderaInjectorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final InjectRepository injectRepository;
  private final CalderaInjectorService calderaService;
  private final InjectExecutionService injectExecutionService;

  @PostConstruct
  public void init() {
    // If enabled, scheduled every X seconds
    if (this.config.isEnable()) {
      CalderaResultCollectorService service =
          new CalderaResultCollectorService(
              this.injectRepository, this.calderaService, this.injectExecutionService);
      this.taskScheduler.scheduleAtFixedRate(service, Duration.ofSeconds(60));
    }
  }
}
