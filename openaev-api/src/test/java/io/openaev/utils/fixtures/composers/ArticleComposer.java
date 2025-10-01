package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Article;
import io.openaev.database.model.Document;
import io.openaev.database.repository.ArticleRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ArticleComposer extends ComposerBase<Article> {
  @Autowired private ArticleRepository articleRepository;

  public class Composer extends InnerComposerBase<Article> {
    private final Article article;
    private ChannelComposer.Composer channelComposer;
    private List<DocumentComposer.Composer> documentComposers = new ArrayList<>();

    public Composer(Article article) {
      this.article = article;
    }

    public Composer withChannel(ChannelComposer.Composer channelComposer) {
      this.channelComposer = channelComposer;
      this.article.setChannel(channelComposer.get());
      return this;
    }

    public Composer withDocument(DocumentComposer.Composer documentComposer) {
      documentComposers.add(documentComposer);
      List<Document> tempDocs = article.getDocuments();
      tempDocs.add(documentComposer.get());
      article.setDocuments(tempDocs);
      return this;
    }

    public Composer withId(String id) {
      this.article.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      this.channelComposer.persist();
      documentComposers.forEach(DocumentComposer.Composer::persist);
      articleRepository.save(article);
      return this;
    }

    @Override
    public Composer delete() {
      articleRepository.delete(article);
      documentComposers.forEach(DocumentComposer.Composer::delete);
      this.channelComposer.delete();
      return null;
    }

    @Override
    public Article get() {
      return article;
    }
  }

  public Composer forArticle(Article article) {
    generatedItems.add(article);
    return new Composer(article);
  }
}
