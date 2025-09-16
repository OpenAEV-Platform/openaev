package io.openbas.rest.collector;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.Collector;
import io.openbas.database.model.ResourceType;
import io.openbas.database.repository.CollectorRepository;
import io.openbas.database.repository.SecurityPlatformRepository;
import io.openbas.rest.collector.form.CollectorCreateInput;
import io.openbas.rest.collector.form.CollectorUpdateInput;
import io.openbas.rest.collector.service.CollectorService;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.FileService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class CollectorApi extends RestBehavior {

  private final CollectorService collectorService;
  private final CollectorRepository collectorRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  private final FileService fileService;

  @GetMapping("/api/collectors")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.COLLECTOR)
  public Iterable<Collector> collectors() {
    return collectorRepository.findAll();
  }

  private Collector updateCollector(
      Collector collector,
      String type,
      String name,
      int period,
      Instant lastExecution,
      String securityPlatform) {
    collector.setUpdatedAt(Instant.now());
    collector.setExternal(true);
    collector.setType(type);
    collector.setName(name);
    collector.setPeriod(period);
    collector.setLastExecution(lastExecution);
    if (securityPlatform != null) {
      collector.setSecurityPlatform(
          securityPlatformRepository.findById(securityPlatform).orElseThrow());
    }
    return collectorRepository.save(collector);
  }

  @GetMapping("/api/collectors/{collectorId}")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.COLLECTOR)
  public Collector getCollector(@PathVariable String collectorId) {
    return collectorService.collector(collectorId);
  }

  @PutMapping("/api/collectors/{collectorId}")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackOn = Exception.class)
  public Collector updateCollector(
      @PathVariable String collectorId, @Valid @RequestBody CollectorUpdateInput input) {
    Collector collector = collectorService.collector(collectorId);
    return updateCollector(
        collector,
        collector.getType(),
        collector.getName(),
        collector.getPeriod(),
        input.getLastExecution(),
        collector.getSecurityPlatform() != null ? collector.getSecurityPlatform().getId() : null);
  }

  @PostMapping(
      value = "/api/collectors",
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackOn = Exception.class)
  public Collector registerCollector(
      @Valid @RequestPart("input") CollectorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    try {
      // Upload icon
      if (file.isPresent() && "image/png".equals(file.get().getContentType())) {
        fileService.uploadFile(
            FileService.COLLECTORS_IMAGES_BASE_PATH + input.getType() + ".png", file.get());
      }
      // We need to support upsert for registration
      Collector collector = collectorRepository.findById(input.getId()).orElse(null);
      if (collector == null) {
        Collector collectorChecking = collectorRepository.findByType(input.getType()).orElse(null);
        if (collectorChecking != null) {
          throw new Exception(
              "The collector "
                  + input.getType()
                  + " already exists with a different ID, please delete it or contact your administrator.");
        }
      }
      if (collector != null) {
        return updateCollector(
            collector,
            input.getType(),
            input.getName(),
            input.getPeriod(),
            collector.getLastExecution(),
            input.getSecurityPlatform());
      } else {
        // save the injector
        Collector newCollector = new Collector();
        newCollector.setId(input.getId());
        newCollector.setExternal(true);
        newCollector.setName(input.getName());
        newCollector.setType(input.getType());
        newCollector.setPeriod(input.getPeriod());
        if (input.getSecurityPlatform() != null) {
          newCollector.setSecurityPlatform(
              securityPlatformRepository.findById(input.getSecurityPlatform()).orElseThrow());
        }
        return collectorRepository.save(newCollector);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
