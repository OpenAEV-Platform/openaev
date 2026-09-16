package io.openaev.execution;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.SecretReference;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutableInjectDTOMapper {

  final EndpointMapper endpointMapper;
  final AssetGroupMapper assetGroupMapper;

  public ExecutableInjectDTO toExecutableInjectDTO(
      ExecutableInject executableInject, String authorisationCode) {
    List<String> credentialReferences =
        executableInject.getInjection().getInject().getSecretReferences().stream()
            .filter(Objects::nonNull)
            .map(SecretReference::getId)
            .filter(Objects::nonNull)
            .toList();

    return ExecutableInjectDTO.builder()
        .injection(executableInject.getInjection())
        .assets(
            executableInject.getAssets().stream()
                // Only endpoints are conveyed as endpoint targets. A group may resolve
                // non-endpoint assets (e.g. AI targets); those are handled by their own injector
                // (which resolves them from the asset group), not through the endpoint asset list.
                // Unproxy first: a lazy proxy typed as Asset would fail a plain instanceof even
                // for a real endpoint and silently drop it from the target list.
                .map(
                    asset ->
                        Hibernate.unproxy(asset) instanceof Endpoint endpoint ? endpoint : null)
                .filter(Objects::nonNull)
                .map(endpointMapper::toEndpointTargetOutput)
                .collect(Collectors.toSet()))
        .assetGroups(
            executableInject.getAssetGroups().stream()
                .map(assetGroupMapper::toAssetGroupSimple)
                .collect(Collectors.toSet()))
        .attachments(
            ExecutableInjectDTO.Attachments.builder()
                .credentialReferences(credentialReferences)
                .authorisationCode(authorisationCode)
                .build())
        .build();
  }
}
