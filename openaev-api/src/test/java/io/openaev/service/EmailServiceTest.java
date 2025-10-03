package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Execution;
import io.openaev.execution.ExecutionContext;
import io.openaev.injectors.email.service.EmailService;
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
import org.springframework.boot.test.mock.mockito.MockBean;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest extends IntegrationTest {

  @MockBean private SmtpService smtpService;

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
}
