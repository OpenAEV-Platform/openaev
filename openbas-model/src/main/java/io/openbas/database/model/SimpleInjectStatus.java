package io.openbas.database.model;

import jakarta.persistence.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.RowMapper;

@Data
@NoArgsConstructor
public class SimpleInjectStatus {

  private String id;
  private ExecutionStatus name;
  private Instant trackingSentDate; // To Queue / processing engine
  private Instant trackingEndDate; // Done task from injector
  private String injectId;

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public SimpleInjectStatus(InjectStatus injectStatus) {
    this.id = injectStatus.getId();
    this.name = injectStatus.getName();
    this.trackingSentDate = injectStatus.getTrackingSentDate();
    this.trackingEndDate = injectStatus.getTrackingEndDate();
    this.injectId = injectStatus.getInject().getId();
  }

  public static class SimpleInjectStatusRowMapper implements RowMapper<SimpleInjectStatus> {

    @Override
    public SimpleInjectStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
      SimpleInjectStatus simpleInjectStatus = new SimpleInjectStatus();
      simpleInjectStatus.setId(rs.getString("status_id"));
      simpleInjectStatus.setName(ExecutionStatus.valueOf(rs.getString("status_name")));
      if (rs.getTimestamp("tracking_end_date") != null) {
        simpleInjectStatus.setTrackingEndDate(rs.getTimestamp("tracking_end_date").toInstant());
      }
      if (rs.getTimestamp("tracking_sent_date") != null) {
        simpleInjectStatus.setTrackingSentDate(rs.getTimestamp("tracking_sent_date").toInstant());
      }
      simpleInjectStatus.setInjectId(rs.getString("status_inject"));

      return simpleInjectStatus;
    }
  }
}
