package io.openaev.service.attackpath.dto;

/**
 * The outcome of playing one stage of a live seed replay: which stage was played, how many there
 * are in total, whether the replay is now complete, and a human label for the stage.
 */
public record AttackPathReplayStepDTO(int stage, int totalStages, boolean done, String label) {}
