package io.openaev.database.specification;

import io.openaev.database.model.Comcheck;
import io.openaev.database.model.ComcheckStatus;
import org.springframework.data.jpa.domain.Specification;

public class ComcheckStatusSpecification {

  public static Specification<ComcheckStatus> thatNeedExecution() {
    return (root, query, cb) ->
        cb.and(
            cb.isNull(root.get("receiveDate")), // Not Checked
            cb.isNull(root.get("lastSent")), // Not already sent
            cb.equal(
                root.get("comcheck").get("state"), Comcheck.COMCHECK_STATUS.RUNNING) // fromRunning
            );
  }
}
