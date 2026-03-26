package io.openaev.utils.mapper;

import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tag;
import io.openaev.rest.injector_contract.output.InjectorContractActionOutput;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class InjectorContractMapper {

  private final PayloadMapper payloadMapper;

  /**
   * Convert an injectorContract to an InjectorContractActionOutput
   *
   * @param injectorContract the injectorContract to convert
   * @return the injector contract action output DTO
   */
  public InjectorContractActionOutput toInjectorContractActionOutput(
      InjectorContract injectorContract) {
    if (injectorContract == null) {
      return null;
    }
    return InjectorContractActionOutput.builder()
        .id(injectorContract.getId())
        .labels(injectorContract.getLabels())
        .injectorType(injectorContract.getInjector().getType())
        .domains(
            injectorContract.getDomains().stream().map(Domain::getId).collect(Collectors.toSet()))
        .platforms(injectorContract.getPlatforms())
        .tags(injectorContract.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .updatedAt(injectorContract.getUpdatedAt())
        .payload(payloadMapper.toPayloadSimple(Optional.ofNullable(injectorContract.getPayload())))
        .build();
  }
}
