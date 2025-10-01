package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.TeamRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TeamComposer extends ComposerBase<Team> {
  @Autowired private TeamRepository teamRepository;

  public class Composer extends InnerComposerBase<Team> {
    private final Team team;
    private final List<UserComposer.Composer> userComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private Optional<OrganizationComposer.Composer> organisationComposer = Optional.empty();

    public Composer(Team team) {
      this.team = team;
    }

    public Composer withUser(UserComposer.Composer userComposer) {
      userComposers.add(userComposer);
      List<User> tempUsers = this.team.getUsers();
      tempUsers.add(userComposer.get());
      this.team.setUsers(tempUsers);
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.team.getTags();
      tempTags.add(tagComposer.get());
      this.team.setTags(tempTags);
      return this;
    }

    public Composer withOrganisation(OrganizationComposer.Composer newOrganisationComposer) {
      organisationComposer = Optional.of(newOrganisationComposer);
      team.setOrganization(newOrganisationComposer.get());
      return this;
    }

    public Composer withId(String id) {
      this.team.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      organisationComposer.ifPresent(OrganizationComposer.Composer::persist);
      userComposers.forEach(UserComposer.Composer::persist);
      tagComposers.forEach(TagComposer.Composer::persist);
      teamRepository.save(team);
      return this;
    }

    @Override
    public Composer delete() {
      teamRepository.delete(team);
      tagComposers.forEach(TagComposer.Composer::delete);
      userComposers.forEach(UserComposer.Composer::delete);
      organisationComposer.ifPresent(OrganizationComposer.Composer::delete);
      return this;
    }

    @Override
    public Team get() {
      return this.team;
    }
  }

  public Composer forTeam(Team team) {
    generatedItems.add(team);
    return new Composer(team);
  }
}
