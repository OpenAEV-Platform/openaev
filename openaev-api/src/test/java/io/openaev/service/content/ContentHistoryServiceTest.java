package io.openaev.service.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.database.model.*;
import io.openaev.database.repository.ContentHistoryRepository;
import io.openaev.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentHistoryServiceTest {

  @Mock private ContentHistoryRepository contentHistoryRepository;
  @Mock private UserService userService;
  @InjectMocks private ContentHistoryService contentHistoryService;

  private static Payload payload(String externalId, String version) {
    Payload payload = new Command();
    payload.setId(UUID.randomUUID().toString());
    payload.setExternalId(externalId);
    payload.setVersion(version);
    payload.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return payload;
  }

  @Nested
  @DisplayName("Payload duplicated")
  class PayloadDuplicated {

    @Test
    void given_duplication_should_fileRowUnderOriginAndPointToCopy() {
      // Arrange
      Payload origin = payload("payload-a", "1.0.0");
      Payload copy = payload(null, null);
      User user = new User();
      when(userService.currentUserOrNull()).thenReturn(user);
      when(contentHistoryRepository.save(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      ContentHistory history = contentHistoryService.payloadDuplicated(origin, copy);

      // Assert
      assertThat(history.getType()).isEqualTo(ContentType.PAYLOAD);
      assertThat(history.getAction()).isEqualTo(ContentHistory.ACTION.DUPLICATE);
      assertThat(history.getExternalId()).isEqualTo("payload-a");
      assertThat(history.getVersion()).isEqualTo("1.0.0");
      assertThat(history.getEntityId()).isEqualTo(copy.getId());
      assertThat(history.getUser()).isSameAs(user);
      assertThat(history.isAutomatic()).isFalse();
      assertThat(history.getTenant()).isSameAs(copy.getTenant());
    }
  }
}
