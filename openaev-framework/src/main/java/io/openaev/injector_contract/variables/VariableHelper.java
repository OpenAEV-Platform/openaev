package io.openaev.injector_contract.variables;

import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractCardinality.One;
import static io.openaev.injector_contract.ContractVariable.variable;

import io.openaev.database.model.Variable.VariableType;
import io.openaev.injector_contract.ContractVariable;
import java.util.List;

/**
 * Helper class providing predefined contract variables for injection templates.
 *
 * <p>This utility class defines standard variables that can be used in injection contracts,
 * including user information, exercise metadata, team data, and platform URIs.
 *
 * <p>Variables defined here are automatically available in all injection contracts and can be
 * referenced in templates using the FreeMarker variable syntax (e.g., {@code ${user.email}}).
 *
 * <p>Available variable groups:
 *
 * <ul>
 *   <li>{@link #userVariable} - Target user information (id, email, name, language)
 *   <li>{@link #exerciseVariable} - Current exercise/scenario details
 *   <li>{@link #teamVariable} - List of participating team names
 *   <li>{@link #uriVariables} - Platform interface URLs
 * </ul>
 *
 * @see ContractVariable
 * @see Contract
 */
public final class VariableHelper {

  private VariableHelper() {
    // Utility class - prevent instantiation
  }

  // Variable key constants
  /** Variable key for user information */
  public static final String USER = "user";

  /** Variable key for exercise/scenario information */
  public static final String EXERCISE = "exercise";

  /** Variable key for team names list */
  public static final String TEAMS = "teams";

  /** Variable key for communication check information */
  public static final String COMCHECK = "comcheck";

  /** Variable key for player interface URI */
  public static final String PLAYER_URI = "player_uri";

  /** Variable key for challenges interface URI */
  public static final String CHALLENGES_URI = "challenges_uri";

  /** Variable key for scoreboard interface URI */
  public static final String SCOREBOARD_URI = "scoreboard_uri";

  /** Variable key for lessons learned interface URI */
  public static final String LESSONS_URI = "lessons_uri";

  // Predefined contract variables

  /**
   * User variable containing information about the target user of the injection.
   *
   * <p>Child variables:
   *
   * <ul>
   *   <li>{@code user.id} - Platform identifier of the user
   *   <li>{@code user.email} - Email address of the user
   *   <li>{@code user.firstname} - First name of the user
   *   <li>{@code user.lastname} - Last name of the user
   *   <li>{@code user.lang} - Preferred language of the user
   * </ul>
   */
  public static final ContractVariable userVariable =
      variable(
          USER,
          "User that will receive the injection",
          VariableType.String,
          One,
          List.of(
              variable(USER + ".id", "Id of the user in the platform", VariableType.String, One),
              variable(USER + ".email", "Email of the user", VariableType.String, One),
              variable(USER + ".firstname", "First name of the user", VariableType.String, One),
              variable(USER + ".lastname", "Last name of the user", VariableType.String, One),
              variable(USER + ".lang", "Language of the user", VariableType.String, One)));

  /**
   * Exercise variable containing information about the current exercise or scenario.
   *
   * <p>Child variables:
   *
   * <ul>
   *   <li>{@code exercise.id} - Platform identifier of the exercise
   *   <li>{@code exercise.name} - Display name of the exercise
   *   <li>{@code exercise.description} - Description text of the exercise
   * </ul>
   */
  public static final ContractVariable exerciseVariable =
      variable(
          EXERCISE,
          "Exercise of the current injection",
          VariableType.Object,
          One,
          List.of(
              variable(
                  EXERCISE + ".id", "Id of the exercise in the platform", VariableType.String, One),
              variable(EXERCISE + ".name", "Name of the exercise", VariableType.String, One),
              variable(
                  EXERCISE + ".description",
                  "Description of the exercise",
                  VariableType.String,
                  One)));

  /** Team variable containing the list of team names participating in the injection. */
  public static final ContractVariable teamVariable =
      variable(TEAMS, "List of team names for the injection", VariableType.String, Multiple);

  /**
   * URI variables providing links to various platform interfaces.
   *
   * <p>Includes:
   *
   * <ul>
   *   <li>{@code player_uri} - Link to the player interface
   *   <li>{@code challenges_uri} - Link to the challenges interface
   *   <li>{@code scoreboard_uri} - Link to the scoreboard interface
   *   <li>{@code lessons_uri} - Link to the lessons learned interface
   * </ul>
   */
  public static final List<ContractVariable> uriVariables =
      List.of(
          variable(PLAYER_URI, "Player interface platform link", VariableType.String, One),
          variable(CHALLENGES_URI, "Challenges interface platform link", VariableType.String, One),
          variable(SCOREBOARD_URI, "Scoreboard interface platform link", VariableType.String, One),
          variable(
              LESSONS_URI, "Lessons learned interface platform link", VariableType.String, One));
}
