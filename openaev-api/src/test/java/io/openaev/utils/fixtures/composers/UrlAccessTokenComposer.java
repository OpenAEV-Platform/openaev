package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.UrlAccessToken;
import io.openaev.database.repository.UrlAccessTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UrlAccessTokenComposer extends ComposerBase<UrlAccessToken> {

  @Autowired private UrlAccessTokenRepository urlAccessTokenRepository;

  public class Composer extends InnerComposerBase<UrlAccessToken> {
    private final UrlAccessToken urlAccessToken;

    public Composer(UrlAccessToken urlAccessToken) {
      this.urlAccessToken = urlAccessToken;
    }

    @Override
    public Composer persist() {
      urlAccessTokenRepository.save(urlAccessToken);
      return this;
    }

    @Override
    public Composer delete() {
      urlAccessTokenRepository.delete(urlAccessToken);
      return this;
    }

    @Override
    public UrlAccessToken get() {
      return this.urlAccessToken;
    }
  }

  public Composer forToken(UrlAccessToken urlAccessToken) {
    generatedItems.add(urlAccessToken);
    return new Composer(urlAccessToken);
  }
}

