package io.openbas.service;

import static io.openbas.utils.constants.Constants.ARTICLES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.Article;
import io.openbas.database.model.Inject;
import io.openbas.database.repository.ArticleRepository;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleService {

  private final ArticleRepository articleRepository;
  private final ObjectMapper objectMapper;

  public List<Article> getInjectsArticles(List<Inject> injects) throws IOException {
    Set<String> uniqueArticleIds = new HashSet<>();
    for (Inject inject : injects) {
      if (inject.getContent() == null || !inject.getContent().has(ARTICLES)) {
        continue;
      }
      uniqueArticleIds.addAll(
          objectMapper.readValue(
              inject.getContent().get(ARTICLES).traverse(), new TypeReference<List<String>>() {}));
    }
    return StreamSupport.stream(
            articleRepository.findAllById(uniqueArticleIds).spliterator(), false)
        .toList();
  }
}
