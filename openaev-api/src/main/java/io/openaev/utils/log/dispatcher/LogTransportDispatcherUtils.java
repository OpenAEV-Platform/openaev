package io.openaev.utils.log.dispatcher;

import io.openaev.utils.log.transport.LogTransportUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LogTransportDispatcherUtils {
    private final List<LogTransportUtils> beans;

    public boolean dispatch(String message, Object level) {
        boolean status = true;

        for (LogTransportUtils bean : beans) {
            if (bean.isEnabled() && !bean.send(message, level)) {
                status = false;
            }
        }

        return status;
    }
}
