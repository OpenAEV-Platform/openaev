package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.*;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.service.scenario.ScenarioService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScenarioComposer extends ComposerBase<Scenario> {
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ScenarioService scenarioService;

  public class Composer extends InnerComposerBase<Scenario> {
    private final Scenario scenario;
    private final List<InjectComposer.Composer> injectComposers = new ArrayList<>();
    private final List<ExerciseComposer.Composer> simulationComposers = new ArrayList<>();
    private final List<ArticleComposer.Composer> articleComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<VariableComposer.Composer> variableComposers = new ArrayList<>();

    public Composer(Scenario scenario) {
      this.scenario = scenario;
    }

    public Composer withInjects(List<InjectComposer.Composer> injectComposers) {
      injectComposers.forEach(this::withInject);
      return this;
    }

    public ScenarioComposer.Composer withTag(TagComposer.Composer tagComposer) {
      this.tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.scenario.getTags();
      tempTags.add(tagComposer.get());
      this.scenario.setTags(tempTags);
      return this;
    }

    public Composer withInject(InjectComposer.Composer injectComposer) {
      injectComposers.add(injectComposer);
      Set<Inject> tempInjects = new HashSet<>(this.scenario.getInjects());
      injectComposer.get().setScenario(scenario);
      tempInjects.add(injectComposer.get());
      this.scenario.setInjects(tempInjects);
      return this;
    }

    public Composer withSimulation(ExerciseComposer.Composer simulationComposer) {
      simulationComposers.add(simulationComposer);
      List<Exercise> simulations = this.scenario.getExercises();
      simulations.add(simulationComposer.get());
      this.scenario.setExercises(simulations);
      return this;
    }

    public Composer withVariable(VariableComposer.Composer variableComposer) {
      variableComposers.add(variableComposer);
      List<Variable> tempVariables = this.scenario.getVariables();
      tempVariables.add(variableComposer.get());
      variableComposer.get().setScenario(scenario);
      scenario.setVariables(tempVariables);
      return this;
    }

    public Composer withArticle(ArticleComposer.Composer articleComposer) {
      articleComposers.add(articleComposer);
      List<Article> tempArticles = new ArrayList<>(this.scenario.getArticles());
      articleComposer.get().setScenario(scenario);
      tempArticles.add(articleComposer.get());
      this.scenario.setArticles(tempArticles);
      return this;
    }

    @Override
    public Composer persist() {
      articleComposers.forEach(ArticleComposer.Composer::persist);
      simulationComposers.forEach(ExerciseComposer.Composer::persist);
      scenarioRepository.save(scenario);
      tagComposers.forEach(TagComposer.Composer::persist);
      injectComposers.forEach(InjectComposer.Composer::persist);
      variableComposers.forEach(VariableComposer.Composer::persist);
      scenarioService.createScenario(scenario);
      return this;
    }

    @Override
    public Composer delete() {
      articleComposers.forEach(ArticleComposer.Composer::delete);
      injectComposers.forEach(InjectComposer.Composer::delete);
      tagComposers.forEach(TagComposer.Composer::delete);
      simulationComposers.forEach(ExerciseComposer.Composer::delete);
      variableComposers.forEach(VariableComposer.Composer::delete);
      scenarioRepository.delete(scenario);
      return this;
    }

    @Override
    public Scenario get() {
      return this.scenario;
    }
  }

  public Composer forScenario(Scenario scenario) {
    this.generatedItems.add(scenario);
    return new Composer(scenario);
  }
}
