package io.openaev.architecture.fixture;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixtures for the #8102 guard. Each inner class is one shape the rule must judge, so the rule is
 * proven to fire on the real defect AND proven not to fire on the shapes that are safe for a
 * reason. The pass-through fixture is the #8102 shape itself: the transactional boundary is not the
 * method the catch wraps, it is one call deeper.
 */
public final class HandledErrorRollbackFixtures {

  private HandledErrorRollbackFixtures() {}

  /** The inner boundary: a checked exception plus a broad rollbackFor, which is half the cause. */
  public static class CheckedBoundary {

    @Transactional(rollbackFor = Exception.class)
    public void write() throws BoundaryFailure {
      throw new BoundaryFailure();
    }

    /** Its own transaction, so a failure rolls back only itself and marks nothing shared. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void writeIsolated() throws BoundaryFailure {
      throw new BoundaryFailure();
    }

    /** The fix on the inner boundary: the exception the caller handles does not roll back. */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BoundaryFailure.class)
    public void writeHandledFailure() throws BoundaryFailure {
      throw new BoundaryFailure();
    }
  }

  /** Carries no transaction of its own: it only forwards, which is what hides the boundary. */
  public static class PassThrough {

    private final CheckedBoundary boundary = new CheckedBoundary();

    public void forward() throws BoundaryFailure {
      boundary.write();
    }
  }

  /** The defect: a transaction is open, the boundary marks it, and the catch returns a success. */
  public static class CatchingCaller {

    private final CheckedBoundary boundary = new CheckedBoundary();

    @Transactional
    public String handle() {
      try {
        boundary.write();
        return "written";
      } catch (BoundaryFailure e) {
        return "handled";
      }
    }
  }

  /** The #8102 shape: the boundary is reached through a bean that is not transactional itself. */
  public static class CatchingCallerThroughPassThrough {

    private final PassThrough passThrough = new PassThrough();

    @Transactional
    public String handle() {
      try {
        passThrough.forward();
        return "written";
      } catch (BoundaryFailure e) {
        return "handled";
      }
    }
  }

  /** Safe: no transaction is open here, so the boundary's own transaction rolls back alone. */
  public static class UntransactionalCaller {

    private final CheckedBoundary boundary = new CheckedBoundary();

    public String handle() {
      try {
        boundary.write();
        return "written";
      } catch (BoundaryFailure e) {
        return "handled";
      }
    }
  }

  /** Safe: REQUIRES_NEW, so the failure cannot reach the caller's transaction. */
  public static class IsolatedBoundaryCaller {

    private final CheckedBoundary boundary = new CheckedBoundary();

    @Transactional
    public String handle() {
      try {
        boundary.writeIsolated();
        return "written";
      } catch (BoundaryFailure e) {
        return "handled";
      }
    }
  }

  /** Safe: the inner boundary declares noRollbackFor for exactly what the caller handles. */
  public static class HandledFailureCaller {

    private final CheckedBoundary boundary = new CheckedBoundary();

    @Transactional
    public String handle() {
      try {
        boundary.writeHandledFailure();
        return "written";
      } catch (BoundaryFailure e) {
        return "handled";
      }
    }
  }

  /** Safe: Spring does not roll back on a checked exception unless asked to. */
  public static class DefaultRulesCaller {

    private final DefaultRulesBoundary boundary = new DefaultRulesBoundary();

    @Transactional
    public String handle() {
      try {
        boundary.write();
        return "written";
      } catch (BoundaryFailure e) {
        return "handled";
      }
    }
  }

  /** Default rollback rules: unchecked only. */
  public static class DefaultRulesBoundary {

    @Transactional
    public void write() throws BoundaryFailure {
      throw new BoundaryFailure();
    }
  }

  /** Checked, so it only rolls back a boundary that asked for it. */
  public static class BoundaryFailure extends Exception {}
}
