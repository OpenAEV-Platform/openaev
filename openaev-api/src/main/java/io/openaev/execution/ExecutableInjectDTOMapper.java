package io.openaev.execution;

import io.openaev.database.model.Endpoint;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
import java.util.HashSet;
import java.util.List;
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
                // Only endpoints are conveyed as endpoint targets. A group may resolve
                // non-endpoint assets (e.g. AI targets); those are handled by their own injector
                // (which resolves them from the asset group), not through the endpoint asset list.
                // Unproxy first: a lazy proxy typed as Asset would fail a plain instanceof even
                // for a real endpoint and silently drop it from the target list.
                // Will be a problem for AI ReadTeam injector if we want it to follow the same
                // resolution path.
                .filter(
                    assetToExecute -> Hibernate.unproxy(assetToExecute.asset()) instanceof Endpoint)
                // Each target carries the inject's asset groups it was resolved from, so the
                // injector can attribute its results without fetching the groups' members again.
                .map(
                    assetToExecute ->
                        endpointMapper.toEndpointTargetOutput(
                            (Endpoint) Hibernate.unproxy(assetToExecute.asset()),
                            new HashSet<>(assetToExecute.assetGroups())))
                .collect(Collectors.toSet()))
        // Attention: should only be used by AI Read Team injector for now since it follows a
        // different resolution path.
        // NOT marking-filtered: asset groups carry no marking of their own - whether/how a group
        // should be marking-aware is still an open, deliberately deferred decision - and the
        // downstream injector resolves non-endpoint members (e.g. AI targets) from the group
        // independently of the flat asset list above. .
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
