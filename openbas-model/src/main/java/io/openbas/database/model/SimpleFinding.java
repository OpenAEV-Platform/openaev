package io.openbas.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.persistence.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import org.springframework.jdbc.core.RowMapper;

@Data
public class SimpleFinding implements Base {
  private String id;
  private String field;
  protected String type;
  protected String value;
  private String[] labels = new String[0];
  protected String name;
  private Set<String> tags = new HashSet<>();
  private String injectId;
  private Instant creationDate = now();
  private Instant updateDate = now();
  private List<String> assets = new ArrayList<>();
  private List<String> teams = new ArrayList<>();
  private List<String> users = new ArrayList<>();

  public static class SimpleFindingRowMapper implements RowMapper<SimpleFinding> {

    @Resource protected ObjectMapper mapper;

    @Override
    public SimpleFinding mapRow(ResultSet rs, int rowNum) throws SQLException {
      SimpleFinding simpleFinding = new SimpleFinding();
      simpleFinding.setId(rs.getString("finding_id"));
      simpleFinding.setField(rs.getString("finding_field"));
      simpleFinding.setType(rs.getString("finding_type"));
      simpleFinding.setValue(rs.getString("finding_value"));
      simpleFinding.setLabels((String[]) rs.getArray("finding_labels").getArray());
      simpleFinding.setName(rs.getString("finding_name"));
      simpleFinding.setInjectId(rs.getString("finding_inject_id"));
      simpleFinding.setCreationDate(rs.getTimestamp("finding_created_at").toInstant());
      simpleFinding.setUpdateDate(rs.getTimestamp("finding_updated_at").toInstant());

      return simpleFinding;
    }
  }
}
