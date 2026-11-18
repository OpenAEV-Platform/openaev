package io.openaev.execution;

import io.openaev.database.model.Endpoint;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
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
  final InjectService injectService;

  public ExecutableInjectDTO toExecutableInjectDTO(
      ExecutableInject executableInject, String authorisationCode) {
    // Marking-filtered, flat asset list: executors pre-cache it via cacheAssetsToExecute(...);
    // direct callers that don't (e.g. atomic testing, chaining) fall back to resolving it fresh
    // here - same fallback pattern as AbstractTechnicalBehavior. Using executableInject.getAssets()
    // directly would bypass the marking filter entirely, since that field is the original,
    // unfiltered list set once at ExecutableInject construction.
    List<AssetToExecute> assetsToExecute = executableInject.getAssetsToExecute();
    if (assetsToExecute == null) {
      assetsToExecute =
          injectService.resolveAllAssetsToExecute(executableInject.getInjection().getInject());
    }
    return ExecutableInjectDTO.builder()
        .injection(executableInject.getInjection())
        .assets(
            assetsToExecute.stream()
                .map(AssetToExecute::asset)
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
        // NOT marking-filtered: asset groups carry no marking of their own - whether/how a group
        // should be marking-aware is still an open, deliberately deferred decision - and the
        // downstream injector resolves non-endpoint members (e.g. AI targets) from the group
        // independently of the flat asset list above. Filtering this set depends on that decision
        // landing first.
        .assetGroups(
            executableInject.getAssetGroups().stream()
                .map(assetGroupMapper::toAssetGroupSimple)
                .collect(Collectors.toSet()))
        .attachments(
            executableInject.getSecretReferenceIds() != null
                    && !executableInject.getSecretReferenceIds().isEmpty()
                ? ExecutableInjectDTO.Attachments.builder()
                    .credentialReferences(executableInject.getSecretReferenceIds())
                    .authorisationCode(authorisationCode)
                    .build()
                : null)
        .build();
  }
}
