package io.openaev.injectors.email.service;

import static java.lang.Integer.parseInt;
import static java.time.Instant.now;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.FileService;
import io.openaev.utils.base.ExternalServiceBase;
import jakarta.activation.DataSource;
import jakarta.annotation.PostConstruct;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.util.MimeMessageParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImapService extends ExternalServiceBase {

  private static final Pattern INJECT_ID_PATTERN =
      Pattern.compile(
          "\\[inject_id=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\]");
  private static final String PROVIDER = "imap";
  private static final String IMAP_SETTINGS_KEY = "imap_service_available";

  private Store imapStore;

  @Value("${openaev.mail.imap.enabled}")
  private boolean enabled;

  @Value("${openaev.mail.imap.host}")
  private String host;

  @Value("${openaev.mail.imap.port}")
  private Integer port;

  @Value("${openaev.mail.imap.username}")
  private String username;

  @Value("${openaev.mail.imap.password}")
  private String password;

  @Value("${openaev.mail.imap.inbox}")
  private List<String> inboxFolders;

  @Value("${openaev.mail.imap.sent}")
  private String sentFolder;

  private final UserRepository userRepository;
  private final InjectRepository injectRepository;
  private final CommunicationRepository communicationRepository;
  private final FileService fileService;
  private final Environment env;
  private final SettingRepository settingRepository;
  private final TenantScopedTransaction tenantScopedTransaction;

  /** The inject a reply names, and the tenant every read and write for that reply runs in. */
  private record ResolvedInject(String injectId, String tenantId) {}

  /** What the attachment upload needs once the communication is recorded. */
  private record RecordedCommunication(String communicationId, String exerciseId) {}

  @PostConstruct
  private void init() {
    this.saveServiceState(IMAP_SETTINGS_KEY, false);
    try {
      initStore();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  private void initStore() throws Exception {
    Session session = Session.getDefaultInstance(buildProperties(), null);
    imapStore = session.getStore(PROVIDER);
    if (enabled) {
      log.info("IMAP sync started");
      imapStore.connect(host, port, username, password);
      try {
        Folder defaultFolder = imapStore.getDefaultFolder();
        Folder sentBox = defaultFolder.getFolder(sentFolder);
        if (!sentBox.exists()) {
          sentBox.create(Folder.READ_WRITE);
          sentBox.setSubscribed(true);
        }
        this.saveServiceState(IMAP_SETTINGS_KEY, true);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        this.saveServiceState(IMAP_SETTINGS_KEY, false);
      }
    } else {
      log.info("IMAP sync disabled");
      this.saveServiceState(IMAP_SETTINGS_KEY, false);
    }
  }

  private String getTextFromMimeMultipart(MimeMultipart mimeMultipart)
      throws MessagingException, IOException {
    StringBuilder result = new StringBuilder();
    int count = mimeMultipart.getCount();
    for (int i = 0; i < count; i++) {
      BodyPart bodyPart = mimeMultipart.getBodyPart(i);
      if (bodyPart.isMimeType("text/plain")) {
        result.append(bodyPart.getContent());
        break;
      } else if (bodyPart.getContent() instanceof MimeMultipart) {
        result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
      }
    }
    return result.toString();
  }

  private String getHtmlFromMimeMultipart(MimeMultipart mimeMultipart)
      throws MessagingException, IOException {
    StringBuilder result = new StringBuilder();
    int count = mimeMultipart.getCount();
    for (int i = 0; i < count; i++) {
      BodyPart bodyPart = mimeMultipart.getBodyPart(i);
      if (bodyPart.isMimeType("text/html")) {
        result.append((String) bodyPart.getContent());
        break;
      } else if (bodyPart.getContent() instanceof MimeMultipart) {
        result.append(getHtmlFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
      }
    }
    return result.toString();
  }

  private String getTextFromMessage(Message message) throws MessagingException, IOException {
    String result = "";
    if (message.isMimeType("multipart/*")) {
      MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
      result = getTextFromMimeMultipart(mimeMultipart);
    } else if (message.isMimeType("text/plain")) {
      result = message.getContent().toString();
    }
    return result;
  }

  private String getHtmlFromMessage(Message message) throws MessagingException, IOException {
    String result = "";
    if (message.isMimeType("multipart/*")) {
      MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
      result = getHtmlFromMimeMultipart(mimeMultipart);
    } else if (message.isMimeType("text/html")) {
      result = message.getContent().toString();
    }
    return result;
  }

  private Properties buildProperties() {
    String sslEnable = env.getProperty("openaev.mail.imap.ssl.enable");
    String sslTrust = env.getProperty("openaev.mail.imap.ssl.trust");
    String sslAuth = env.getProperty("openaev.mail.imap.auth");
    String sslStartTLS = env.getProperty("openaev.mail.imap.starttls.enable");
    Properties props = new Properties();
    props.setProperty("mail.imap.ssl.enable", sslEnable);
    props.setProperty("mail.imap.ssl.trust", sslTrust);
    props.setProperty("mail.imap.auth", sslAuth);
    props.setProperty("mail.imap.starttls.enable", sslStartTLS);
    return props;
  }

  private List<String> computeParticipants(Message message) throws Exception {
    List<String> from =
        Arrays.stream(message.getFrom())
            .map(addr -> (((InternetAddress) addr).getAddress()))
            .toList();
    List<String> recipients =
        Arrays.stream(message.getAllRecipients())
            .map(addr -> (((InternetAddress) addr).getAddress()))
            .toList();
    return Stream.concat(from.stream(), recipients.stream())
        .map(String::toLowerCase)
        .filter(recipient -> !recipient.equals(username))
        .distinct()
        .toList();
  }

  /**
   * An inbound email carries no tenant: the inject id in its footer is the only routing key, and it
   * is globally unique. This is the one read made across every tenant, and it returns the inject's
   * tenant only; everything that follows runs in that tenant.
   */
  private Optional<ResolvedInject> injectResolver(String content, String contentHtml) {
    Matcher matcher =
        content.length() > 10
            ? INJECT_ID_PATTERN.matcher(content)
            : INJECT_ID_PATTERN.matcher(contentHtml);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String injectId = matcher.group(1);
    return tenantScopedTransaction.execute(
        TxCtx.allTenants(),
        () ->
            injectRepository
                .findTenantRefById(injectId)
                .map(ref -> new ResolvedInject(ref.getId(), ref.getTenantId())));
  }

  private RecordedCommunication recordCommunication(String injectId, Communication communication) {
    Inject inject = injectRepository.findById(injectId).orElseThrow();
    communication.setInject(inject);
    Communication saved = communicationRepository.save(communication);
    // Update inject for real time
    inject.setUpdatedAt(now());
    injectRepository.save(inject);
    String exerciseId = inject.getExercise() != null ? inject.getExercise().getId() : null;
    return new RecordedCommunication(saved.getId(), exerciseId);
  }

  /**
   * Processes each fetched message on its own: one message that cannot be read or stored must not
   * abort the batch. The per-folder read cursor is written by {@link #synchronizeBox} only after
   * this method returns, so an abort here re-fetches the same message on every tick and wedges
   * every message behind it.
   */
  private void parseMessages(Message[] messages, Boolean isSent) {
    for (Message message : messages) {
      try {
        parseMessage(message, isSent);
      } catch (Exception e) {
        log.error("Could not process an inbound message, skipping it", e);
      }
    }
  }

  private void parseMessage(Message message, Boolean isSent) throws Exception {
    MimeMessage mimeMessage = (MimeMessage) message;
    String messageID = mimeMessage.getMessageID();
    String content = getTextFromMessage(message);
    String contentHtml = getHtmlFromMessage(message);
    Optional<ResolvedInject> resolved = injectResolver(content, contentHtml);
    if (resolved.isPresent()) {
      String injectId = resolved.get().injectId();
      String tenantId = resolved.get().tenantId();
      TxCtx tenantScope = TxCtx.forTenant(tenantId);
      boolean messageAlreadyAvailable =
          tenantScopedTransaction.execute(
              tenantScope,
              () ->
                  communicationRepository.existsByIdentifierAndInjectTenantId(messageID, tenantId));
      if (!messageAlreadyAvailable) {
        List<String> participants = computeParticipants(message);
        List<User> users =
            tenantScopedTransaction.execute(
                tenantScope,
                () -> userRepository.findAllByEmailInIgnoreCaseAndTenantId(participants, tenantId));
        if (!users.isEmpty()) {
          String subject = message.getSubject();
          String from = String.valueOf(Arrays.stream(message.getFrom()).toList().get(0));
          String to = String.valueOf(Arrays.stream(message.getAllRecipients()).toList());
          // Save messaging
          Communication communication = new Communication();
          // Both dates are nullable by the JavaMail contract (a server that exposes only
          // INTERNALDATE, a draft); the field keeps its own now() default, which is what the NOT
          // NULL column needs, instead of a toInstant() on null.
          Date receivedDate = message.getReceivedDate();
          if (receivedDate != null) {
            communication.setReceivedAt(receivedDate.toInstant());
          }
          Date sentDate = message.getSentDate();
          if (sentDate != null) {
            communication.setSentAt(sentDate.toInstant());
          }
          communication.setSubject(subject);
          communication.setContent(content);
          communication.setContentHtml(contentHtml);
          communication.setIdentifier(messageID);
          communication.setUsers(users);
          communication.setAnimation(isSent);
          communication.setFrom(from);
          communication.setTo(to);
          try {
            RecordedCommunication recorded =
                tenantScopedTransaction.execute(
                    tenantScope, () -> recordCommunication(injectId, communication));
            // Upload attachments in communication
            final MimeMessageParser mimeParser = new MimeMessageParser(mimeMessage).parse();
            final List<DataSource> attachmentList = mimeParser.getAttachmentList();
            final List<String> uploads = new ArrayList<>();
            String exerciseId = recorded.exerciseId();
            for (DataSource dataSource : attachmentList) {
              final String fileName = dataSource.getName();
              String path =
                  exerciseId != null
                      ? "/" + exerciseId + "/communications/" + recorded.communicationId()
                      : "/communications/" + recorded.communicationId();
              // Explicit tenant: this job sets no ambient TenantContext, so the object is written
              // under the inject's own tenant path.
              String uploadName =
                  fileService.uploadStream(tenantId, path, fileName, dataSource.getInputStream());
              uploads.add(uploadName);
            }
            // Add attachment in the communication
            tenantScopedTransaction.execute(
                tenantScope,
                () ->
                    communicationRepository
                        .findById(recorded.communicationId())
                        .ifPresent(
                            comm -> {
                              comm.setAttachments(uploads.toArray(String[]::new));
                              communicationRepository.save(comm);
                            }));
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }
        }
      }
    }
  }

  /**
   * The per-inbox read cursor is a platform row of the dual-scope {@code parameters} table ({@code
   * tenant_id IS NULL}): it tracks how far this IMAP account has read, not any tenant's data. Each
   * DB-only step runs in its own short, platform-write transaction; the IMAP network calls (message
   * count, fetch, and {@link #parseMessages}, which opens its own per-tenant transactions) run
   * outside any transaction, in between.
   */
  private void synchronizeBox(Folder inbox, Boolean isSent) throws Exception {
    String inboxKey = username + "-imap-" + inbox.getName();
    int startMessageNumber =
        tenantScopedTransaction.executeAsPlatformWrite(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
            () -> {
              Setting current =
                  settingRepository
                      .findByKeyAndTenantIsNull(inboxKey)
                      .orElseGet(() -> settingRepository.save(new Setting(inboxKey, "0")));
              return parseInt(current.getValue());
            });
    int messageCount = inbox.getMessageCount();
    if (startMessageNumber < messageCount) {
      log.info(
          "synchronizeInbox {} from {} to {}", inbox.getName(), startMessageNumber, messageCount);
      int start = startMessageNumber + 1;
      Message[] messages = inbox.getMessages(start, messageCount);
      if (messages.length > 0) {
        parseMessages(messages, isSent);
      }
    }
    int finalMessageCount = messageCount;
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Setting current = settingRepository.findByKeyAndTenantIsNull(inboxKey).orElseThrow();
          current.setValue(String.valueOf(finalMessageCount));
          settingRepository.save(current);
        });
  }

  private void tryToSynchronizeFolderFromBox(String folderName, Boolean isSent) throws Exception {
    try (Folder folderBox = imapStore.getFolder(folderName)) {
      folderBox.open(Folder.READ_ONLY);
      synchronizeBox(folderBox, isSent);
    }
  }

  private void syncFolders() throws Exception {
    // Sync sent
    tryToSynchronizeFolderFromBox(sentFolder, true);
    // Sync received
    for (String listeningFolder : inboxFolders) {
      tryToSynchronizeFolderFromBox(listeningFolder, false);
    }
  }

  /**
   * Retries {@link #syncFolders} up to three times, two seconds apart, and drops the store after
   * the last failure so the next tick builds a new one from scratch.
   *
   * <p>The retry loop has to own the {@link MessagingException}: when {@code syncFolders} caught it
   * and called this method instead, no call here could ever fail, so the first connection failure
   * recursed between the two methods until the scheduler thread died with a {@link
   * StackOverflowError}, without a single retry and without ever closing the store.
   */
  private void retrySyncFolders() throws Exception {
    MessagingException lastFailure = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        syncFolders();
        return;
      } catch (MessagingException e) {
        lastFailure = e;
        log.warn("Retrying connection... ({}/3) {}", attempt + 1, e.getMessage(), e);
        if (attempt < 2) {
          Thread.sleep(2000);
        }
      }
    }
    // A closed store cannot be reconnected; nulling it makes the next tick build a fresh one.
    if (imapStore != null) {
      try {
        if (imapStore.isConnected()) {
          imapStore.close();
        }
      } catch (MessagingException e) {
        log.warn("Could not close the IMAP store: {}", e.getMessage(), e);
      }
      imapStore = null;
    }
    throw lastFailure;
  }

  // Sync folders every 10 sec
  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  public void connectionListener() throws Exception {
    if (enabled) {
      if (imapStore == null) {
        // init() logs the failure that left it null; retry the store creation on this tick
        try {
          initStore();
        } catch (Exception e) {
          log.warn("Could not create the IMAP store: {}", e.getMessage(), e);
          return;
        }
      }
      if (!imapStore.isConnected()) {
        try {
          imapStore.connect(host, port, username, password);
          this.saveServiceState(IMAP_SETTINGS_KEY, true);
        } catch (MessagingException e) {
          log.warn(e.getMessage());
          this.saveServiceState(IMAP_SETTINGS_KEY, false);
          return;
        }
      }
      try {
        retrySyncFolders();
      } catch (MessagingException e) {
        log.warn("IMAP folder sync failed after retries: {}", e.getMessage(), e);
        this.saveServiceState(IMAP_SETTINGS_KEY, false);
      }
    }
  }

  public void storeSentMessage(MimeMessage message) throws Exception {
    if (enabled) {
      try (Folder folder = imapStore.getFolder(sentFolder)) {
        folder.open(Folder.READ_WRITE);
        message.setFlag(Flags.Flag.SEEN, true);
        folder.appendMessages(new Message[] {message});
      }
    }
  }

  @Override
  public SettingRepository getSettingRepository() {
    return settingRepository;
  }

  @Override
  public TenantScopedTransaction getTenantScopedTransaction() {
    return tenantScopedTransaction;
  }
}
