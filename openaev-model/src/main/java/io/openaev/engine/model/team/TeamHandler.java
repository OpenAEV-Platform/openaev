package io.openaev.engine.model.team;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawTeam;
import io.openaev.database.repository.TeamRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TeamHandler implements Handler<EsTeam> {

  private final TeamRepository teamRepository;

  @Override
  public List<EsTeam> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawTeam> forIndexing = teamRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            team -> {
              EsTeam esTeam = new EsTeam();
              // Base
              esTeam.setBase_id(team.getTeam_id());
              esTeam.setBase_created_at(team.getTeam_created_at());
              esTeam.setBase_updated_at(team.getTeam_updated_at());
              esTeam.setBase_representative(team.getTeam_name());
              esTeam.setBase_restrictions(buildRestrictions(team.getTeam_id()));
              // Specific
              return esTeam;
            })
        .toList();
  }
}
