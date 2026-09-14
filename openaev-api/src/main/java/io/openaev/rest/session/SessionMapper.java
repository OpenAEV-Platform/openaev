package io.openaev.rest.session;

import io.openaev.config.SessionManager.SessionInfo;
import io.openaev.database.raw.RawUserIdentity;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.session.response.SessionOutput;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionMapper {

  private final UserRepository userRepository;

  /** Resolves the owners of the whole listing in a single scalar query, never one query per row. */
  public List<SessionOutput> toSessionOutputs(final List<SessionInfo> sessions) {
    List<String> userIds = sessions.stream().map(SessionInfo::userId).distinct().toList();
    if (userIds.isEmpty()) {
      return List.of();
    }
    Map<String, RawUserIdentity> owners =
        userRepository.rawIdentities(userIds).stream()
            .collect(Collectors.toMap(RawUserIdentity::getUser_id, Function.identity()));
    return sessions.stream()
        .map(session -> SessionOutput.from(session, owners.get(session.userId())))
        .toList();
  }
}
