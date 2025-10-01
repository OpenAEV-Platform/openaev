package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_34__Inject_communication extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Injects
    select.execute(
        """
                            create table communications
                            (
                                communication_id varchar(255) not null constraint communications_pkey primary key,
                                communication_received_at timestamp not null,
                                communication_sent_at timestamp not null,
                                communication_subject text,
                                communication_content text,
                                communication_message_id text not null,
                                communication_inject varchar(255)
                                    constraint fk_communication_inject references injects on delete cascade
                            );
                            create index idx_communication_subject on communications (communication_subject);
                            create index idx_communications on communications (communication_id);
                """);
    // Create communications_users
    select.execute(
        """
                CREATE TABLE communications_users (
                    communication_id varchar(255) not null constraint communication_id_fk references communications on delete cascade,
                    user_id varchar(255) not null constraint user_id_fk references users on delete cascade,
                    constraint communications_users_pkey primary key (communication_id, user_id)
                );
                CREATE INDEX idx_communications_users_communication on communications_users (communication_id);
                CREATE INDEX idx_communications_users_user on communications_users (user_id);
                """);
  }
}
