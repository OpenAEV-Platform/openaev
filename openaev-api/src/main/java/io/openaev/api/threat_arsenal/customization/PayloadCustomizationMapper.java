package io.openaev.api.threat_arsenal.customization;

import io.openaev.api.threat_arsenal.customization.dto.PayloadCustomizationOutput;
import io.openaev.api.threat_arsenal.customization.dto.PayloadCustomizationUpdateOutput;
import io.openaev.api.threat_arsenal.customization.dto.PayloadFieldUpdateOutput;
import io.openaev.api.threat_arsenal.customization.dto.PayloadReleaseNoteOutput;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.service.payload.PayloadCustomizationMerge.FieldUpdate;
import io.openaev.service.payload.PayloadCustomizationMerge.Update;
import io.openaev.service.payload.PayloadCustomizationService.Customization;
import java.util.List;

public class PayloadCustomizationMapper {

  private PayloadCustomizationMapper() {}

  public static PayloadCustomizationOutput toOutput(Customization customization) {
    return new PayloadCustomizationOutput(
        customization.baseAction().getId(),
        customization.baseAction().getPayload().getVersion(),
        customization
            .update()
            .map(update -> toOutput(update, customization.releaseNotes()))
            .orElse(null));
  }

  private static PayloadCustomizationUpdateOutput toOutput(
      Update update, List<CatalogPayload> releaseNotes) {
    return new PayloadCustomizationUpdateOutput(
        update.target().getId(),
        update.target().getVersion(),
        update.fields().stream().map(PayloadCustomizationMapper::toOutput).toList(),
        update.otherChanges(),
        releaseNotes.stream()
            .map(
                release ->
                    new PayloadReleaseNoteOutput(release.getVersion(), release.getChangelog()))
            .toList());
  }

  private static PayloadFieldUpdateOutput toOutput(FieldUpdate field) {
    return new PayloadFieldUpdateOutput(
        field.field(),
        field.field().getBlock(),
        field.base(),
        field.filigran(),
        field.yours(),
        field.status());
  }
}
