package io.openaev.rest.document.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonInclude(NON_EMPTY)
public class DocumentRelationsOutput {

  private Set<RelatedEntityOutput> simulations;

  private Set<RelatedEntityOutput> securityPlatforms;

  private Set<RelatedEntityOutput> channels;

  private Set<RelatedEntityOutput> payloads;

  private Set<RelatedEntityOutput> scenarioArticles;

  private Set<RelatedEntityOutput> simulationArticles;

  private Set<RelatedEntityOutput> atomicTestings;

  private Set<RelatedEntityOutput> scenarioInjects;

  private Set<RelatedEntityOutput> simulationInjects;

  private Set<RelatedEntityOutput> challenges;
}
