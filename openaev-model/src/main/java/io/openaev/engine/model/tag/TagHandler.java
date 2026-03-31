package io.openaev.engine.model.tag;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawTagIndexing;
import io.openaev.database.repository.TagRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TagHandler implements Handler<EsTag> {

  private TagRepository tagRepository;

  @Autowired
  public void setTagRepository(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  @Override
  public List<EsTag> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawTagIndexing> forIndexing = tagRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            tag -> {
              EsTag esTag = new EsTag();
              // Base
              esTag.setBase_id(tag.getTag_id());
              esTag.setBase_representative(tag.getTag_name());
              esTag.setBase_created_at(tag.getTag_created_at());
              esTag.setBase_updated_at(tag.getTag_updated_at());
              esTag.setBase_tenant_side(tag.getTenant_id());
              // not sure what to put here, if anything
              esTag.setBase_restrictions(buildRestrictions(tag.getTag_id()));

              esTag.setTag_color(tag.getTag_color());
              return esTag;
            })
        .toList();
  }
}
