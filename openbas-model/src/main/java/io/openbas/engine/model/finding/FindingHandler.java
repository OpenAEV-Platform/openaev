package io.openbas.engine.model.finding;

import static io.openbas.engine.EsUtils.buildRestrictions;
import static org.springframework.util.StringUtils.hasText;

import io.openbas.database.raw.RawFinding;
import io.openbas.database.repository.FindingRepository;
import io.openbas.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FindingHandler implements Handler<EsFinding> {

  private FindingRepository findingRepository;

  @Autowired
  public void setFindingRepository(FindingRepository findingRepository) {
    this.findingRepository = findingRepository;
  }

  @Override
  public List<EsFinding> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawFinding> forIndexing = findingRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            finding -> {
              EsFinding esFinding = new EsFinding();
              // Base
              esFinding.setBase_id(finding.getFinding_id());
              esFinding.setBase_representative(finding.getFinding_value());
              esFinding.setBase_created_at(finding.getFinding_created_at());
              esFinding.setBase_updated_at(finding.getFinding_updated_at());
              esFinding.setBase_restrictions(buildRestrictions(finding.getInject_scenario()));
              // Specific
              esFinding.setFinding_type(finding.getFinding_type());
              esFinding.setFinding_field(finding.getFinding_field());
              esFinding.setFinding_value(finding.getFinding_value());
              // Dependencies
              List<String> dependencies = new ArrayList<>();
              if (hasText(finding.getFinding_inject_id())) {
                dependencies.add(finding.getFinding_inject_id());
                esFinding.setBase_inject_side(finding.getFinding_inject_id());
              }
              if (hasText(finding.getInject_scenario())) {
                dependencies.add(finding.getInject_scenario());
                esFinding.setBase_scenario_side(finding.getInject_scenario());
              }
              esFinding.setBase_dependencies(dependencies);
              return esFinding;
            })
        .toList();
  }
}
