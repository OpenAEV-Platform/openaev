package io.openaev.executors;

import static io.openaev.service.FileService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.service.FileService;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExecutorService {

  public static final String EXT_PNG = ".png";
  @Resource protected ObjectMapper mapper;

  private FileService fileService;

  private ExecutorRepository executorRepository;

  @Resource
  public void setFileService(FileService fileService) {
    this.fileService = fileService;
  }

  @Autowired
  public void setExecutorRepository(ExecutorRepository executorRepository) {
    this.executorRepository = executorRepository;
  }

  public Iterable<Executor> executors() {
    return this.executorRepository.findAll();
  }

  @Transactional
  public Executor register(
      String id,
      String type,
      String name,
      String documentationUrl,
      String backgroundColor,
      InputStream iconData,
      InputStream bannerData,
      String[] platforms)
      throws Exception {
    // Sanity checks
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("Executor ID must not be null or empty.");
    }

    // Save imgs
    if (iconData != null) {
      fileService.uploadStream(EXECUTORS_IMAGES_ICONS_BASE_PATH, type + EXT_PNG, iconData);
    }
    if (bannerData != null) {
      fileService.uploadStream(EXECUTORS_IMAGES_BANNERS_BASE_PATH, type + EXT_PNG, bannerData);
    }

    Executor executor = executorRepository.findById(id).orElse(null);
    if (executor == null) {
      Executor executorChecking = executorRepository.findByType(type).orElse(null);
      if (executorChecking != null) {
        throw new Exception(
            "The executor "
                + type
                + " already exists with a different ID, please delete it or contact your administrator.");
      }

      executor = new Executor();
      executor.setId(id);
    }

    executor.setName(name);
    executor.setType(type);
    executor.setDoc(documentationUrl);
    executor.setBackgroundColor(backgroundColor);
    executor.setPlatforms(platforms);

    executorRepository.save(executor);
    return executor;
  }

  @Transactional
  public void remove(String id) {
    executorRepository.findById(id).ifPresent(executor -> executorRepository.deleteById(id));
  }

  @Transactional
  public void removeFromType(String type) {
    executorRepository
        .findByType(type)
        .ifPresent(executor -> executorRepository.deleteById(executor.getId()));
  }
}
