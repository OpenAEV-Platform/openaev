package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.*;
import io.openaev.database.model.Article;
import io.openaev.database.repository.*;
import io.openaev.rest.exercise.service.ExerciseService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExerciseComposer extends ComposerBase<Exercise> {
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager entityManager;

  public class Composer extends InnerComposerBase<Exercise> {
    private final Exercise exercise;
    private final List<InjectComposer.Composer> injectComposers = new ArrayList<>();
    private final List<LessonsCategoryComposer.Composer> categoryComposers = new ArrayList<>();
    private final List<TeamComposer.Composer> teamComposers = new ArrayList<>();
    private final List<ArticleComposer.Composer> articleComposers = new ArrayList<>();
    private final List<ObjectiveComposer.Composer> objectiveComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<DocumentComposer.Composer> documentComposers = new ArrayList<>();
    private final List<VariableComposer.Composer> variableComposers = new ArrayList<>();
    private final List<PauseComposer.Composer> pauseComposers = new ArrayList<>();
    private Optional<SecurityCoverageComposer.Composer> securityCoverageComposer = Optional.empty();
    private Optional<SecurityCoverageSendJobComposer.Composer> securityCoverageSendJobComposer =
        Optional.empty();

    public Composer(Exercise exercise) {
      this.exercise = exercise;
    }

    public Composer withVariable(VariableComposer.Composer variableComposer) {
      variableComposers.add(variableComposer);
      List<Variable> variables = exercise.getVariables();
      variables.add(variableComposer.get());
      variableComposer.get().setExercise(exercise);
      this.exercise.setVariables(variables);
      return this;
    }

    public Composer withSecurityCoverage(
        SecurityCoverageComposer.Composer securityCoverageWrapper) {
      securityCoverageComposer = Optional.of(securityCoverageWrapper);
      this.exercise.setSecurityCoverage(securityCoverageWrapper.get());
      return this;
    }

    public Composer withInjects(List<InjectComposer.Composer> injectComposers) {
      injectComposers.forEach(this::withInject);
      return this;
    }

    public Composer withSecurityCoverageSendJob(
        SecurityCoverageSendJobComposer.Composer securityCoverageSendJobWrapper) {
      this.securityCoverageSendJobComposer = Optional.of(securityCoverageSendJobWrapper);
      securityCoverageSendJobWrapper.get().setSimulation(this.exercise);
      return this;
    }

    public Composer withInject(InjectComposer.Composer injectComposer) {
      injectComposers.add(injectComposer);
      List<Inject> injects = exercise.getInjects();
      injectComposer.get().setExercise(exercise);
      injects.add(injectComposer.get());
      this.exercise.setInjects(injects);
      return this;
    }

    public Composer withLessonCategory(LessonsCategoryComposer.Composer categoryComposer) {
      this.categoryComposers.add(categoryComposer);
      List<LessonsCategory> tempCategories = this.exercise.getLessonsCategories();
      tempCategories.add(categoryComposer.get());
      this.exercise.setLessonsCategories(tempCategories);
      return this;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposers.add(teamComposer);
      List<Team> tempTeams = this.exercise.getTeams();
      tempTeams.add(teamComposer.get());
      this.exercise.setTeams(tempTeams);
      return this;
    }

    // special composition that injects the currently set users in currently set teams as
    // "ExerciseTeamUsers"
    public Composer withTeamUsers() {
      List<ExerciseTeamUser> tempTeamUsers = new ArrayList<>();
      this.exercise
          .getTeams()
          .forEach(
              team ->
                  team.getUsers()
                      .forEach(
                          user -> {
                            ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
                            exerciseTeamUser.setExercise(exercise);
                            exerciseTeamUser.setUser(user);
                            exerciseTeamUser.setTeam(team);
                            tempTeamUsers.add(exerciseTeamUser);
                          }));
      this.exercise.setTeamUsers(tempTeamUsers);
      return this;
    }

    public Composer withArticle(ArticleComposer.Composer articleComposer) {
      this.articleComposers.add(articleComposer);
      List<Article> tempArticles = this.exercise.getArticles();
      tempArticles.add(articleComposer.get());
      this.exercise.setArticles(tempArticles);
      return this;
    }

    public Composer withObjective(ObjectiveComposer.Composer objectiveComposer) {
      this.objectiveComposers.add(objectiveComposer);
      List<Objective> tempObjectives = this.exercise.getObjectives();
      tempObjectives.add(objectiveComposer.get());
      this.exercise.setObjectives(tempObjectives);
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      this.tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.exercise.getTags();
      tempTags.add(tagComposer.get());
      this.exercise.setTags(tempTags);
      return this;
    }

    public Composer withDocument(DocumentComposer.Composer documentComposer) {
      this.documentComposers.add(documentComposer);
      List<Document> tempDocuments = this.exercise.getDocuments();
      tempDocuments.add(documentComposer.get());
      this.exercise.setDocuments(tempDocuments);
      return this;
    }

    public Composer withPause(PauseComposer.Composer pauseComposer) {
      this.pauseComposers.add(pauseComposer);
      List<Pause> tempPauses = this.exercise.getPauses();
      tempPauses.add(pauseComposer.get());
      this.exercise.setPauses(tempPauses);
      return this;
    }

    public Composer withId(String id) {
      this.exercise.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      exerciseRepository.save(exercise);
      this.articleComposers.forEach(ArticleComposer.Composer::persist);
      this.categoryComposers.forEach(LessonsCategoryComposer.Composer::persist);
      this.teamComposers.forEach(TeamComposer.Composer::persist);
      this.injectComposers.forEach(InjectComposer.Composer::persist);
      this.objectiveComposers.forEach(ObjectiveComposer.Composer::persist);
      this.tagComposers.forEach(TagComposer.Composer::persist);
      this.documentComposers.forEach(DocumentComposer.Composer::persist);
      this.variableComposers.forEach(VariableComposer.Composer::persist);
      this.pauseComposers.forEach(PauseComposer.Composer::persist);
      this.securityCoverageComposer.ifPresent(SecurityCoverageComposer.Composer::persist);
      this.securityCoverageSendJobComposer.ifPresent(
          SecurityCoverageSendJobComposer.Composer::persist);
      exerciseService.createExercise(exercise);
      return this;
    }

    @Override
    public Composer delete() {
      exerciseRepository.delete(exercise);
      this.securityCoverageSendJobComposer.ifPresent(
          SecurityCoverageSendJobComposer.Composer::delete);
      this.securityCoverageComposer.ifPresent(SecurityCoverageComposer.Composer::delete);
      this.variableComposers.forEach(VariableComposer.Composer::delete);
      this.documentComposers.forEach(DocumentComposer.Composer::delete);
      this.tagComposers.forEach(TagComposer.Composer::delete);
      this.objectiveComposers.forEach(ObjectiveComposer.Composer::delete);
      this.injectComposers.forEach(InjectComposer.Composer::delete);
      this.teamComposers.forEach(TeamComposer.Composer::delete);
      this.categoryComposers.forEach(LessonsCategoryComposer.Composer::delete);
      this.articleComposers.forEach(ArticleComposer.Composer::delete);
      this.pauseComposers.forEach(PauseComposer.Composer::delete);
      return this;
    }

    @Override
    public Exercise get() {
      return this.exercise;
    }
  }

  public Composer forExercise(Exercise exercise) {
    generatedItems.add(exercise);
    return new Composer(exercise);
  }
}
