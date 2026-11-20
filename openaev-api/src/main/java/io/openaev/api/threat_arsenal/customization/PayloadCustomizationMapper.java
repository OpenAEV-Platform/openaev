package io.openaev.api.threat_arsenal.customization;

import io.openaev.api.threat_arsenal.customization.dto.PayloadCustomizationOutput;
import io.openaev.api.threat_arsenal.customization.dto.PayloadCustomizationUpdateOutput;
import io.openaev.api.threat_arsenal.customization.dto.PayloadFieldUpdateOutput;
import io.openaev.service.payload.CustomizableBlock;
import io.openaev.service.payload.PayloadCustomizationMerge.FieldUpdate;
import io.openaev.service.payload.PayloadCustomizationMerge.Update;
import io.openaev.service.payload.PayloadCustomizationService.Customization;
import java.util.Set;

public class PayloadCustomizationMapper {

  private PayloadCustomizationMapper() {}

  public static PayloadCustomizationOutput toOutput(Customization customization) {
    return new PayloadCustomizationOutput(
        customization.baseAction().getId(),
        customization.baseAction().getPayload().getVersion(),
        customization.update().map(PayloadCustomizationMapper::toOutput).orElse(null));
  }

  private static PayloadCustomizationUpdateOutput toOutput(Update update) {
    Set<CustomizableBlock> conflicts = update.conflicts();
    return new PayloadCustomizationUpdateOutput(
        update.target().getId(),
        update.target().getVersion(),
        update.fields().stream().map(field -> toOutput(field, conflicts)).toList(),
        update.otherChanges());
  }

  private static PayloadFieldUpdateOutput toOutput(
      FieldUpdate field, Set<CustomizableBlock> conflicts) {
    CustomizableBlock block = field.field().getBlock();
    return new PayloadFieldUpdateOutput(
        field.field(),
        block,
        field.base(),
        field.filigran(),
        field.yours(),
        conflicts.contains(block));
  }
}
