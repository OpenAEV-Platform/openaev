package io.openaev.service;

import static io.openaev.config.SessionHelper.currentUser;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.scenario.response.ImportMessage;
import io.openaev.rest.scenario.response.ImportPostSummary;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.service.utils.InjectImportUtils;
import io.openaev.utils.InjectUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectImportService {

  private final InjectRepository injectRepository;
  private final ScenarioTeamUserRepository scenarioTeamUserRepository;
  private final ExerciseTeamUserRepository exerciseTeamUserRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;

  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final ImportService importService;

  private final List<String> importReservedField = List.of("description", "title", "trigger_time");
  final Pattern relativeDayPattern = Pattern.compile("^.*[DJ]([+\\-]?[0-9]*)(.*)$");
  final Pattern relativeHourPattern = Pattern.compile("^.*[HT]([+\\-]?[0-9]*).*$");
  final Pattern relativeMinutePattern = Pattern.compile("^.*[M]([+\\-]?[0-9]*).*$");

  final String pathSeparator = FileSystems.getDefault().getSeparator();

  final int FILE_STORAGE_DURATION = 60;

  /**
   * Store an xls file for ulterior import. The file will be deleted on exit.
   *
   * @param file
   * @return ImportPostSummary
   */
  public ImportPostSummary storeXlsFileForImport(MultipartFile file) {
    ImportPostSummary result = new ImportPostSummary();
    result.setAvailableSheets(new ArrayList<>());
    // Generating an UUID for identifying the file
    String fileID = UUID.randomUUID().toString();
    result.setImportId(fileID);
    try {
      // We're opening the file and listing the names of the sheets
      Workbook workbook = WorkbookFactory.create(file.getInputStream());
      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        result.getAvailableSheets().add(workbook.getSheetName(i));
      }
      // Writing the file in a temp dir
      Path tempDir = Files.createDirectory(Path.of(System.getProperty("java.io.tmpdir"), fileID));
      Path tempFile =
          Files.createTempFile(
              tempDir, null, "." + FilenameUtils.getExtension(file.getOriginalFilename()));
      Files.write(tempFile, file.getBytes());

      CompletableFuture.delayedExecutor(FILE_STORAGE_DURATION, TimeUnit.MINUTES)
          .execute(
              () -> {
                tempFile.toFile().delete();
                tempDir.toFile().delete();
              });

      // We're making sure the files are deleted when the backend restart
      tempDir.toFile().deleteOnExit();
      tempFile.toFile().deleteOnExit();
    } catch (Exception ex) {
      log.error("Error while importing an xls file", ex);
      throw new BadRequestException("File seems to be corrupt");
    }

    return result;
  }

  public ImportTestSummary importInjectIntoScenarioFromXLS(
      Scenario scenario,
      ImportMapper importMapper,
      String importId,
      String sheetName,
      int timezoneOffset,
      boolean saveAll) {
    return importInjectIntoFromXLS(
        scenario, null, importMapper, importId, sheetName, timezoneOffset, saveAll);
  }

  public ImportTestSummary importInjectIntoExerciseFromXLS(
      Exercise exercise,
      ImportMapper importMapper,
      String importId,
      String sheetName,
      int timezoneOffset,
      boolean saveAll) {
    return importInjectIntoFromXLS(
        null, exercise, importMapper, importId, sheetName, timezoneOffset, saveAll);
  }

  public ImportTestSummary importInjectIntoFromXLS(
      Scenario scenario,
      Exercise exercise,
      ImportMapper importMapper,
      String importId,
      String sheetName,
      int timezoneOffset,
      boolean saveAll) {
    // We call the inject service to get the injects to create as well as messages on how things
    // went
    ImportTestSummary importTestSummary =
        importXls(importId, scenario, exercise, importMapper, sheetName, timezoneOffset);
    Optional<ImportMessage> hasCritical =
        importTestSummary.getImportMessage().stream()
            .filter(
                importMessage ->
                    importMessage.getMessageLevel() == ImportMessage.MessageLevel.CRITICAL)
            .findAny();
    importTestSummary.setTotalNumberOfInjects(importTestSummary.getInjects().size());
    if (hasCritical.isPresent()) {
      // If there are critical errors, we do not save and we
      // empty the list of injects, we just keep the messages
      importTestSummary.setTotalNumberOfInjects(0);
      importTestSummary.setInjects(new ArrayList<>());
    } else if (importTestSummary.getInjects().isEmpty()
        && !importTestSummary.getImportMessage().isEmpty()) {
      return importTestSummary;
    } else if (saveAll) {
      importTestSummary.setInjects(
          importTestSummary.getInjects().stream()
              .map(
                  inject -> {
                    inject.setListened(false);
                    return inject;
                  })
              .toList());
      Iterable<Inject> newInjects = injectRepository.saveAll(importTestSummary.getInjects());
      if (exercise != null) {
        computeInjectInExercise(exercise, newInjects);
      } else if (scenario != null) {
        computeInjectInScenario(scenario, newInjects);
      } else {
        throw new IllegalArgumentException(
            "At least one of exercise or scenario should be present");
      }
      importTestSummary.setInjects(new ArrayList<>());
    } else {
      importTestSummary.setInjects(importTestSummary.getInjects().stream().limit(5).toList());
    }

    return importTestSummary;
  }

  private void computeInjectInExercise(
      @NotNull Exercise exercise, @NotNull Iterable<Inject> newInjects) {
    newInjects.forEach(
        inject -> {
          exercise.getInjects().add(inject);
          inject
              .getTeams()
              .forEach(
                  team -> {
                    if (!exercise.getTeams().contains(team)) {
                      exercise.getTeams().add(team);
                    }
                  });
          inject
              .getTeams()
              .forEach(
                  team ->
                      team.getUsers()
                          .forEach(
                              user -> {
                                if (!exercise.getTeamUsers().contains(user)) {
                                  ExerciseTeamUserId compositeId = new ExerciseTeamUserId();
                                  compositeId.setExerciseId(exercise.getId());
                                  compositeId.setTeamId(team.getId());
                                  compositeId.setUserId(user.getId());
                                  boolean exists =
                                      exerciseTeamUserRepository.findById(compositeId).isPresent();

                                  if (!exists) {
                                    ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
                                    exerciseTeamUser.setExercise(exercise);
                                    exerciseTeamUser.setTeam(team);
                                    exerciseTeamUser.setUser(user);
                                    exerciseTeamUser =
                                        exerciseTeamUserRepository.save(exerciseTeamUser);
                                    exercise.getTeamUsers().add(exerciseTeamUser);
                                  }
                                }
                              }));
        });
  }

  private void computeInjectInScenario(
      @NotNull Scenario scenario, @NotNull Iterable<Inject> newInjects) {
    newInjects.forEach(
        inject -> {
          scenario.getInjects().add(inject);
          inject
              .getTeams()
              .forEach(
                  team -> {
                    if (!scenario.getTeams().contains(team)) {
                      scenario.getTeams().add(team);
                    }
                  });
          inject
              .getTeams()
              .forEach(
                  team ->
                      team.getUsers()
                          .forEach(
                              user -> {
                                if (!scenario.getTeamUsers().contains(user)) {
                                  ScenarioTeamUserId compositeId = new ScenarioTeamUserId();
                                  compositeId.setScenarioId(scenario.getId());
                                  compositeId.setTeamId(team.getId());
                                  compositeId.setUserId(user.getId());
                                  boolean exists =
                                      scenarioTeamUserRepository.findById(compositeId).isPresent();

                                  if (!exists) {
                                    ScenarioTeamUser scenarioTeamUser = new ScenarioTeamUser();
                                    scenarioTeamUser.setScenario(scenario);
                                    scenarioTeamUser.setTeam(team);
                                    scenarioTeamUser.setUser(user);
                                    scenarioTeamUser =
                                        scenarioTeamUserRepository.save(scenarioTeamUser);
                                    scenario.getTeamUsers().add(scenarioTeamUser);
                                  }
                                }
                              }));
        });
  }

  private ImportTestSummary importXls(
      String importId,
      Scenario scenario,
      Exercise exercise,
      ImportMapper importMapper,
      String sheetName,
      int timezoneOffset) {
    ImportTestSummary importTestSummary = new ImportTestSummary();

    try {
      // We open the previously saved file
      String tmpdir = System.getProperty("java.io.tmpdir");
      Path file =
          Files.list(Path.of(tmpdir, pathSeparator, importId, pathSeparator))
              .findFirst()
              .orElseThrow();

      // We open the file and convert it to an apache POI object
      InputStream xlsFile = Files.newInputStream(file);
      Workbook workbook = WorkbookFactory.create(xlsFile);
      Sheet selectedSheet = workbook.getSheet(sheetName);

      Map<Integer, InjectTime> mapInstantByRowIndex = new HashMap<>();

      // For performance reasons, we compile the pattern of the Inject Importers only once
      Map<String, Pattern> mapPatternByInjectImport =
          importMapper.getInjectImporters().stream()
              .collect(
                  Collectors.toMap(
                      InjectImporter::getId,
                      injectImporter -> Pattern.compile(injectImporter.getImportTypeValue())));

      Map<String, Pattern> mapPatternByAllTeams =
          importMapper.getInjectImporters().stream()
              .flatMap(injectImporter -> injectImporter.getRuleAttributes().stream())
              .filter(ruleAttribute -> Objects.equals(ruleAttribute.getName(), "teams"))
              .filter(
                  ruleAttribute ->
                      ruleAttribute.getAdditionalConfig() != null
                          && !Strings.isBlank(
                              ruleAttribute.getAdditionalConfig().get("allTeamsValue")))
              .collect(
                  Collectors.toMap(
                      ruleAttribute -> ruleAttribute.getAdditionalConfig().get("allTeamsValue"),
                      ruleAttribute ->
                          Pattern.compile(ruleAttribute.getAdditionalConfig().get("allTeamsValue")),
                      (first, second) -> first));

      // We also get the list of teams into a map to be able to get them easily later on
      // First, all the teams that are non-contextual
      Map<String, Team> mapTeamByName =
          StreamSupport.stream(teamRepository.findAll().spliterator(), false)
              .filter(team -> !team.getContextual())
              .collect(
                  Collectors.toMap(Team::getName, Function.identity(), (first, second) -> first));

      // Then we add the contextual teams of the scenario
      List<Team> teams;
      if (exercise != null) {
        teams = exercise.getTeams();
      } else if (scenario != null) {
        teams = scenario.getTeams();
      } else {
        throw new IllegalArgumentException(
            "At least one of exercise or scenario should be present");
      }
      mapTeamByName.putAll(
          teams.stream()
              .filter(Team::getContextual)
              .collect(
                  Collectors.toMap(Team::getName, Function.identity(), (first, second) -> first)));

      ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(timezoneOffset * 60);

      AtomicInteger count = new AtomicInteger(0);
      // For each rows of the selected sheet
      selectedSheet
          .rowIterator()
          .forEachRemaining(
              row -> {
                Instant start;
                if (scenario != null) {
                  start = scenario.getRecurrenceStart();
                } else if (exercise != null) {
                  start = exercise.getStart().orElse(null);
                } else {
                  throw new IllegalArgumentException(
                      "At least one of exercise or scenario should be present");
                }
                ImportRow rowSummary =
                    importRow(
                        row,
                        importMapper,
                        start,
                        mapPatternByInjectImport,
                        mapTeamByName,
                        mapPatternByAllTeams,
                        zoneOffset,
                        count);
                // We set the exercise or scenario
                Inject inject = rowSummary.getInject();
                if (scenario != null && inject != null) {
                  inject.setScenario(scenario);
                } else if (exercise != null && inject != null) {
                  inject.setExercise(exercise);
                }
                rowSummary.setInject(inject);

                importTestSummary.getImportMessage().addAll(rowSummary.getImportMessages());
                if (rowSummary.getInject() != null) {
                  importTestSummary.getInjects().add(rowSummary.getInject());
                }
                if (rowSummary.getInjectTime() != null) {
                  mapInstantByRowIndex.put(row.getRowNum(), rowSummary.getInjectTime());
                }
              });

      // Now that we did our first pass, we do another one real quick to find out
      // the date relative to each others
      importTestSummary.getImportMessage().addAll(updateInjectDates(mapInstantByRowIndex));
      importTestSummary.setTotalRowsAnalysed(count.get());
      // We get the earliest date
      Optional<Instant> earliestDate =
          mapInstantByRowIndex.values().stream()
              .map(InjectTime::getDate)
              .filter(Objects::nonNull)
              .min(Comparator.naturalOrder());

      // If there is one, we update the date
      earliestDate.ifPresent(
          date -> {
            ZonedDateTime zonedDateTime = date.atZone(ZoneId.of("UTC"));
            Instant dayOfStart =
                ZonedDateTime.of(
                        zonedDateTime.getYear(),
                        zonedDateTime.getMonthValue(),
                        zonedDateTime.getDayOfMonth(),
                        0,
                        0,
                        0,
                        0,
                        ZoneId.of("UTC"))
                    .toInstant();
            if (scenario != null) {
              scenario.setRecurrenceStart(dayOfStart);
              scenario.setRecurrence(
                  "0 "
                      + zonedDateTime.getMinute()
                      + " "
                      + zonedDateTime.getHour()
                      + " * * *"); // Every day now + 1 hour
              scenario.setRecurrenceEnd(dayOfStart.plus(1, ChronoUnit.DAYS));
            } else if (exercise != null) {
              exercise.setStart(dayOfStart);
            } else {
              throw new IllegalArgumentException(
                  "At least one of exercise or scenario should be present");
            }
          });
    } catch (IOException ex) {
      log.error("Error while importing an xls file", ex);
      throw new BadRequestException();
    }

    // Sorting by the order of the enum declaration to have error messages first, then warn and then
    // info
    importTestSummary.getImportMessage().sort(Comparator.comparing(ImportMessage::getMessageLevel));

    return importTestSummary;
  }

  private ImportRow importRow(
      Row row,
      ImportMapper importMapper,
      Instant start,
      Map<String, Pattern> mapPatternByInjectImport,
      Map<String, Team> mapTeamByName,
      Map<String, Pattern> mapPatternByAllTeams,
      ZoneOffset timezoneOffset,
      AtomicInteger count) {
    ImportRow importTestSummary = new ImportRow();
    // The column that differenciate the importer is the same for all so we get it right now
    int colTypeIdx = CellReference.convertColStringToIndex(importMapper.getInjectTypeColumn());
    if (colTypeIdx < 0) {
      // If there are no values, we add an info message so they know there is a potential issue here
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.INFO,
                  ImportMessage.ErrorCode.NO_POTENTIAL_MATCH_FOUND,
                  Map.of(
                      "column_type_num",
                      importMapper.getInjectTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()))));
      return importTestSummary;
    }

    // If the row is completely empty, we ignore it altogether and do not send a warn message
    if (InjectUtils.checkIfRowIsEmpty(row)) {
      return importTestSummary;
    } else {
      count.getAndIncrement();
    }
    // First of all, we get the value of the differentiation cell
    Cell typeCell = row.getCell(colTypeIdx);
    if (typeCell == null) {
      // If there are no values, we add an info message so they know there is a potential issue here
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.INFO,
                  ImportMessage.ErrorCode.NO_POTENTIAL_MATCH_FOUND,
                  Map.of(
                      "column_type_num",
                      importMapper.getInjectTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()))));
      return importTestSummary;
    }

    // We find the matching importers on the inject
    List<InjectImporter> matchingInjectImporters =
        importMapper.getInjectImporters().stream()
            .filter(
                injectImporter -> {
                  Matcher matcher =
                      mapPatternByInjectImport
                          .get(injectImporter.getId())
                          .matcher(
                              InjectImportUtils.getValueAsString(
                                  row, importMapper.getInjectTypeColumn()));
                  return matcher.find();
                })
            .toList();

    // If there are no match, we add a message for the user and we go to the next row
    if (matchingInjectImporters.isEmpty()) {
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.INFO,
                  ImportMessage.ErrorCode.NO_POTENTIAL_MATCH_FOUND,
                  Map.of(
                      "column_type_num",
                      importMapper.getInjectTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()))));
      return importTestSummary;
    }

    // If there are more than one match, we add a message for the user and use the first match
    if (matchingInjectImporters.size() > 1) {
      String listMatchers =
          matchingInjectImporters.stream()
              .map(InjectImporter::getImportTypeValue)
              .collect(Collectors.joining(", "));
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.WARN,
                  ImportMessage.ErrorCode.SEVERAL_MATCHES,
                  Map.of(
                      "column_type_num",
                      importMapper.getInjectTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()),
                      "possible_matches",
                      listMatchers)));
      return importTestSummary;
    }

    InjectImporter matchingInjectImporter = matchingInjectImporters.get(0);
    InjectorContract injectorContract = matchingInjectImporter.getInjectorContract();

    // Creating the inject
    Inject inject = new Inject();
    inject.setDependsDuration(0L);

    // Adding the description
    setAttributeValue(row, matchingInjectImporter, "description", inject::setDescription);

    // Adding the title
    setAttributeValue(row, matchingInjectImporter, "title", inject::setTitle);

    // Adding the trigger time
    RuleAttribute triggerTimeRuleAttribute =
        matchingInjectImporter.getRuleAttributes().stream()
            .filter(ruleAttribute -> ruleAttribute.getName().equals("trigger_time"))
            .findFirst()
            .orElseGet(
                () -> {
                  RuleAttribute ruleAttributeDefault = new RuleAttribute();
                  ruleAttributeDefault.setAdditionalConfig(Map.of("timePattern", ""));
                  return ruleAttributeDefault;
                });

    String timePattern = triggerTimeRuleAttribute.getAdditionalConfig().get("timePattern");
    String dateAsString = Strings.EMPTY;
    if (triggerTimeRuleAttribute.getColumns() != null) {
      dateAsString =
          Arrays.stream(triggerTimeRuleAttribute.getColumns().split("\\+"))
              .map(column -> InjectImportUtils.getDateAsStringFromCell(row, column, timePattern))
              .collect(Collectors.joining());
    }
    if (dateAsString.isBlank()) {
      dateAsString = triggerTimeRuleAttribute.getDefaultValue();
    }

    Matcher relativeDayMatcher = relativeDayPattern.matcher(dateAsString);
    Matcher relativeHourMatcher = relativeHourPattern.matcher(dateAsString);
    Matcher relativeMinuteMatcher = relativeMinutePattern.matcher(dateAsString);

    boolean relativeDays = relativeDayMatcher.matches();
    boolean relativeHour = relativeHourMatcher.matches();
    boolean relativeMinute = relativeMinuteMatcher.matches();

    InjectTime injectTime = new InjectTime();
    injectTime.setUnformattedDate(dateAsString);
    injectTime.setLinkedInject(inject);

    Temporal dateTime = InjectImportUtils.getInjectDate(injectTime, timePattern);

    if (dateTime == null) {
      injectTime.setRelativeDay(relativeDays);
      if (relativeDays
          && relativeDayMatcher.groupCount() > 0
          && !relativeDayMatcher.group(1).isBlank()) {
        try {
          injectTime.setRelativeDayNumber(Integer.parseInt(relativeDayMatcher.group(1)));
        } catch (NumberFormatException ex) {
          log.warn(
              String.format("Can't format %s into an integer", relativeDayMatcher.group(1)), ex);
        }
      }
      injectTime.setRelativeHour(relativeHour);
      if (relativeHour
          && relativeHourMatcher.groupCount() > 0
          && !relativeHourMatcher.group(1).isBlank()) {
        try {
          injectTime.setRelativeHourNumber(Integer.parseInt(relativeHourMatcher.group(1)));
        } catch (NumberFormatException ex) {
          log.warn(
              String.format("Can't format %s into an integer", relativeHourMatcher.group(1)), ex);
        }
      }
      injectTime.setRelativeMinute(relativeMinute);
      if (relativeMinute
          && relativeMinuteMatcher.groupCount() > 0
          && !relativeMinuteMatcher.group(1).isBlank()) {
        try {
          injectTime.setRelativeMinuteNumber(Integer.parseInt(relativeMinuteMatcher.group(1)));
        } catch (NumberFormatException ex) {
          log.warn(
              String.format("Can't format %s into an integer", relativeMinuteMatcher.group(1)), ex);
        }
      }

      // Special case : a mix of relative day and absolute hour
      if (relativeDays
          && relativeDayMatcher.groupCount() > 1
          && !relativeDayMatcher.group(2).isBlank()) {
        Temporal date = null;
        try {
          date = LocalTime.parse(relativeDayMatcher.group(2).trim(), injectTime.getFormatter());
        } catch (DateTimeParseException firstException) {
          try {
            date = LocalTime.parse(relativeDayMatcher.group(2).trim(), DateTimeFormatter.ISO_TIME);
          } catch (DateTimeParseException exception) {
            // This is a "probably" a relative date
          }
        }
        if (date != null) {
          if (start != null) {
            injectTime.setDate(
                start
                    .atZone(timezoneOffset)
                    .toLocalDateTime()
                    .withHour(date.get(ChronoField.HOUR_OF_DAY))
                    .withMinute(date.get(ChronoField.MINUTE_OF_HOUR))
                    .toInstant(timezoneOffset));
          } else {
            importTestSummary
                .getImportMessages()
                .add(
                    new ImportMessage(
                        ImportMessage.MessageLevel.CRITICAL,
                        ImportMessage.ErrorCode.ABSOLUTE_TIME_WITHOUT_START_DATE,
                        Map.of(
                            "column_type_num",
                            importMapper.getInjectTypeColumn(),
                            "row_num",
                            String.valueOf(row.getRowNum()))));
          }
        }
      }
    }
    injectTime.setSpecifyDays(
        relativeDays || injectTime.getFormatter().equals(DateTimeFormatter.ISO_DATE_TIME));

    // We get the absolute dates available on our first pass
    if (!injectTime.isRelativeDay()
        && !injectTime.isRelativeHour()
        && !injectTime.isRelativeMinute()
        && dateTime != null) {
      if (dateTime instanceof LocalDateTime) {
        Instant injectDate =
            Instant.ofEpochSecond(((LocalDateTime) dateTime).toEpochSecond(timezoneOffset));
        injectTime.setDate(injectDate);
      } else if (dateTime instanceof LocalTime) {
        if (start != null) {
          injectTime.setDate(
              start
                  .atZone(timezoneOffset)
                  .withHour(((LocalTime) dateTime).getHour())
                  .withMinute(((LocalTime) dateTime).getMinute())
                  .toInstant());
        } else {
          importTestSummary
              .getImportMessages()
              .add(
                  new ImportMessage(
                      ImportMessage.MessageLevel.CRITICAL,
                      ImportMessage.ErrorCode.ABSOLUTE_TIME_WITHOUT_START_DATE,
                      Map.of(
                          "column_type_num",
                          importMapper.getInjectTypeColumn(),
                          "row_num",
                          String.valueOf(row.getRowNum()))));
          return importTestSummary;
        }
      }
    }

    // Initializing the content with a root node
    ObjectMapper mapper = new ObjectMapper();
    inject.setContent(mapper.createObjectNode());

    // Once it's done, we set the injectorContract
    inject.setInjectorContract(injectorContract);

    // So far, we only support one expectation
    AtomicReference<InjectExpectation> expectation = new AtomicReference<>();

    // For each rule attributes of the importer
    matchingInjectImporter
        .getRuleAttributes()
        .forEach(
            ruleAttribute -> {
              importTestSummary
                  .getImportMessages()
                  .addAll(
                      addFields(
                          inject,
                          ruleAttribute,
                          row,
                          mapTeamByName,
                          expectation,
                          importMapper,
                          mapPatternByAllTeams));
            });
    // The user is the one doing the import
    inject.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    // No exercise yet
    inject.setExercise(null);
    // No dependencies
    inject.setDependsOn(null);

    if (expectation.get() != null) {
      // We set the expectation
      ArrayNode expectationsNode = mapper.createArrayNode();
      ObjectNode expectationNode = mapper.createObjectNode();
      expectationNode.put("expectation_description", expectation.get().getDescription());
      expectationNode.put("expectation_name", expectation.get().getName());
      expectationNode.put("expectation_score", expectation.get().getExpectedScore());
      expectationNode.put("expectation_type", expectation.get().getType().name());
      expectationNode.put("expectation_expectation_group", false);
      expectationsNode.add(expectationNode);
      inject.getContent().set("expectations", expectationsNode);
    }

    importTestSummary.setInject(inject);
    importTestSummary.setInjectTime(injectTime);
    return importTestSummary;
  }

  private void setAttributeValue(
      Row row,
      InjectImporter matchingInjectImporter,
      String attributeName,
      Consumer<String> setter) {
    RuleAttribute ruleAttribute =
        matchingInjectImporter.getRuleAttributes().stream()
            .filter(attr -> attr.getName().equals(attributeName))
            .findFirst()
            .orElseThrow(ElementNotFoundException::new);

    if (ruleAttribute.getColumns() != null) {
      int colIndex = CellReference.convertColStringToIndex(ruleAttribute.getColumns());
      if (colIndex == -1) {
        return;
      }
      Cell valueCell = row.getCell(colIndex);

      if (valueCell == null) {
        setter.accept(ruleAttribute.getDefaultValue());
      } else {
        String cellValue = InjectImportUtils.getValueAsString(row, ruleAttribute.getColumns());
        setter.accept(cellValue.isBlank() ? ruleAttribute.getDefaultValue() : cellValue);
      }
    }
  }

  private List<ImportMessage> addFields(
      Inject inject,
      RuleAttribute ruleAttribute,
      Row row,
      Map<String, Team> mapTeamByName,
      AtomicReference<InjectExpectation> expectation,
      ImportMapper importMapper,
      Map<String, Pattern> mapPatternByAllTeams) {
    // If it's a reserved field, it's already taken care of
    if (importReservedField.contains(ruleAttribute.getName())) {
      return emptyList();
    }

    // For ease of use, we create a map of the available keys for the injector
    Map<String, JsonNode> mapFieldByKey =
        StreamSupport.stream(
                (Objects.requireNonNull(
                    inject
                        .getInjectorContract()
                        .map(
                            injectorContract ->
                                injectorContract.getConvertedContent().get("fields").spliterator())
                        .orElse(null))),
                false)
            .collect(
                Collectors.toMap(jsonNode -> jsonNode.get("key").asText(), Function.identity()));

    // Otherwise, the default type is text, but it can be overriden
    String type = "text";
    if (mapFieldByKey.get(ruleAttribute.getName()) != null) {
      type = mapFieldByKey.get(ruleAttribute.getName()).get("type").asText();
    } else if (ruleAttribute.getName().startsWith("expectation")) {
      type = "expectation";
    }
    switch (type) {
      case "text":
      case "textarea":
        // If text, we get the columns, split by "+" if there is a concatenation of columns
        // and then joins the result of the cells
        String columnValue = Strings.EMPTY;
        if (ruleAttribute.getColumns() != null) {
          columnValue =
              InjectImportUtils.extractAndConvertStringColumnValue(
                  row, ruleAttribute, mapFieldByKey);
        }
        if (columnValue.isBlank()) {
          inject.getContent().put(ruleAttribute.getDefaultValue(), columnValue);
        } else {
          inject.getContent().put(ruleAttribute.getName(), columnValue);
        }
        break;
      case "team":
        // If the rule type is on a team field, we split by "+" if there is a concatenation of
        // columns
        // and then joins the result, split again by "," and use the list of results to get the
        // teams by their name
        List<String> columnValues = new ArrayList<>();
        String allTeamsValue =
            ruleAttribute.getAdditionalConfig() != null
                ? ruleAttribute.getAdditionalConfig().get("allTeamsValue")
                : null;
        if (ruleAttribute.getColumns() != null) {
          columnValues =
              Arrays.stream(
                      Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                          .map(column -> InjectImportUtils.getValueAsString(row, column))
                          .collect(Collectors.joining(","))
                          .split(","))
                  .toList();
        }
        if (columnValues.isEmpty() || columnValues.stream().allMatch(String::isEmpty)) {
          List<String> defaultValues =
              Arrays.stream(ruleAttribute.getDefaultValue().split(",")).toList();
          inject
              .getTeams()
              .addAll(
                  mapTeamByName.entrySet().stream()
                      .filter(nameTeamEntry -> defaultValues.contains(nameTeamEntry.getKey()))
                      .map(Map.Entry::getValue)
                      .toList());
        } else {
          List<ImportMessage> importMessages = new ArrayList<>();
          columnValues.forEach(
              teamName -> {
                inject.setAllTeams(false);
                Matcher allTeamsMatcher = null;
                if (mapPatternByAllTeams.get(allTeamsValue) != null) {
                  allTeamsMatcher = mapPatternByAllTeams.get(allTeamsValue).matcher(teamName);
                }
                if (allTeamsValue != null && allTeamsMatcher != null && allTeamsMatcher.find()) {
                  inject.setAllTeams(true);
                } else if (mapTeamByName.containsKey(teamName)) {
                  inject.getTeams().add(mapTeamByName.get(teamName));
                } else {
                  // The team does not exist, we create a new one
                  Team team = new Team();
                  team.setName(teamName);
                  team.setContextual(true);
                  team = teamRepository.save(team);
                  mapTeamByName.put(team.getName(), team);
                  inject.getTeams().add(team);

                  // We aldo add a message so the user knows there was a new team created
                  importMessages.add(
                      new ImportMessage(
                          ImportMessage.MessageLevel.WARN,
                          ImportMessage.ErrorCode.NO_TEAM_FOUND,
                          Map.of(
                              "column_type_num",
                              importMapper.getInjectTypeColumn(),
                              "row_num",
                              String.valueOf(row.getRowNum()),
                              "team_name",
                              teamName)));
                }
              });
          if (!importMessages.isEmpty()) {
            return importMessages;
          }
        }
        break;
      case "expectation":
        // If the rule type is of an expectation,
        if (expectation.get() == null) {
          expectation.set(new InjectExpectation());
          expectation.get().setType(InjectExpectation.EXPECTATION_TYPE.MANUAL);
        }
        if (ruleAttribute.getName().contains("_")) {
          if ("score".equals(ruleAttribute.getName().split("_")[1])) {
            if (ruleAttribute.getColumns() != null) {
              List<String> columns =
                  Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                      .filter(column -> column != null && !column.isBlank())
                      .toList();
              if (!columns.isEmpty()
                  && columns.stream()
                      .allMatch(
                          column ->
                              row.getCell(CellReference.convertColStringToIndex(column))
                                      .getCellType()
                                  == CellType.NUMERIC)) {
                Double columnValueExpectation =
                    columns.stream()
                        .map(column -> InjectImportUtils.getValueAsDouble(row, column))
                        .reduce(0.0, Double::sum);
                expectation.get().setExpectedScore(columnValueExpectation.doubleValue());
              } else {
                try {
                  expectation
                      .get()
                      .setExpectedScore(Double.parseDouble(ruleAttribute.getDefaultValue()));
                } catch (NumberFormatException exception) {
                  List<ImportMessage> importMessages = new ArrayList<>();
                  importMessages.add(
                      new ImportMessage(
                          ImportMessage.MessageLevel.WARN,
                          ImportMessage.ErrorCode.EXPECTATION_SCORE_UNDEFINED,
                          Map.of(
                              "column_type_num",
                              String.join(", ", columns),
                              "row_num",
                              String.valueOf(row.getRowNum()))));
                  return importMessages;
                }
              }
            } else {
              expectation
                  .get()
                  .setExpectedScore(Double.parseDouble(ruleAttribute.getDefaultValue()));
            }
          } else if ("name".equals(ruleAttribute.getName().split("_")[1])) {
            if (ruleAttribute.getColumns() != null) {
              String columnValueExpectation =
                  Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                      .map(column -> InjectImportUtils.getValueAsString(row, column))
                      .collect(Collectors.joining());
              expectation
                  .get()
                  .setName(
                      columnValueExpectation.isBlank()
                          ? ruleAttribute.getDefaultValue()
                          : columnValueExpectation);
            } else {
              expectation.get().setName(ruleAttribute.getDefaultValue());
            }
          } else if ("description".equals(ruleAttribute.getName().split("_")[1])) {
            if (ruleAttribute.getColumns() != null) {
              String columnValueExpectation =
                  Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                      .map(column -> InjectImportUtils.getValueAsString(row, column))
                      .collect(Collectors.joining());
              expectation
                  .get()
                  .setDescription(
                      columnValueExpectation.isBlank()
                          ? ruleAttribute.getDefaultValue()
                          : columnValueExpectation);
            } else {
              expectation.get().setDescription(ruleAttribute.getDefaultValue());
            }
          }
        }

        break;
      default:
        throw new UnsupportedOperationException();
    }
    return emptyList();
  }

  private List<ImportMessage> updateInjectDates(Map<Integer, InjectTime> mapInstantByRowIndex) {
    List<ImportMessage> importMessages = new ArrayList<>();
    // First of all, are there any absolute date
    boolean allDatesAreAbsolute =
        mapInstantByRowIndex.values().stream()
            .filter(injectTime -> !injectTime.getUnformattedDate().isBlank())
            .noneMatch(
                injectTime ->
                    injectTime.getDate() == null
                        || injectTime.isRelativeDay()
                        || injectTime.isRelativeHour()
                        || injectTime.isRelativeMinute());
    boolean allDatesAreRelative =
        mapInstantByRowIndex.values().stream()
            .filter(injectTime -> !injectTime.getUnformattedDate().isBlank())
            .allMatch(
                injectTime ->
                    injectTime.getDate() == null
                        && (injectTime.isRelativeDay()
                            || injectTime.isRelativeHour()
                            || injectTime.isRelativeMinute()));

    if (allDatesAreAbsolute) {
      processDateToAbsolute(mapInstantByRowIndex);
    } else if (allDatesAreRelative) {
      // All is relative and we just need to set depends relative to each others
      // First of all, we find the minimal relative number of days and hour
      int earliestDay =
          mapInstantByRowIndex.values().stream()
              .min(Comparator.comparing(InjectTime::getRelativeDayNumber))
              .map(InjectTime::getRelativeDayNumber)
              .orElse(0);
      int earliestHourOfThatDay =
          mapInstantByRowIndex.values().stream()
              .filter(injectTime -> injectTime.getRelativeDayNumber() == earliestDay)
              .min(Comparator.comparing(InjectTime::getRelativeHourNumber))
              .map(InjectTime::getRelativeHourNumber)
              .orElse(0);
      int earliestMinuteOfThatHour =
          mapInstantByRowIndex.values().stream()
              .filter(
                  injectTime ->
                      injectTime.getRelativeDayNumber() == earliestDay
                          && injectTime.getRelativeHourNumber() == earliestHourOfThatDay)
              .min(Comparator.comparing(InjectTime::getRelativeMinuteNumber))
              .map(InjectTime::getRelativeMinuteNumber)
              .orElse(0);
      long offsetAsMinutes =
          (((earliestDay * 24L) + earliestHourOfThatDay) * 60 + earliestMinuteOfThatHour) * -1;
      mapInstantByRowIndex.values().stream()
          .filter(
              injectTime ->
                  injectTime.getDate() == null
                      && (injectTime.isRelativeDay()
                          || injectTime.isRelativeHour()
                          || injectTime.isRelativeMinute()))
          .forEach(
              injectTime -> {
                long injectTimeAsMinutes =
                    (((injectTime.getRelativeDayNumber() * 24L)
                                + injectTime.getRelativeHourNumber())
                            * 60)
                        + injectTime.getRelativeMinuteNumber()
                        + offsetAsMinutes;
                injectTime.getLinkedInject().setDependsDuration(injectTimeAsMinutes * 60);
              });
    } else {
      // Worst case scenario : there is a mix of relative and absolute dates
      // We will need to resolve this row by row in the order they are in the import file
      Optional<Map.Entry<Integer, InjectTime>> sortedInstantMap =
          mapInstantByRowIndex.entrySet().stream().min(Map.Entry.comparingByKey());
      int firstRow;
      if (sortedInstantMap.isPresent()) {
        firstRow = sortedInstantMap.get().getKey();
      } else {
        firstRow = 0;
      }
      mapInstantByRowIndex.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEachOrdered(
              integerInjectTimeEntry -> {
                InjectTime injectTime = integerInjectTimeEntry.getValue();

                if (injectTime.getDate() != null) {
                  // Special case : we have an absolute time but a relative day
                  if (injectTime.isRelativeDay()) {
                    injectTime.setDate(
                        injectTime
                            .getDate()
                            .plus(injectTime.getRelativeDayNumber(), ChronoUnit.DAYS));
                  }
                  // Great, we already have an absolute date for this one
                  // If we are the first, good, nothing more to do
                  // Otherwise, we need to get the date of the first row to set the depends on
                  if (integerInjectTimeEntry.getKey() != firstRow) {
                    Instant firstDate = mapInstantByRowIndex.get(firstRow).getDate();
                    injectTime
                        .getLinkedInject()
                        .setDependsDuration(
                            injectTime.getDate().getEpochSecond() - firstDate.getEpochSecond());
                  }
                } else {
                  // We don't have an absolute date so we need to deduce it from another row
                  if (injectTime.getRelativeDayNumber() < 0
                      || (injectTime.getRelativeDayNumber() == 0
                          && injectTime.getRelativeHourNumber() < 0)
                      || (injectTime.getRelativeDayNumber() == 0
                          && injectTime.getRelativeHourNumber() == 0
                          && injectTime.getRelativeMinuteNumber() < 0)) {
                    // We are in the past, so we need to explore the future to find the next
                    // absolute date
                    Optional<Map.Entry<Integer, InjectTime>> firstFutureWithAbsolute =
                        mapInstantByRowIndex.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .filter(
                                entry ->
                                    entry.getKey() > integerInjectTimeEntry.getKey()
                                        && entry.getValue().getDate() != null)
                            .findFirst();
                    if (firstFutureWithAbsolute.isPresent()) {
                      injectTime.setDate(
                          firstFutureWithAbsolute
                              .get()
                              .getValue()
                              .getDate()
                              .plus(injectTime.getRelativeDayNumber(), ChronoUnit.DAYS)
                              .plus(injectTime.getRelativeHourNumber(), ChronoUnit.HOURS)
                              .plus(injectTime.getRelativeMinuteNumber(), ChronoUnit.MINUTES));
                    } else {
                      importMessages.add(
                          new ImportMessage(
                              ImportMessage.MessageLevel.ERROR,
                              ImportMessage.ErrorCode.DATE_SET_IN_PAST,
                              Map.of("row_num", String.valueOf(integerInjectTimeEntry.getKey()))));
                    }
                  } else {
                    // We are in the future, so we need to explore the past to find an absolute date
                    Optional<Map.Entry<Integer, InjectTime>> firstPastWithAbsolute =
                        mapInstantByRowIndex.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .filter(
                                entry ->
                                    entry.getKey() < integerInjectTimeEntry.getKey()
                                        && entry.getValue().getDate() != null)
                            .min(Map.Entry.comparingByKey(Comparator.reverseOrder()));
                    if (firstPastWithAbsolute.isPresent()) {
                      injectTime.setDate(
                          firstPastWithAbsolute
                              .get()
                              .getValue()
                              .getDate()
                              .plus(injectTime.getRelativeDayNumber(), ChronoUnit.DAYS)
                              .plus(injectTime.getRelativeHourNumber(), ChronoUnit.HOURS)
                              .plus(injectTime.getRelativeMinuteNumber(), ChronoUnit.MINUTES));
                    } else {
                      importMessages.add(
                          new ImportMessage(
                              ImportMessage.MessageLevel.ERROR,
                              ImportMessage.ErrorCode.DATE_SET_IN_FUTURE,
                              Map.of("row_num", String.valueOf(integerInjectTimeEntry.getKey()))));
                    }
                  }
                }
              });

      processDateToAbsolute(mapInstantByRowIndex);
    }

    return importMessages;
  }

  private void processDateToAbsolute(Map<Integer, InjectTime> mapInstantByRowIndex) {
    Optional<Instant> earliestAbsoluteDate =
        mapInstantByRowIndex.values().stream()
            .map(InjectTime::getDate)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder());

    // If we have an earliest date, we calculate the dates depending on the earliest one
    earliestAbsoluteDate.ifPresent(
        earliestInstant ->
            mapInstantByRowIndex.values().stream()
                .filter(injectTime -> injectTime.getDate() != null)
                .forEach(
                    injectTime -> {
                      injectTime
                          .getLinkedInject()
                          .setDependsDuration(
                              injectTime.getDate().getEpochSecond()
                                  - earliestInstant.getEpochSecond());
                    }));
  }

  public void importInjectsForScenario(MultipartFile file, String scenarioId) throws Exception {
    Scenario targetScenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);

    this.importService.handleFileImport(file, null, targetScenario);
  }

  public void importInjectsForSimulation(MultipartFile file, String simulationId) throws Exception {
    Exercise targetSimulation =
        exerciseRepository.findById(simulationId).orElseThrow(ElementNotFoundException::new);

    this.importService.handleFileImport(file, targetSimulation, null);
  }

  public void importInjectsForAtomicTestings(MultipartFile file) throws Exception {
    this.importService.handleFileImport(file, null, null);
  }
}
