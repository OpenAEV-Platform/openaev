package io.openaev.utils;

/** Throwaway class with no test, to check the coverage rule and its waiver. Never merged. */
public final class CoverageWaiverProbe {

  private CoverageWaiverProbe() {}

  public static int clamp(int value, int low, int high) {
    if (value < low) {
      return low;
    }
    if (value > high) {
      return high;
    }
    return value;
  }
}
