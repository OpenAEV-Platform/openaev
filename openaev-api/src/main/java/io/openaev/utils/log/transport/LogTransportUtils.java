package io.openaev.utils.log.transport;

public interface LogTransportUtils {
    boolean isEnabled();
    boolean send(String message, Object level);
}
