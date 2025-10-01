package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_24__Refactor_Grants_Table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement stmt = connection.createStatement();
    stmt.execute(
        """
          -- Step 1: Add both new columns
          ALTER TABLE grants ADD COLUMN grant_resource varchar(255) DEFAULT NULL;
          ALTER TABLE grants ADD COLUMN grant_resource_type varchar(50);
          -- Step 2: Migrate data from grant_exercise to grant_resource with type
          UPDATE grants
          SET grant_resource = grant_exercise,
              grant_resource_type = 'SIMULATION'
          WHERE grant_exercise IS NOT NULL;
          -- Step 3: Migrate data from grant_scenario to grant_resource with type
          UPDATE grants
          SET grant_resource = grant_scenario,
              grant_resource_type = 'SCENARIO'
          WHERE grant_scenario IS NOT NULL
            AND grant_resource IS NULL;
          -- Step 4: Add NOT NULL constraint to grant_resource_type if desired
          ALTER TABLE grants
          ALTER COLUMN grant_resource_type SET NOT NULL;
          -- Step 5: Drop the unique indexes that reference the old columns
          DROP INDEX IF EXISTS grant_exercise;
          DROP INDEX IF EXISTS grant_scenario;
          -- Step 6: Drop the regular indexes on the old columns
          DROP INDEX IF EXISTS idx_64adc7d6cae7a988;
          -- Step 7: Drop the old columns (this will also drop their foreign key constraints)
          ALTER TABLE grants DROP COLUMN grant_exercise;
          ALTER TABLE grants DROP COLUMN grant_scenario;
          -- Step 8: Create new indexes
          CREATE INDEX idx_grant_resource ON grants (grant_resource);
          CREATE INDEX idx_grant_resource_type ON grants (grant_resource_type);
          CREATE INDEX idx_grant_resource_type_resource ON grants (grant_resource_type, grant_resource);
          -- Step 9: Create a new unique index
          CREATE UNIQUE INDEX grant_resource_unique ON grants (grant_group, grant_resource, grant_name);
          -- Step 10: Create the new unified table for group default grants
          CREATE TABLE groups_default_grants (
              group_id VARCHAR(255) NOT NULL,
              grant_type VARCHAR(255) NOT NULL,
              grant_resource_type VARCHAR(50) NOT NULL,
              CONSTRAINT fk_groups_default_grants_group_id
                  FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE
          );
          -- Step 11: Migrate data from exercises tabl
          INSERT INTO groups_default_grants (group_id, grant_type, grant_resource_type)
          SELECT group_id, exercises_default_grants, 'SIMULATION'
          FROM groups_exercises_default_grants;
          -- Step 12: Migrate data from scenarios table
          INSERT INTO groups_default_grants (group_id, grant_type, grant_resource_type)
          SELECT group_id, scenarios_default_grants, 'SCENARIO'
          FROM groups_scenarios_default_grants;
          -- Step 13: Create indexes for performance
          CREATE INDEX idx_groups_default_grants_group_id ON groups_default_grants(group_id);
          CREATE INDEX idx_groups_default_grants_resource_type ON groups_default_grants(grant_resource_type);
          -- Step 14: Drop the old tables
          DROP TABLE groups_exercises_default_grants;
          DROP TABLE groups_scenarios_default_grants;
          """);
  }

  /* ROLLBACK
  -- Step 1: Drop the new indexes
  DROP INDEX IF EXISTS grant_resource_unique;
  DROP INDEX IF EXISTS idx_grant_resource_type_resource;
  DROP INDEX IF EXISTS idx_grant_resource_type;
  DROP INDEX IF EXISTS idx_grant_resource;
  -- Step 2: Re-add the grant_exercise and grant_scenario columns
  ALTER TABLE grants
  ADD COLUMN grant_exercise varchar(255) DEFAULT NULL,
  ADD COLUMN grant_scenario varchar(255) DEFAULT NULL;
  -- Step 3: Re-add the foreign key constraints
  ALTER TABLE grants
  ADD CONSTRAINT fk_64adc7d6cae7a988
  FOREIGN KEY (grant_exercise)
  REFERENCES exercises
  ON DELETE CASCADE;
  ALTER TABLE grants
  ADD CONSTRAINT scenario_fk
  FOREIGN KEY (grant_scenario)
  REFERENCES scenarios
  ON DELETE CASCADE;
  -- Step 4: Migrate data back based on grant_resource_type
  UPDATE grants
  SET grant_exercise = grant_resource
  WHERE grant_resource_type = 'SIMULATION';
  UPDATE grants
  SET grant_scenario = grant_resource
  WHERE grant_resource_type = 'SCENARIO';
  -- Step 5: Re-create the original indexes
  CREATE INDEX idx_64adc7d6cae7a988 ON grants (grant_exercise);
  CREATE UNIQUE INDEX grant_exercise ON grants (grant_group, grant_exercise, grant_name);
  CREATE UNIQUE INDEX grant_scenario ON grants (grant_group, grant_scenario, grant_name);
  -- Step 6: Drop the new columns
  ALTER TABLE grants
  DROP COLUMN grant_resource,
  DROP COLUMN grant_resource_type;
  -- Step 7: Recreate the old tables
  CREATE TABLE groups_exercises_default_grants (
      group_id VARCHAR(255) NOT NULL,
      exercises_default_grants VARCHAR(255),
      CONSTRAINT fk_exercises_grants_group_id
          FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE
  );
  CREATE TABLE groups_scenarios_default_grants (
      group_id VARCHAR(255) NOT NULL,
      scenarios_default_grants VARCHAR(255),
      CONSTRAINT fk_scenarios_grants_group_id
          FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE
  );
  -- Step 8: Migrate data back
  INSERT INTO groups_exercises_default_grants (group_id, exercises_default_grants)
  SELECT group_id, grant_type
  FROM groups_default_grants
  WHERE grant_resource_type = 'SIMULATION';
  INSERT INTO groups_scenarios_default_grants (group_id, scenarios_default_grants)
  SELECT group_id, grant_type
  FROM groups_default_grants
  WHERE grant_resource_type = 'SCENARIO';
  -- Step 9: Drop the unified table
  DROP TABLE groups_default_grants;
    */
}
