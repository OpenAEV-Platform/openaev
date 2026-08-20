package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.execution.ExecutionContext;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.injectors.email.service.ImapService;
import io.openaev.injectors.email.service.SmtpService;
import io.openaev.utils.fixtures.UserFixture;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest extends IntegrationTest {

  @MockitoBean private SmtpService smtpService;

  @MockitoBean private ImapService imapService;

  @Autowired private EmailService emailService;

  @Test
  void shouldSetReplyToInHeaderEqualsToFrom() throws Exception {
    ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);

    Execution execution = new Execution();
    ExecutionContext userContext = new ExecutionContext(UserFixture.getSavedUser(), null);

    when(smtpService.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    emailService.sendEmail(
        execution,
        List.of(userContext),
        "user@openaev.io",
        null,
        List.of("user-reply-to@openaev.io"),
        null,
        false,
        "subject",
        "message",
        Collections.emptyList());
    verify(smtpService).send(argument.capture());
    assertEquals("user@openaev.io", argument.getValue().getHeader("From")[0]);
    assertEquals("user-reply-to@openaev.io", argument.getValue().getHeader("Reply-To")[0]);
  }

  @Test
  void shouldMarkImapStoreFailureAsWarningNotError() throws Exception {
    // The mail is sent, but storing a copy in the IMAP sent folder keeps failing (e.g. IMAP not
    // connected). The inject must still be considered a success, with the IMAP failure surfaced as
    // a
    // WARNING trace rather than an ERROR that would flip the whole inject to "Error".
    ReflectionTestUtils.setField(emailService, "imapEnabled", true);
    when(smtpService.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    doThrow(new RuntimeException("Not connected"))
        .when(imapService)
        .storeSentMessage(any(MimeMessage.class));

    Execution execution = new Execution(true);
    ExecutionContext userContext = new ExecutionContext(UserFixture.getSavedUser(), null);

    try {
      emailService.sendEmail(
          execution,
          List.of(userContext),
          "user@openaev.io",
          null,
          List.of("user-reply-to@openaev.io"),
          null,
          false,
          "subject",
          "message",
          Collections.emptyList());
    } finally {
      ReflectionTestUtils.setField(emailService, "imapEnabled", false);
    }

    List<ExecutionTrace> traces = execution.getTraces();

    // A single terminal WARNING trace on the COMPLETE action, and no ERROR trace anywhere.
    assertTrue(
        traces.stream()
            .anyMatch(
                t ->
                    ExecutionTraceStatus.WARNING.equals(t.getStatus())
                        && ExecutionTraceAction.COMPLETE.equals(t.getAction())),
        "expected a WARNING trace on the COMPLETE action for the IMAP store failure");
    assertFalse(
        traces.stream().anyMatch(t -> ExecutionTraceStatus.ERROR.equals(t.getStatus())),
        "IMAP store failure must not add any ERROR trace");
    // The overall execution status is a success (WARNING is counted as success).
    assertEquals(ExecutionStatus.EXECUTED, execution.getStatus());
  }
}
