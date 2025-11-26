package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_49__Init_domains_categorization extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute(
                    """
                        insert into payloads_domains (payload_id, domain_id)
                        select p.payload_id, d.domain_id from payloads p
                        inner join domains d on d.domain_name = 'To classify';
                    """);
            stmt.execute(
                    """
                        insert into injectors_contracts_domains (injector_contract_id, domain_id)
                        select ic.injector_contract_id, d.domain_id from injectors_contracts ic
                        inner join domains d on d.domain_name = 'To classify'
                        where ic.injector_contract_payload is null;
                    """);
        }
    }
}
