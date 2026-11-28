package io.openaev.injectors.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.FileService;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A connection failure must end the tick, not the scheduler.
 *
 * <p>The sync retry used to be split wrong: {@code syncFolders} swallowed the {@link
 * MessagingException} and called the retry method, so nothing the retry method called could ever
 * fail. The first connection failure therefore recursed between the two methods until the scheduler
 * thread died with a {@code StackOverflowError}, with no retry performed and no store closed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImapService sync retry")
class ImapServiceSyncRetryTest {

  @Mock private UserRepository userRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private CommunicationRepository communicationRepository;
  @Mock private FileService fileService;
  @Mock private Environment env;
  @Mock private SettingRepository settingRepository;
  @Mock private TenantScopedTransaction tenantScopedTransaction;

  private ImapService imapService;
  private Store store;

  @BeforeEach
  void beforeEach() throws Exception {
    imapService =
        new ImapService(
            userRepository,
            injectRepository,
            communicationRepository,
            fileService,
            env,
            settingRepository,
            tenantScopedTransaction);
    ReflectionTestUtils.setField(imapService, "enabled", true);
    ReflectionTestUtils.setField(imapService, "host", "imap.openaev.test");
    ReflectionTestUtils.setField(imapService, "port", 993);
    ReflectionTestUtils.setField(imapService, "username", "mailbox@openaev.test");
    ReflectionTestUtils.setField(imapService, "password", "password");
    ReflectionTestUtils.setField(imapService, "sentFolder", "Sent");
    ReflectionTestUtils.setField(imapService, "inboxFolders", List.of("INBOX"));

    // Pass-through, so the availability write reaches the mocked repository
    lenient()
        .doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantScopedTransaction)
        .executeAsPlatformWrite(any(TxCtx.class), any(Runnable.class));
    lenient()
        .when(settingRepository.findByKeyAndTenantIsNull(anyString()))
        .thenReturn(Optional.empty());

    store = mock(Store.class);
    when(store.isConnected()).thenReturn(true);
    when(store.getFolder(anyString())).thenThrow(new MessagingException("connection reset"));
    ReflectionTestUtils.setField(imapService, "imapStore", store);
  }

  @Test
  @DisplayName("given_a_folder_that_cannot_be_opened, should_try_three_times_then_drop_the_store")
  void given_aFolderThatCannotBeOpened_should_tryThreeTimesThenDropTheStore() throws Exception {
    // Act: three attempts, two seconds apart - the recursion this replaces never returned
    assertTimeout(Duration.ofSeconds(60), () -> imapService.connectionListener());

    // Assert: bounded attempts, the store closed once, and nulled so the next tick rebuilds it
    verify(store, times(3)).getFolder(anyString());
    verify(store, times(1)).close();
    assertThat((Object) ReflectionTestUtils.getField(imapService, "imapStore")).isNull();
  }
}
