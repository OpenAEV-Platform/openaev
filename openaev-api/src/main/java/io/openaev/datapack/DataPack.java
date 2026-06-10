package io.openaev.datapack;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class DataPack {
  private final DataPackService dataPackService;

  protected DataPack(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  protected abstract boolean doProcess(TxCtx ctx);

  @Getter private final String packId = this.getClass().getCanonicalName();

  public DataPackProcessingResult process(TxCtx ctx) {
    return dataPackService
        .findByIdAndTenant(packId, new Tenant(ctx.tenantIdFromUri()))
        .map(
            dataPack -> {
              log.debug("Already processed datapack '{}' for tenant {}.", packId, ctx.tenantIdFromUri());
              return DataPackProcessingResult.SKIPPED;
            })
        .orElseGet(
            () -> {
              log.info(
                  "Processing datapack '{}' for tenant {}.",
                  this.getClass().getCanonicalName(),
                  ctx.tenantIdFromUri());
              if (doProcess(ctx)) {
                dataPackService.registerDataPack(packId, new Tenant(ctx.tenantIdFromUri()));
              }
              return DataPackProcessingResult.PROCESSED;
            });
  }
}
