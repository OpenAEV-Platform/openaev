package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Stack reduction for the write-attribution detector: which stacks are a Hibernate flush, and how a
 * flush-time write that nobody captured is keyed so the gate reports it instead of waiving it.
 */
@DisplayName("Write-attribution stack reduction")
class WriteAttrStackTest {

  private static StackTraceElement frame(String className, String method, int line) {
    return new StackTraceElement(className, method, className + ".java", line);
  }

  /** A deferred write flushed by a test's native read: JUnit, the test, Hibernate, JDBC. */
  private static StackTraceElement[] flushFromTestFrame() {
    return new StackTraceElement[] {
      frame("io.openaev.config.WriteAttrDetectorListener", "afterQuery", 80),
      frame("net.ttddyy.dsproxy.proxy.StatementProxyLogic", "invoke", 200),
      frame("org.hibernate.engine.jdbc.internal.ResultSetReturnImpl", "executeUpdate", 200),
      frame("org.hibernate.persister.entity.mutation.InsertCoordinatorStandard", "insert", 100),
      frame("org.hibernate.action.internal.EntityInsertAction", "execute", 100),
      frame("org.hibernate.engine.spi.ActionQueue", "executeActions", 600),
      frame("org.hibernate.event.internal.AbstractFlushingEventListener", "performExecutions", 300),
      frame("org.hibernate.event.internal.DefaultAutoFlushEventListener", "onAutoFlush", 70),
      frame("org.hibernate.internal.SessionImpl", "autoFlushIfRequired", 1300),
      frame("io.openaev.rest.scenario.ScenarioApiTest$Update", "given_x_should_y", 88),
      frame("java.base/jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", 100),
      frame("org.junit.platform.commons.util.ReflectionUtils", "invokeMethod", 700),
    };
  }

  /** A synchronous write the test issued itself: no flush frame anywhere. */
  private static StackTraceElement[] nativeWriteFromTestFrame() {
    return new StackTraceElement[] {
      frame("io.openaev.config.WriteAttrDetectorListener", "afterQuery", 80),
      frame("net.ttddyy.dsproxy.proxy.StatementProxyLogic", "invoke", 200),
      frame("org.hibernate.engine.jdbc.internal.ResultSetReturnImpl", "executeUpdate", 200),
      frame("org.hibernate.query.sql.internal.NativeQueryImpl", "executeUpdate", 900),
      frame("io.openaev.config.WriteAttrDetectorNearMissTest", "nativeInsertIsFlagged", 116),
      frame("org.junit.platform.commons.util.ReflectionUtils", "invokeMethod", 700),
    };
  }

  @Nested
  @DisplayName("Flush detection")
  class FlushDetection {

    @Test
    @DisplayName("Given a write executed by the Hibernate flush, should be a flush stack")
    void given_flushExecution_should_beFlushStack() {
      assertTrue(WriteAttrStack.isFlushStack(flushFromTestFrame()));
    }

    @Test
    @DisplayName("Given a native write issued directly, should not be a flush stack")
    void given_synchronousNativeWrite_should_notBeFlushStack() {
      assertFalse(WriteAttrStack.isFlushStack(nativeWriteFromTestFrame()));
    }
  }

  @Nested
  @DisplayName("Unattributed key")
  class UnattributedKey {

    @Test
    @DisplayName("Given a flush from a test frame, should key on the test that flushed it")
    void given_flushFromTestFrame_should_keyOnTheFlushingTest() {
      // Arrange
      StackTraceElement[] stack = flushFromTestFrame();

      // Act
      String key = WriteAttrStack.unattributed(stack);

      // Assert
      assertNull(
          WriteAttrStack.entryFrame(stack), "precondition: no production frame on the stack");
      assertEquals(
          "unattributed(io.openaev.rest.scenario.ScenarioApiTest$Update.given_x_should_y)", key);
    }

    @Test
    @DisplayName("Given a synchronous test write, should stay a test-driven write with no key")
    void given_synchronousTestWrite_should_haveNoKey() {
      assertNull(WriteAttrStack.unattributed(nativeWriteFromTestFrame()));
    }

    @Test
    @DisplayName("Given no test frame at all, should still name the flush as unattributed")
    void given_noTestFrame_should_nameUnknown() {
      StackTraceElement[] stack = {
        frame(
            "org.hibernate.event.internal.AbstractFlushingEventListener", "performExecutions", 300),
        frame("org.springframework.orm.jpa.JpaTransactionManager", "doCommit", 500),
      };
      assertEquals("unattributed(unknown)", WriteAttrStack.unattributed(stack));
    }
  }

  @Nested
  @DisplayName("Outermost test frame")
  class OutermostTestFrame {

    @Test
    @DisplayName("Given a test method calling a fixture, should name the test method")
    void given_testCallingFixture_should_nameTheTestMethod() {
      StackTraceElement[] stack = {
        frame("io.openaev.utils.fixtures.composers.ScenarioComposer", "persist", 40),
        frame("io.openaev.rest.scenario.ScenarioApiTest", "given_a_should_b", 60),
      };
      assertEquals(
          "io.openaev.rest.scenario.ScenarioApiTest.given_a_should_b",
          WriteAttrStack.outermostTestFrame(stack));
    }
  }
}
