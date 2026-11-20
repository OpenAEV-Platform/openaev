package io.openaev.api.threat_arsenal.move;

import io.openaev.api.threat_arsenal.move.dto.PayloadUsageMoveOutput;
import io.openaev.api.threat_arsenal.move.dto.PayloadUsageMoveOutput.PayloadUsageRefusal;
import io.openaev.service.payload.PayloadUsageMoveService.Moves;

public class PayloadUsageMoveMapper {

  private PayloadUsageMoveMapper() {}

  public static PayloadUsageMoveOutput toOutput(Moves moves) {
    return new PayloadUsageMoveOutput(
        moves.moved(),
        moves.refused().entrySet().stream()
            .map(refusal -> new PayloadUsageRefusal(refusal.getKey(), refusal.getValue()))
            .toList());
  }
}
