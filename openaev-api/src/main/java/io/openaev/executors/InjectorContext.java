package io.openaev.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.service.FileService;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class InjectorContext {
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper mapper;
  private final FileService fileService;
  private final DocumentRepository documentRepository;

  public InjectorContext(
      OpenAEVConfig openAEVConfig,
      ObjectMapper mapper,
      FileService fileService,
      DocumentRepository documentRepository) {
    this.openAEVConfig = openAEVConfig;
    this.mapper = mapper;
    this.fileService = fileService;
    this.documentRepository = documentRepository;
  }
}
