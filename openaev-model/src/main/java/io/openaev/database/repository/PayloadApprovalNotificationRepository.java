package io.openaev.database.repository;

import io.openaev.database.model.PayloadApprovalNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadApprovalNotificationRepository
    extends JpaRepository<PayloadApprovalNotification, String> {

  /** The tenants with notifications still to send (read under a cross-tenant scope). */
  @Query(
      value =
          "SELECT DISTINCT n.tenant_id FROM payload_approval_notifications n"
              + " WHERE n.payload_approval_notification_dispatched_at IS NULL",
      nativeQuery = true)
  List<String> findTenantIdsToDispatch();

  /** The notifications of the scoped tenant still to send, oldest first. */
  List<PayloadApprovalNotification> findByDispatchedAtIsNullOrderByCreatedAtAsc();
}
