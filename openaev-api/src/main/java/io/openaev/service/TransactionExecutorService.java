package io.openaev.service;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TransactionExecutorService {

  private final PlatformTransactionManager transactionManager;

  public void runInNewTransaction(Runnable action) {
    supplyInNewTransaction(
        () -> {
          action.run();
          return null;
        });
  }

  public <T> T supplyInNewTransaction(Supplier<T> supplier) {
    TransactionTemplate shortWriteTx = new TransactionTemplate(transactionManager);
    shortWriteTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return shortWriteTx.execute(status -> supplier.get());
  }
}

