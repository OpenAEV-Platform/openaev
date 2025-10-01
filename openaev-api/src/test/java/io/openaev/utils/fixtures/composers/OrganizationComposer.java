package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.OrganizationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrganizationComposer extends ComposerBase<Organization> {
  @Autowired OrganizationRepository organizationRepository;

  public class Composer extends InnerComposerBase<Organization> {
    private final Organization organization;
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();

    public Composer(Organization organization) {
      this.organization = organization;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.organization.getTags();
      tempTags.add(tagComposer.get());
      this.organization.setTags(tempTags);
      return this;
    }

    public Composer withId(String id) {
      this.organization.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      this.tagComposers.forEach(TagComposer.Composer::persist);
      organizationRepository.save(organization);
      return this;
    }

    @Override
    public Composer delete() {
      organizationRepository.delete(organization);
      this.tagComposers.forEach(TagComposer.Composer::delete);
      return this;
    }

    @Override
    public Organization get() {
      return this.organization;
    }
  }

  public Composer forOrganization(Organization organization) {
    generatedItems.add(organization);
    return new Composer(organization);
  }
}
