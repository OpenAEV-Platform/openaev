package io.openaev.utils.fixtures.composers;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserComposer extends ComposerBase<User> {
  @Autowired UserRepository userRepository;

  public class Composer extends InnerComposerBase<User> {
    private final User user;
    private OrganizationComposer.Composer organizationComposer;
    private Organization organization;
    private String organizationTenantId;
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<InnerComposerBase<Group>> groupComposers = new ArrayList<>();
    private final List<TokenComposer.Composer> tokenComposers = new ArrayList<>();

    public Composer(User user) {
      this.user = user;
    }

    public Composer withToken(TokenComposer.Composer tokenComposer) {
      this.tokenComposers.add(tokenComposer);
      List<Token> tempTokens = this.user.getTokens();
      tokenComposer.get().setUser(this.user);
      tempTokens.add(tokenComposer.get());
      this.user.setTokens(tempTokens);
      return this;
    }

    public Composer withOrganization(OrganizationComposer.Composer organizationComposer) {
      this.organizationComposer = organizationComposer;
      return withOrganization(organizationComposer.get());
    }

    /**
     * Links the user to an organization, in the tenant of that organization or, for an organization
     * not yet persisted and so without tenant, in the current one (the tenant the persist listener
     * gives it). The organization is never modified.
     */
    public Composer withOrganization(Organization organization) {
      this.organization = organization;
      this.organizationTenantId =
          Optional.ofNullable(organization.getTenant())
              .map(Tenant::getId)
              .orElseGet(TenantContext::getCurrentTenant);
      this.user.setOrganizationForTenant(this.organizationTenantId, organization);
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      this.user.getTags().add(tagComposer.get());
      return this;
    }

    public Composer withGroup(InnerComposerBase<Group> groupComposer) {
      groupComposers.add(groupComposer);
      this.user.getUnscopedGroups().add(groupComposer.get());
      return this;
    }

    public Composer withId(String id) {
      this.user.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      this.tagComposers.forEach(TagComposer.Composer::persist);
      if (organizationComposer != null) {
        organizationComposer.persist();
      }
      this.groupComposers.forEach(InnerComposerBase::persist);
      userRepository.save(user);
      if (organization != null) {
        userRepository.assignOrganization(user, organizationTenantId, organization);
      }
      this.tokenComposers.forEach(TokenComposer.Composer::persist);
      return this;
    }

    @Override
    public Composer delete() {
      this.tokenComposers.forEach(TokenComposer.Composer::delete);
      userRepository.delete(user);
      if (organizationComposer != null) {
        organizationComposer.delete();
      }
      this.tagComposers.forEach(TagComposer.Composer::delete);
      this.groupComposers.forEach(InnerComposerBase::delete);
      return this;
    }

    @Override
    public User get() {
      return this.user;
    }
  }

  public Composer forUser(User user) {
    generatedItems.add(user);
    return new Composer(user);
  }
}
