package io.openaev.api.graph.dto;

/** The relation kinds of the exploration graph (#9388), one per link the UI can expand by. */
public enum GraphEdgeType {
  /** Asset group contains asset. */
  MEMBER_OF,
  /** Finding observed on asset. */
  FOUND_ON,
  /** Inject produced finding. */
  PRODUCED,
  /** Inject targeted asset or asset group, with its expectation result. */
  TARGETS,
  /** Inject belongs to a simulation. */
  PART_OF,
  /** Simulation is a run of a scenario. */
  RUN_OF
}
