package io.openaev.injectors.email.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Communication;
import io.openaev.database.model.Inject;
import io.openaev.database.model.User;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.FileService;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * One message that cannot be read must not wedge the folder.
 *
 * <p>{@code synchronizeBox} writes the per-folder read cursor only after {@code parseMessages}
 * returns, so an exception thrown for a single message re-fetches that same message on every
 * 10-second tick and never reaches the messages behind it. Two ways a single message did that: a
 * null Received/Sent date ({@code toInstant()} on null) and any other read failure.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImapService message isolation")
class ImapServiceMessageIsolationTest {

  private static final String INJECT_ID = "3f2a1b6c-9d4e-4a1b-8c2d-5e6f7a8b9c0d";
  private static final String TENANT_ID = "11111111-2222-3333-4444-555555555555";

  @Mock private UserRepository userRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private CommunicationRepository communicationRepository;
  @Mock private FileService fileService;
  @Mock private Environment env;
  @Mock private SettingRepository settingRepository;
  @Mock private TenantScopedTransaction tenantScopedTransaction;

  private ImapService imapService;

  @BeforeEach
  void beforeEach() {
    imapService =
        new ImapService(
            userRepository,
            injectRepository,
            communicationRepository,
            fileService,
            env,
            settingRepository,
            tenantScopedTransaction);
    ReflectionTestUtils.setField(imapService, "username", "mailbox@openaev.test");

    // Pass-through scope: the assertions are about the message loop, not the transaction guard
    lenient()
        .when(tenantScopedTransaction.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());

    InjectRepository.InjectTenantRef ref = mock(InjectRepository.InjectTenantRef.class);
    when(ref.getId()).thenReturn(INJECT_ID);
    when(ref.getTenantId()).thenReturn(TENANT_ID);
    when(injectRepository.findTenantRefById(INJECT_ID)).thenReturn(Optional.of(ref));
    when(communicationRepository.existsByIdentifierAndInjectTenantId(anyString(), anyString()))
        .thenReturn(false);
    when(userRepository.findAllByEmailInIgnoreCaseAndTenantId(anyList(), anyString()))
        .thenReturn(List.of(new User()));

    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    when(injectRepository.findById(INJECT_ID)).thenReturn(Optional.of(inject));
    when(communicationRepository.save(any(Communication.class)))
        .thenAnswer(
            invocation -> {
              Communication saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID().toString());
              return saved;
            });
  }

  /** A reply whose ENVELOPE carries no date, as a server exposing only INTERNALDATE does. */
  private static MimeMessage reply(Date receivedDate, Date sentDate) throws Exception {
    MimeMessage message = mock(MimeMessage.class);
    when(message.getMessageID()).thenReturn("<abc@" + UUID.randomUUID() + ">");
    when(message.isMimeType("multipart/*")).thenReturn(false);
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(message.getContent()).thenReturn("Thanks [inject_id=" + INJECT_ID + "]");
    when(message.getFrom()).thenReturn(new InternetAddress[] {new InternetAddress("a@b.test")});
    when(message.getAllRecipients())
        .thenReturn(new InternetAddress[] {new InternetAddress("b@c.test")});
    when(message.getSubject()).thenReturn("Re: simulation");
    when(message.getReceivedDate()).thenReturn(receivedDate);
    when(message.getSentDate()).thenReturn(sentDate);
    return message;
  }

  @Test
  @DisplayName("given_a_message_without_dates, should_record_it_instead_of_failing")
  void given_aMessageWithoutDates_should_recordItInsteadOfFailing() throws Exception {
    // Arrange: both dates null - toInstant() on either used to abort the whole batch
    MimeMessage message = reply(null, null);

    // Act / Assert
    assertThatCode(
            () ->
                ReflectionTestUtils.invokeMethod(
                    imapService, "parseMessages", new Message[] {message}, false))
        .doesNotThrowAnyException();

    org.mockito.ArgumentCaptor<Communication> saved =
        org.mockito.ArgumentCaptor.forClass(Communication.class);
    verify(communicationRepository).save(saved.capture());
    // The NOT NULL columns keep the model's own now() default rather than a null
    org.assertj.core.api.Assertions.assertThat(saved.getValue().getReceivedAt()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(saved.getValue().getSentAt()).isNotNull();
  }

  @Test
  @DisplayName("given_one_unreadable_message, should_still_process_the_next_one")
  void given_oneUnreadableMessage_should_stillProcessTheNextOne() throws Exception {
    // Arrange: the first message of the batch cannot be read at all
    MimeMessage unreadable = mock(MimeMessage.class);
    when(unreadable.getMessageID()).thenReturn("<broken@" + UUID.randomUUID() + ">");
    when(unreadable.isMimeType("multipart/*")).thenReturn(false);
    when(unreadable.isMimeType("text/plain")).thenReturn(true);
    when(unreadable.getContent()).thenThrow(new MessagingException("connection reset"));
    MimeMessage healthy = reply(new Date(), new Date());

    // Act: the batch is processed, and one bad message does not end it
    assertThatCode(
            () ->
                ReflectionTestUtils.invokeMethod(
                    imapService, "parseMessages", new Message[] {unreadable, healthy}, false))
        .doesNotThrowAnyException();

    // Assert: the message behind the broken one was still recorded
    verify(communicationRepository).save(any(Communication.class));
  }
}
