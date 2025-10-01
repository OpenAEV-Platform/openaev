package io.openaev.utils.mapper;

import io.openaev.database.model.Grant;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.raw.RawUserAuthFlat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RawUserAuthMapper {

  public RawUserAuth toRawUserAuth(List<RawUserAuthFlat> flatList) {
    if (flatList == null || flatList.isEmpty()) {
      return null;
    }

    RawUserAuthFlat first = flatList.get(0);
    String userId = first.getUser_id();
    boolean userAdmin = first.getUser_admin();

    Set<RawGrant> grants =
        flatList.stream()
            .filter(row -> row.getGrant_id() != null) // Skip null grants (e.g. user has no grants)
            .map(
                row ->
                    new RawGrant() {
                      @Override
                      public String getGrant_id() {
                        return row.getGrant_id();
                      }

                      @Override
                      public String getGrant_name() {
                        return row.getGrant_name();
                      }

                      @Override
                      public String getUser_id() {
                        return row.getUser_id();
                      }

                      @Override
                      public String getGrant_resource() {
                        return row.getGrant_resource();
                      }

                      @Override
                      public Grant.GRANT_RESOURCE_TYPE getGrant_resource_type() {
                        return row.getGrant_resource_type();
                      }
                    })
            .collect(Collectors.toSet());

    return new RawUserAuth() {
      @Override
      public String getUser_id() {
        return userId;
      }

      @Override
      public boolean getUser_admin() {
        return userAdmin;
      }

      @Override
      public Set<RawGrant> getUser_grants() {
        return grants;
      }
    };
  }
}
