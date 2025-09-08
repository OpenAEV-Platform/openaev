package io.openbas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.openbas.IntegrationTest;
import io.openbas.database.model.Execution;
import io.openbas.execution.ExecutionContext;
import io.openbas.injectors.email.service.EmailService;
import io.openbas.utils.fixtures.UserFixture;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest extends IntegrationTest {

  @Mock private JavaMailSender emailSender;
  @InjectMocks private EmailService emailService;

  @Test
  void shouldSetReplyToInHeaderEqualsToFrom() throws Exception {
    ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);

    Execution execution = new Execution();
    ExecutionContext userContext = new ExecutionContext(UserFixture.getSavedUser(), null);

    when(emailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    emailService.sendEmail(
        execution,
        List.of(userContext),
        "user@openbas.io",
        List.of("user-reply-to@openbas.io"),
        null,
        false,
        "subject",
        "message",
        Collections.emptyList());
    verify(emailSender).send(argument.capture());
    assertEquals("user@openbas.io", argument.getValue().getHeader("From")[0]);
    assertEquals("user-reply-to@openbas.io", argument.getValue().getHeader("Reply-To")[0]);
  }
}
