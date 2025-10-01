package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_92__Role extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
                   CREATE TABLE roles (
                       role_id varchar(255) not null,
                       role_name varchar(255) not null,
                       primary key (role_id)
                   );
                """);

    select.execute(
        """
                   CREATE TABLE roles_capabilities (
                       role_id varchar(255) not null
                            constraint role_id_fk
                               references roles,
                       capability varchar(255) not null,
                       primary key (role_id, capability)
                   );
                """);

    select.execute(
        """
                   CREATE TABLE groups_roles (
                       role_id varchar(255) not null
                            constraint role_id_fk
                               references roles,
                       group_id varchar(255) not null
                            constraint group_id_fk
                               references groups,
                       primary key (role_id, group_id)
                   );
                """);
  }
}
