package io.openaev.secrets.service;

import io.openaev.secrets.provider.SecretProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretService {
  private final List<SecretProvider> backends;
}
