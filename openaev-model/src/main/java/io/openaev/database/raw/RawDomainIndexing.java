package io.openaev.database.raw;

import java.time.Instant;

public interface RawDomainIndexing extends RawTenant {

  String getDomain_id();

  String getDomain_name();

  String getDomain_color();

  Instant getDomain_created_at();

  Instant getDomain_updated_at();
}
