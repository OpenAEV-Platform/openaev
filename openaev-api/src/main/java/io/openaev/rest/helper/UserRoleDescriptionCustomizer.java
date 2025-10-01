package io.openaev.rest.helper;

import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.User;
import io.swagger.v3.oas.models.Operation;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class UserRoleDescriptionCustomizer implements OperationCustomizer {
  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    UserRoleDescription annotation;
    // We check if we have the annotation on the method as it will take priority on the class
    if (handlerMethod.getMethodAnnotation(UserRoleDescription.class) != null) {
      annotation = handlerMethod.getMethodAnnotation(UserRoleDescription.class);
    } else {
      // If not, we check if we have the annotation on the class
      annotation = handlerMethod.getBeanType().getAnnotation(UserRoleDescription.class);
    }
    // If we found any annotation UserRoleDescription
    if (annotation != null) {
      // We get the Secured annotation
      var securedAnnotation = handlerMethod.getMethodAnnotation(Secured.class);
      String description =
          operation.getDescription() == null ? "" : (operation.getDescription() + "<br>");
      // If we found a secured annotation
      if (securedAnnotation != null) {
        // We add the required role in the description
        description +=
            "**Required role :** ***"
                + String.join("*** or ***", securedAnnotation.value())
                + "***";
      } else if (annotation.needAuthenticated()) {
        // If there are no secured annotation and we need to be authenticated
        // Then we add all the existing role in the description
        description +=
            "**Required role :** ***" + String.join("*** or ***", User.ALL_ROLES) + "***";
      } else {
        // If no secured annotation and we don't need authentication, then we show that no roles are
        // required
        description += "**Required role :** none";
      }

      operation.setDescription(description + getSpecialRequirements(handlerMethod));
    }
    return operation;
  }

  private String getSpecialRequirements(HandlerMethod handlerMethod) {
    String specialRequirement = StringUtils.EMPTY;
    var preAuthorizeAnnotation = handlerMethod.getMethodAnnotation(PreAuthorize.class);
    if (preAuthorizeAnnotation != null) {
      specialRequirement += "<br>**Special Requirement :** ";
      String preAuthorize = preAuthorizeAnnotation.value();
      if (preAuthorize.startsWith("isExercisePlanner")
          || preAuthorize.startsWith("isSimulationPlanner")) {
        specialRequirement +=
            "You need to be an admin or a planner of the simulation to call this endpoint";
      } else if (preAuthorize.startsWith("isExerciseObserver")) {
        specialRequirement +=
            "You need to be an admin or an observer of the simulation to call this endpoint";
      } else if (preAuthorize.startsWith("isExercisePlayer")) {
        specialRequirement +=
            "You need to be an admin or a player of the simulation to call this endpoint";
      } else if (preAuthorize.startsWith("isExerciseObserverOrPlayer")) {
        specialRequirement +=
            "You need to be an admin, an observer or a player of the simulation to call this endpoint";
      } else if (preAuthorize.startsWith("isScenarioPlanner")) {
        specialRequirement +=
            "You need to be an admin or a planner of the scenario to call this endpoint";
      } else if (preAuthorize.startsWith("isScenarioObserver")) {
        specialRequirement +=
            "You need to be an admin or an observer of the scenario to call this endpoint";
      } else if (preAuthorize.startsWith("isPlanner")) {
        specialRequirement += "You need to be an admin or a planner to call this endpoint";
      } else if (preAuthorize.startsWith("isObserver")) {
        specialRequirement += "You need to be an admin or an observer to call this endpoint";
      } else if (preAuthorize.startsWith("isPlayer")) {
        specialRequirement += "You need to be a player to call this endpoint";
      }
    }
    return specialRequirement;
  }
}
