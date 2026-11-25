package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notification recipients are the readers of the event's object")
class NotificationRecipientFilterTest {

  private static final String TENANT = "tenant-a";
  private static final String SCENARIO_ID = "scenario-id";

  private PermissionService permissionService;
  private UserService userService;
  private NotificationRecipientFilter filter;

  @BeforeEach
  void setUp() {
    permissionService = mock(PermissionService.class);
    userService = mock(UserService.class);
    // holdsInTenant enters the tenant and runs the decision: run it as is here.
    when(permissionService.holdsInTenant(eq(TENANT), any()))
        .thenAnswer(invocation -> invocation.<BooleanSupplier>getArgument(1).getAsBoolean());
    filter = new NotificationRecipientFilter(permissionService, userService);
  }

  private static User user(String id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private static ResolvedNotificationTrigger trigger(List<String> recipients) {
    return new ResolvedNotificationTrigger(
        "trigger-id",
        "Scenarios",
        NotificationTriggerType.LIVE,
        ResourceType.SCENARIO,
        Set.of(),
        null,
        null,
        null,
        null,
        List.of(),
        TENANT,
        recipients,
        List.of(new ResolvedNotifier("notifier-id", "UI", NotifierType.UI, Map.of())));
  }

  private void canRead(User user, ResourceType type, String id, boolean allowed) {
    when(userService.userWithRoles(user.getId())).thenReturn(user);
    when(permissionService.hasPermissionInTenant(
            any(TxCtx.class), eq(user), eq(id), eq(type), eq(Action.READ)))
        .thenReturn(allowed);
  }

  @Test
  @DisplayName(
      "a restricted user with a trigger on scenarios gets nothing for a scenario it cannot read")
  void restrictedUserIsDropped() {
    User reader = user("reader");
    User restricted = user("restricted");
    canRead(reader, ResourceType.SCENARIO, SCENARIO_ID, true);
    canRead(restricted, ResourceType.SCENARIO, SCENARIO_ID, false);

    List<String> readers =
        filter.readersOf(
            trigger(List.of("reader", "restricted")),
            NotificationResourceCatalog.SCENARIO,
            SCENARIO_ID);

    assertEquals(List.of("reader"), readers);
  }

  @Test
  @DisplayName("a recipient that cannot be loaded is dropped (fail closed)")
  void unknownRecipientIsDropped() {
    User reader = user("reader");
    canRead(reader, ResourceType.SCENARIO, SCENARIO_ID, true);
    when(userService.userWithRoles("gone")).thenThrow(new ElementNotFoundException("gone"));

    List<String> readers =
        filter.readersOf(
            trigger(List.of("gone", "reader")), NotificationResourceCatalog.SCENARIO, SCENARIO_ID);

    assertEquals(List.of("reader"), readers);
  }

  @Test
  @DisplayName("a check that throws drops the recipient (fail closed)")
  void failingCheckIsDropped() {
    User recipient = user("recipient");
    when(userService.userWithRoles("recipient")).thenReturn(recipient);
    when(permissionService.hasPermissionInTenant(
            any(TxCtx.class), eq(recipient), anyString(), any(), any()))
        .thenThrow(new ElementNotFoundException("parent gone"));

    List<String> readers =
        filter.readersOf(
            trigger(List.of("recipient")), NotificationResourceCatalog.SIMULATION, "simulation-id");

    assertEquals(List.of(), readers);
  }

  @Test
  @DisplayName("a catalogue sync event is checked as READ on the catalogue")
  void catalogSyncIsCheckedOnTheCatalog() {
    User admin = user("catalog-admin");
    User player = user("player");
    canRead(admin, ResourceType.CATALOG, "release-1", true);
    canRead(player, ResourceType.CATALOG, "release-1", false);

    List<String> readers =
        filter.readersOf(
            trigger(List.of("catalog-admin", "player")),
            NotificationResourceCatalog.CATALOG_SYNC,
            "release-1");

    assertEquals(List.of("catalog-admin"), readers);
  }
}
