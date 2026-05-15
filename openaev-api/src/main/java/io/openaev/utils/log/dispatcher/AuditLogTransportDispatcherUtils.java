package io.openaev.utils.log.dispatcher;

import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.transport.AuditLogTransportUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class AuditLogTransportDispatcherUtils {
    private final List<AuditLogTransportUtils> beans;

    public boolean dispatch(LogEvent event, Object level) {
        List<CompletableFuture<Boolean>> futures = beans.stream()
                .filter(AuditLogTransportUtils::isEnabled)
                .map(bean -> bean.send(event, level))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .join();

        return futures.stream()
                .allMatch(CompletableFuture::join); //only return true if all results from all send methods are true.
    }

    public boolean dispatch(String message, Object level) {
        List<CompletableFuture<Boolean>> futures = beans.stream()
                .filter(AuditLogTransportUtils::isEnabled)
                .map(bean -> bean.send(message, level))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .allMatch(CompletableFuture::join);//only return true if all results from all send methods are true.
    }
}
