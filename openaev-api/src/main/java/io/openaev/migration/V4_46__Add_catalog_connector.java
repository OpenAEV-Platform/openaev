package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_46__Add_catalog_connector extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            select.execute("""
                CREATE TABLE catalog_connectors (
                    connector_id VARCHAR(255) NOT NULL CONSTRAINT catalog_connectors_pkey PRIMARY KEY,
                    connector_title VARCHAR(255) NOT NULL,
                    connector_slug VARCHAR(255) NOT NULL UNIQUE,
                    connector_description VARCHAR(255),
                    connector_short_description VARCHAR(255),
                    connector_logo_url VARCHAR(255),
                    connector_use_cases text[],
                    connector_verified BOOLEAN,
                    connector_last_verified_date TIMESTAMP,
                    connector_playbook_supported BOOLEAN,
                    connector_max_confidence_level INTEGER,
                    connector_support_version VARCHAR(50),
                    connector_subscription_link VARCHAR(255),
                    connector_source_code VARCHAR(255),
                    connector_manager_supported BOOLEAN,
                    connector_container_version VARCHAR(50),
                    connector_container_image VARCHAR(255),
                    connector_container_type VARCHAR(255),
                    catalog_connector_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                    catalog_connector_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
                  );
                """);
            select.execute("""
                CREATE TABLE catalog_connectors_configuration (
                    connector_id VARCHAR(255) NOT NULL CONSTRAINT connectors_configuration_pkey PRIMARY KEY,
                    connector_configuration_catalog varchar(255) constraint catalog_connectors_pkey references catalog_connectors,
                    connector_configuration_key VARCHAR(255) NOT NULL,
                    connector_configuration_default VARCHAR(255),
                    connector_configuration_description VARCHAR(255),
                    connector_configuration_type VARCHAR(255) NOT NULL,
                    connector_configuration_format VARCHAR(255),
                    connector_configuration_enum VARCHAR(255),
                    connector_configuration_writeonly BOOLEAN,
                    connector_configuration_required BOOLEAN,
                  );
                """);
            select.execute(
            """
                CREATE TABLE connector_instances (
                    connector_instance_id VARCHAR(255) NOT NULL CONSTRAINT connector_instances_pkey PRIMARY KEY,
                    connector_instance_hash VARCHAR(255) NOT NULL,
                    connector_instance_catalog varchar(255) constraint catalog_connectors_pkey references catalog_connectors,
                    connector_instance_current_status VARCHAR(255) NOT NULL,
                    connector_instance_requested_status VARCHAR(255),
                    connector_instance_restart_count INTEGER,
                    connector_instance_started_at TIMESTAMP,
                    connector_instance_is_in_reboot_loop BOOLEAN,
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
                    connector_instance varchar(255) constraint connector_instances_pkey references connector_instances,
                    connector_instance_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                    connector_instance_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
                  );
                """);
        }
    }
}
