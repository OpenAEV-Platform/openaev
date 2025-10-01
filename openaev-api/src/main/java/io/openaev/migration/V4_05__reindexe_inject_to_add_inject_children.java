package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_05__reindexe_inject_to_add_inject_children extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // ADD TRIGGER function used to update injectorContract updated at after add or remove attack
      // pattern
      String updateInjectorContractUpdatedAt =
          """
            CREATE OR REPLACE FUNCTION update_injector_contract_updated_at()
                RETURNS TRIGGER AS $$
            BEGIN
                UPDATE public.injectors_contracts
                SET injector_contract_updated_at = now()
                WHERE injector_contract_id = OLD.injector_contract_id;  -- Use NEW. if it is AFTER INSERT

                RETURN OLD;
            END;
            $$ LANGUAGE plpgsql;

            -- Trigger for AFTER DELETE
            CREATE TRIGGER after_delete_update_injector_contract_updated_at
                AFTER DELETE ON public.injectors_contracts_attack_patterns
                FOR EACH ROW
            EXECUTE FUNCTION update_injector_contract_updated_at();

            -- Trigger for AFTER INSERT
            CREATE TRIGGER after_insert_update_injector_contract_updated_at
                AFTER INSERT ON public.injectors_contracts_attack_patterns
                FOR EACH ROW
            EXECUTE FUNCTION update_injector_contract_updated_at();


            CREATE OR REPLACE FUNCTION update_inject_updated_at_after_delete_inject_child()
                RETURNS TRIGGER AS $$
            BEGIN
                UPDATE public.injects
                SET inject_updated_at = now()
                WHERE inject_id = OLD.inject_parent_id;
                RETURN OLD;
            END;
            $$ LANGUAGE plpgsql;

            -- Trigger for AFTER DELETE
            CREATE TRIGGER after_delete_update_inject_updated_at
                AFTER DELETE ON public.injects_dependencies
                FOR EACH ROW EXECUTE FUNCTION update_inject_updated_at_after_delete_inject_child();
            """;

      statement.executeUpdate(updateInjectorContractUpdatedAt);
      // re-index injects in ES
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'attack-pattern';");
      statement.executeUpdate("DELETE FROM indexing_status WHERE indexing_status_type = 'inject';");
    }
  }
}
