package io.openaev.service.asset;

import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AiTargetRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.credential.CredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AiTargetService {

  private final AiTargetRepository aiTargetRepository;
  private final TagRepository tagRepository;
  private final CredentialService credentialService;

  // -- CREATE --

  public Asset createAiTarget(String tenantId, AiTargetInput input) {
    Asset aiTarget = new Asset();
    aiTarget.setTenant(new Tenant(tenantId));
    return aiTargetRepository.save(applyInput(aiTarget, input));
  }

  // -- UPDATE --

  public Asset updateAiTarget(String aiTargetId, AiTargetInput input) {
    Asset aiTarget =
        aiTargetRepository.findAiTargetById(aiTargetId).orElseThrow(ElementNotFoundException::new);
    return aiTargetRepository.save(applyInput(aiTarget, input));
  }

  private Asset applyInput(Asset aiTarget, AiTargetInput input) {
    aiTarget.setUpdateAttributes(input);
    aiTarget.setAiTargetCredential(apiTokenCredential(input.getAiTargetCredentialId()));
    aiTarget.setCategory(AssetCategory.AI_TARGET);
    aiTarget.setTags(
        iterableToSet(
            tagRepository.findAllByIdInTenant(input.getTagIds(), aiTarget.getTenant().getId())));
    return aiTarget;
  }

  private CredentialSecretReference apiTokenCredential(String credentialId) {
    if (credentialId == null) {
      return null;
    }
    CredentialSecretReference credential = credentialService.getCredentialById(credentialId);
    if (credential.getCredentialAuthMethod()
        != CredentialSecretReference.CREDENTIAL_AUTH_METHOD.API_TOKEN) {
      throw new BadRequestException("An AI target needs an API token credential");
    }
    return credential;
  }
}
