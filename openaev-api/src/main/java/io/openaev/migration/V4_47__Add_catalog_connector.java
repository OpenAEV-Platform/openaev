package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_47__Add_catalog_connector extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
        CREATE TYPE connector_type AS ENUM ('COLLECTOR', 'INJECTOR', 'EXECUTOR');
        CREATE TABLE catalog_connectors (
            catalog_connector_id VARCHAR(255) NOT NULL CONSTRAINT catalog_connectors_pkey PRIMARY KEY,
            catalog_connector_title VARCHAR(255) NOT NULL UNIQUE,
            catalog_connector_slug VARCHAR(255) NOT NULL UNIQUE,
            catalog_connector_description TEXT,
            catalog_connector_short_description TEXT,
            catalog_connector_logo_url VARCHAR(255),
            catalog_connector_use_cases TEXT[],
            catalog_connector_verified BOOLEAN DEFAULT false,
            catalog_connector_last_verified_date TIMESTAMP,
            catalog_connector_playbook_supported BOOLEAN DEFAULT false,
            catalog_connector_max_confidence_level INTEGER,
            catalog_connector_support_version VARCHAR(50),
            catalog_connector_subscription_link VARCHAR(255),
            catalog_connector_source_code VARCHAR(255),
            catalog_connector_manager_supported BOOLEAN DEFAULT false,
            catalog_connector_container_version VARCHAR(50),
            catalog_connector_container_image VARCHAR(255),
            catalog_connector_type connector_type,
            catalog_connector_class_name VARCHAR(255),
            catalog_connector_deleted_at TIMESTAMP WITH TIME ZONE,
            catalog_connector_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            catalog_connector_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
      select.execute(
          """
        CREATE TABLE catalog_connectors_configuration (
            connector_configuration_id VARCHAR(255) NOT NULL CONSTRAINT connectors_configuration_pkey PRIMARY KEY,
            connector_configuration_catalog_id VARCHAR(255) NOT NULL REFERENCES catalog_connectors(catalog_connector_id) ON DELETE CASCADE,
            connector_configuration_key VARCHAR(255) NOT NULL,
            connector_configuration_default VARCHAR(255),
            connector_configuration_description TEXT,
            connector_configuration_type VARCHAR(255) NOT NULL,
            connector_configuration_format VARCHAR(255),
            connector_configuration_enum VARCHAR(255),
            connector_configuration_writeonly BOOLEAN NOT NULL DEFAULT false,
            connector_configuration_required BOOLEAN NOT NULL DEFAULT false,
            connector_configuration_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            connector_configuration_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
      select.execute(
          """
        CREATE TABLE connector_instances (
            connector_instance_id VARCHAR(255) NOT NULL CONSTRAINT connector_instances_pkey PRIMARY KEY,
            connector_instance_catalog_id VARCHAR(255) NOT NULL REFERENCES catalog_connectors(catalog_connector_id) ON DELETE CASCADE,
            connector_instance_current_status VARCHAR(255) NOT NULL,
            connector_instance_requested_status VARCHAR(255),
            connector_instance_restart_count INTEGER,
            connector_instance_started_at TIMESTAMP,
            connector_instance_is_in_reboot_loop BOOLEAN DEFAULT false,
            connector_instance_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            connector_instance_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
      select.execute(
          """
        CREATE TABLE connector_instance_configurations (
            connector_instance_configuration_id VARCHAR(255) NOT NULL CONSTRAINT connector_instance_configuration_pkey PRIMARY KEY,
            connector_instance_configuration_key VARCHAR(255) NOT NULL,
            connector_instance_configuration_value JSONB,
            connector_instance_id VARCHAR(255) NOT NULL REFERENCES connector_instances(connector_instance_id) ON DELETE CASCADE,
            connector_instance_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            connector_instance_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
       """);

      select.execute(
          """
              Alter TABLE  injectors ADD COLUMN injector_connector_instance_id VARCHAR(255) REFERENCES connector_instances(connector_instance_id) ON DELETE CASCADE ;
          """);
      select.execute(
          """
              Alter TABLE  collectors ADD COLUMN collector_connector_instance_id VARCHAR(255) REFERENCES connector_instances(connector_instance_id) ON DELETE CASCADE ;
          """);
      select.execute(
          """
              Alter TABLE  executors ADD COLUMN executor_connector_instance_id VARCHAR(255) REFERENCES connector_instances(connector_instance_id) ON DELETE CASCADE ;
          """);
    }
  }
}
