package io.openaev.migration;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.ARTICLE;
import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.MANUAL;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.model.inject.form.Expectation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_63__InjectExpectation_upgrade extends BaseJavaMigration {

  public static final String EXPECTATION_MANUAL_NAME =
      "The animation team can validate the audience reaction";
  public static final String EXPECTATION_ARTICLE_NAME = "Expect audiences to read the article(s)";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Upgrade Inject Expectation table
    select.execute(
        """
        ALTER TABLE injects_expectations ADD inject_expectation_name varchar(255);
        ALTER TABLE injects_expectations ADD inject_expectation_description text;
        """);
    // Migration datas
    ObjectMapper mapper = new ObjectMapper();
    ResultSet results =
        select.executeQuery(
            """
        SELECT * FROM injects
        WHERE inject_type = 'openex_email' OR inject_type = 'openex_ovh_sms' OR inject_type = 'openex_media'
        """);
    PreparedStatement statement =
        connection.prepareStatement("UPDATE injects SET inject_content = ? WHERE inject_id = ?");
    while (results.next()) {
      String content = results.getString("inject_content");
      if (!content.equals("null")) {
        String id = results.getString("inject_id");
        String type = results.getString("inject_type");
        if (Objects.equals(type, "openex_ovh_sms")) {
          OvhSmsContentOld ovhSms = mapper.readValue(content, OvhSmsContentOld.class);
          content = mapper.writeValueAsString(ovhSms.toNewContent());
        } else if (Objects.equals(type, "openex_email")) {
          EmailContentOld email = mapper.readValue(content, EmailContentOld.class);
          content = mapper.writeValueAsString(email.toNewContent());
        } else if (Objects.equals(type, "openex_media")) {
          MediaContentOld channel = mapper.readValue(content, MediaContentOld.class);
          content = mapper.writeValueAsString(channel.toNewContent());
        }
        statement.setString(1, content);
        statement.setString(2, id);
        statement.addBatch();
      }
    }
    statement.executeBatch();
  }

  @Data
  public static class OvhSmsContentOld {

    private String message;
    private String expectationType;
    private Double expectationScore;

    OvhSmsContentNew toNewContent() {
      OvhSmsContentNew content = new OvhSmsContentNew();
      content.setMessage(this.message);
      Expectation expectation = new Expectation();
      expectation.setScore(this.expectationScore);
      expectation.setType(MANUAL);
      expectation.setName(EXPECTATION_MANUAL_NAME);
      content.setExpectations(List.of(expectation));
      return content;
    }
  }

  @Data
  public static class OvhSmsContentNew {
    private String message;
    private List<Expectation> expectations;
  }

  @Data
  public static class EmailContentOld {

    private String body;
    private String subject;
    private String inReplyTo;
    private boolean encrypted;
    private String expectationType;
    private Double expectationScore;

    EmailContent toNewContent() {
      EmailContent content = new EmailContent();
      content.setBody(this.body);
      content.setSubject(this.subject);
      content.setInReplyTo(this.inReplyTo);
      content.setEncrypted(this.encrypted);
      Expectation expectation = new Expectation();
      expectation.setScore(this.expectationScore);
      expectation.setType(MANUAL);
      expectation.setName(EXPECTATION_MANUAL_NAME);
      content.setExpectations(List.of(expectation));
      return content;
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class MediaContentOld extends EmailContentOld {

    private List<String> articles;
    private boolean expectation;
    private Double expectationScore;
    private boolean emailing;

    ChannelContent toNewContent() {
      ChannelContent content = new ChannelContent();
      content.setBody(this.getBody());
      content.setSubject(this.getSubject());
      content.setInReplyTo(this.getInReplyTo());
      content.setEncrypted(this.isEncrypted());
      content.setArticles(this.articles);
      content.setEmailing(this.emailing);
      Expectation expectation = new Expectation();
      expectation.setScore(this.expectationScore);
      expectation.setType(ARTICLE);
      expectation.setName(EXPECTATION_ARTICLE_NAME);
      content.setExpectations(List.of(expectation));
      return content;
    }
  }
}
