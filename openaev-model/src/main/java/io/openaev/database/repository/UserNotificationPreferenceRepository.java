package io.openaev.database.repository;

import io.openaev.database.model.NotificationPreferenceType;
import io.openaev.database.model.UserNotificationPreference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationPreferenceRepository
    extends JpaRepository<UserNotificationPreference, String> {

  List<UserNotificationPreference> findByUserId(String userId);

  Optional<UserNotificationPreference> findByUserIdAndType(
      String userId, NotificationPreferenceType type);

  List<UserNotificationPreference> findByUserIdInAndType(
      Collection<String> userIds, NotificationPreferenceType type);
}
