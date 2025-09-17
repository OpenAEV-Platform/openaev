package io.openbas.rest.collector.service;

import static io.openbas.database.specification.CollectorSpecification.hasSecurityPlatform;
import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.service.FileService.COLLECTORS_IMAGES_BASE_PATH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.database.model.Collector;
import io.openbas.database.repository.CollectorRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.service.FileService;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

  @Resource protected ObjectMapper mapper;

  private final CollectorRepository collectorRepository;
  private final FileService fileService;

  // -- CRUD --

  public Collector collector(String id) {
    return collectorRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with id: " + id));
  }

  public List<Collector> securityPlatformCollectors() {
    return fromIterable(collectorRepository.findAll(hasSecurityPlatform()));
  }

  public Collector updateCollectorState(Collector collectorToUpdate, ObjectNode newState) {
    ObjectNode state =
        Optional.ofNullable(collectorToUpdate.getState()).orElse(mapper.createObjectNode());
    newState
        .fieldNames()
        .forEachRemaining(fieldName -> state.set(fieldName, newState.get(fieldName)));
    return collectorRepository.save(collectorToUpdate);
  }

  // -- ACTION --

  @Transactional
  public void register(String id, String type, String name, InputStream iconData) throws Exception {
    if (iconData != null) {
      fileService.uploadStream(COLLECTORS_IMAGES_BASE_PATH, type + ".png", iconData);
    }
    Collector collector = collectorRepository.findById(id).orElse(null);
    if (collector == null) {
      Collector collectorChecking = collectorRepository.findByType(type).orElse(null);
      if (collectorChecking != null) {
        throw new Exception(
            "The collector "
                + type
                + " already exists with a different ID, please delete it or contact your administrator.");
      }
    }
    if (collector != null) {
      collector.setName(name);
      collector.setExternal(false);
      collector.setType(type);
      collectorRepository.save(collector);
    } else {
      // save the collector
      Collector newCollector = new Collector();
      newCollector.setId(id);
      newCollector.setName(name);
      newCollector.setType(type);
      collectorRepository.save(newCollector);
    }
  }

  public List<Collector> collectorsForPayload(String payloadId) {
    return collectorRepository.findByPayloadId(payloadId);
  }

  @Query(
      "SELECT c FROM Collector c WHERE c.detectionRemediations.payload.injector.contracts.injects.injectId = :injectId")
  public List<Collector> collectorsForAtomicTesting(String injectId) {
    return collectorRepository.findByInjectId(injectId);
  }
}
