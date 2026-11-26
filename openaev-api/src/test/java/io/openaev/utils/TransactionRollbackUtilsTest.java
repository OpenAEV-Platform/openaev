package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TransactionRollbackUtils")
class TransactionRollbackUtilsTest {

  @Test
  @DisplayName("given_noActiveTransaction_should_notBeRollbackOnly")
  void given_noActiveTransaction_should_notBeRollbackOnly() {
    // -- ACT & ASSERT --
    assertThat(TransactionRollbackUtils.isCurrentTransactionRollbackOnly()).isFalse();
  }
}
