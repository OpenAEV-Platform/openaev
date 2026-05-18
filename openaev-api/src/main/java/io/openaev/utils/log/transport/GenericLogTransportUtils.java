package io.openaev.utils.log.transport;

public interface GenericLogTransportUtils {
    boolean isEnabled();
    boolean send(String message, Object level);
}
