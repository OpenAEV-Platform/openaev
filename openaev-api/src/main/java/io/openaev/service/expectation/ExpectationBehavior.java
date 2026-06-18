package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import java.util.List;

public interface ExpectationBehavior {

  boolean supports(BaseInjectExpectation expectation);

  void applyResultToLeaves(BaseInjectExpectation expectation, ExpectationUpdateInput input);

  void initializeResults(BaseInjectExpectation expectation);

  List<BaseInjectExpectation> propagate(BaseInjectExpectation expectation);
}
