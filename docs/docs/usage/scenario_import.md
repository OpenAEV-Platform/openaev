# Importing Injects into a Scenario

Recreating a timeline of [injects](injects.md) that were already defined in a spreadsheet can be a frustrating task. To help users save time, we added the possibility to import injects as defined in an xls file into a [scenario](scenario.md). This is done via a two-steps process : [creating a mapper](#how-to-create-a-mapper) and [importing the xls file using the mapper](#how-to-import-injects-into-a-scenario-using-a-mapper).

## How to create a mapper ?

First of all, to import [injects](injects.md) into a [scenario](scenario.md), you need to create a mapper. To do that, using an admin account, navigate to the Settings > Data ingestion page. You will then be able to see a list of all the mappers but also to create new ones by clicking on the "+" button on the bottom right of the screen.

![List of Mappers](assets/mapper_screen.png)

### Setting up the mapper to tell each injects apart 

When creating a new mapper, you will quickly be asked to choose an inject type column. This column is the one that will allow the mapper to figure out which injects to create (Sending a mail, sending an sms, ...). Once this column has been chosen, you can add a representation for an inject type. 

The first thing to define in this representation is the matching type in the xls. This is the value that will define which inject to create when scanning the column defined in "inject type column". For instance, if you want to create an inject that sends individual emails when in the column there is the word "mail", then you will need to set the value as "mail". You can also make use of [regular expressions](https://en.wikipedia.org/wiki/Regular_expression) in this field. Please keep in mind though that this value is case sensitive. 

![Creating an xls mapper](assets/xls_mapper_example.png)

Once that is done, you can select the inject type among a list of [injects](injects.md) that are compatible with the xls import. When that selection is done, you will be able to set a column for each of the attribute that can be completed using the import. If you wish to set a default value you can do so by clicking the gear on the right side of the field.

### Properly setting the trigger time of the inject
It should also be noted that the "Trigger Time" field has a second parameter that can be set using the gear button. It can be used to set a custom format for specific dates and or time to be interpreted. The complete format rules are available [here](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html) but here is a very quick overview :

| Symbol | Meaning                    | Presentation | Examples            |
|--------|----------------------------|--------------|---------------------|
| y      | year-of-era                | year         | 2004; 04            |
| M/L    | month-of-year              | number/text  | 7; 07; Jul; July; J |
| d      | day-of-month               | number       | 10                  |
| a      | am-pm-of-day               | text         | PM                  |
| h      | clock-hour-of-am-pm (1-12) | number       | 12                  |
| H      | hour-of-day (0-23)         | number       | 0                   |
| m      | minute-of-hour             | number       | 30                  |
| s      | second-of-minute           | number       | 55                  |
| '      | escape for text            | delimiter    |                     |
| ''     | single quote               | literal      | '                   |
| [      | optional section start     |              |                     |
| ]      | optional section end       |              |                     |

Please, do note that if you wish to use exact time of the day (e.g. 9:30) in your trigger time, you will need to set a launch date on your scenario before importing the timeline of injects from the xls file.

You can also decide to use relative dates for each injects. For instance, you can say that your first inject happens at T and that subsequent injects happens at T+x. If so, you can set relative dates using the following values :

| Symbol    | Meaning               |
|-----------|-----------------------|
| D         | Day                   |
| J         | Day                   |
| H         | Hour                  |
| T         | Hour                  |
| M         | Minutes               |

This means that if you want your inject to start 2 days, 3 hours and 45 minutes after the start of your scenario, you can set the trigger time to D+2 H+3 M+45. When using relative dates, you do not need to define a pattern for the trigger time field.

Once you have set all fields you wish to set, you can click on the create button if you wish to create your mapper but you can also click on the test button to check that it works as intended.
### Teams
If your team name already exists, it will be automatically linked to your Inject; otherwise, a contextual team (available only within this scenario) will be created.

### Testing the mapper
If you click on the test button, you'll then be asked to choose a file. Once that is done, you will have to select the sheet to test out of the spreadsheet and the timezone. You will then be able to click on Test to have the result field filled as well as a list of the messages generated during the import (those are not saved, and are just there to help figure out what happened during the import itself).

![Testing an xls mapper](assets/mapper_test_example.png)

## How to import injects into a scenario using a mapper ?

Once your mapper has been created, navigate to your [scenario](scenario.md) and then to the [injects](injects.md) tab. There, you will be able to click on an import button on the top right.
A modal will be opening, inviting you to select an .xls/.xlsx file. Once it has been selected, you can click on next. You will then be asked to choose the sheet to import out of the spreadsheet and to select the mapper to use. You will also be able to select the timezone to use for the import. Once everything is set, click on the launch import button and your injects will be imported into the current scenario !
Please do note that if all the dates in the xls file are absolute time of the day (e.g. 9:30, 12:45, ...), it is required for the scenario to have a launch date set.

## Example 
#### 1. Mapper
    
![Testing an xls mapper](assets/update_the_xls_mapper.png)
![Testing an xls mapper](assets/mapper_inject_type_sms.png)
![Testing an xls mapper](assets/mapper_inject_type_mail.png)

Trigger time configuration:

![mapper_inject_import_xls_trigger_time.png](assets/mapper_inject_import_xls_trigger_time.png)

#### 2. XLS

   | A | B                                                                                              | C                                           | D                                                                             | E                                                                | F                                                                                                                                                        | G                                                                                                                 | H                                                                     | I                                                          | J                                         | K                                            |
   |---|------------------------------------------------------------------------------------------------|---------------------------------------------|-------------------------------------------------------------------------------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------------------|-------------------------------------------|----------------------------------------------|
   |   | **Action to be taken**                                                                         | **Timeline**                                | **Teams**                                                                     | **Subject**                                                      | **Content**                                                                                                                                              | **Comments**                                                                                                      | **Expectation Description**                                           | **Expectation Name**                                       | **Expectation Score**                     | **Done/To do**                               |
   |   | <span style="color: #1bb6ff"> Notify the exercise sponsor by SMS </span>                       | <span style="color: #1bb6ff"> M-5 </span>   | <span style="color: #1bb6ff"> Sponsor </span>                                 | <span style="color: #1bb6ff"> Cybersecurity exercise </span>     | <span style="color: #1bb6ff"> Hello, the exercise starts at 09:00. Please remain available for decisions. </span>                                        | <span style="color: #1bb6ff"> Use the crisis cell number; keep the message factual and concise. </span>           | <span style="color: #1bb6ff"> Sponsor notified before start </span>   | <span style="color: #1bb6ff"> Sponsor notification </span> | <span style="color: #1bb6ff"> 100 </span> | <span style="color: #1bb6ff"> To do </span>  |
   |   | <span style="color: #1bb6ff"> Send an announcement email to executive management </span>       | <span style="color: #1bb6ff"> 09:05 </span> | <span style="color: #1bb6ff"> Executive management </span>                    | <span style="color: #1bb6ff"> Exercise launch </span>            | <span style="color: #1bb6ff"> An incident response exercise is starting. Objectives, duration, and Teams channel are provided in the attachment. </span> | <span style="color: #1bb6ff"> Clearly mention “Exercise” to avoid confusion. </span>                              | <span style="color: #1bb6ff"> Announcement message sent </span>       | <span style="color: #1bb6ff"> Exec announcement </span>    | <span style="color: #1bb6ff"> 100 </span> | <span style="color: #1bb6ff"> To do  </span> |
   |   | Open the crisis bridge (Teams/bridge)                                                          | 09:10                                       | Incident Commander                                                            | Crisis meeting                                                   | Teams link, access code, speaking and decision rules.                                                                                                    | Create the channel; invite SOC/IT Ops/Comms/Legal.                                                                | Bridge operational < 5 min                                            | Bridge opened                                              | 100                                       | To do                                        |
   |   | Activate the crisis cell                                                                       | 09:15                                       | Crisis cell                                                                   | Activation                                                       | Please join the bridge and acknowledge (R/A/A).                                                                                                          | List of members and alternates attached.                                                                          | Quorum reached in < 10 min                                            | Crisis cell activation                                     | 100                                       | To do                                        |
   |   | Initial brief to SOC and IT Ops                                                                | T+2                                         | SOC, IT Ops                                                                   | Initial alert                                                    | Network and web server anomalies detected (indicators provided). Prioritize triage.                                                                      | Provide prepared IOCs and fake logs.                                                                              | Initial triage started                                                | Initial triage                                             | 80                                        | To do                                        |
   |   | <span style="color: #1bb6ff"> Notify the Service Desk (user email) </span>                     | <span style="color: #1bb6ff"> 11:00 </span> | <span style="color: #1bb6ff">  AI Desk </span>                                | <span style="color: #1bb6ff"> Internal communication </span>     | <span style="color: #1bb6ff"> Draft: possible slowdowns; investigation in progress; update in +30 min. </span>                                           | <span style="color: #1bb6ff"> Validate with Comms and the Incident Commander. </span>                             | <span style="color: #1bb6ff"> Draft ready within 15 min </span>       | <span style="color: #1bb6ff"> Internal comms ready </span> | <span style="color: #1bb6ff"> 25 </span>  | <span style="color: #1bb6ff"> To do  </span> |
   |   | Planner / Prepare the exercise                                                                 |                                             |                                                                               |                                                                  |                                                                                                                                                          |                                                                                                                   |                                                                       |                                                            |                                           |                                              |
   |   | Consolidate contacts and escalation paths                                                      | 12:00                                       | Planner                                                                       | Crisis directory                                                 | Verify numbers, roles, on-call, and alternate channels.                                                                                                  | Directory available in the planner/facilitator panel.                                                             | Directory verified and shared                                         | Crisis directory                                           | 100                                       | To do                                        |
   |   | Prepare artifacts and materials (IOCs, fake logs, screenshots)                                 | 12:30                                       | Planner/Red Team                                                              | Exercise artifacts                                               | Indicator pack ready (hashes, domains, alert templates, screenshots).                                                                                    | Support available in the planner/facilitator panel.                                                               | Artifacts ready and tested                                            | Artifact pack                                              | 100                                       | To do                                        |
   |   | Facilitator / Run the exercise                                                                 |                                             |                                                                               |                                                                  |                                                                                                                                                          |                                                                                                                   |                                                                       |                                                            |                                           |                                              |
   |   | <span style="color: #1bb6ff"> Send the initial alert to the SOC team by SMS </span>            | <span style="color: #1bb6ff"> T-1 </span>   | <span style="color: #1bb6ff"> SOC </span>                                     | <span style="color: #1bb6ff"> Alert: IT system anomalies </span> | <span style="color: #1bb6ff"> Describe simulated symptoms and scope; provide IOC #1. </span>                                                             | <span style="color: #1bb6ff"> Timeline available in the planner/facilitator panel. </span>                        | <span style="color: #1bb6ff"> Alert sent </span>                      | <span style="color: #1bb6ff"> SOC alert </span>            | <span style="color: #1bb6ff"> 100 </span> | <span style="color: #1bb6ff"> To do  </span> |
   |   | <span style="color: #1bb6ff"> Inform executive management of the actual start by email </span> | <span style="color: #1bb6ff"> 16:00 </span> | <span style="color: #1bb6ff"> Executive management </span>                    | <span style="color: #1bb6ff"> Exercise start </span>             | <span style="color: #1bb6ff"> The exercise starts now; situation updates every 30 minutes. </span>                                                       | <span style="color: #1bb6ff"> Preparing stimuli in advance saves time on the day. </span>                         | <span style="color: #1bb6ff"> Leadership informed </span>             | <span style="color: #1bb6ff"> Leadership info </span>      | <span style="color: #1bb6ff"> 100 </span> | <span style="color: #1bb6ff"> To do  </span> |
   |   | Observe and record key decisions                                                               | 16:05                                       | Observer                                                                      | Observation                                                      | Note decisions, timelines, friction points, and escalations.                                                                                             | Use the provided observation grid.                                                                                | Complete observations                                                 | Observation                                                | 100                                       | To do                                        |
   |   | Validate simulated remediation and record results                                              | 17:00                                       | Incident Commander, Tech Leads                                                | Remediation                                                      | Confirm measures (isolation, IOC blocking, patching, restoration) and expected outcomes.                                                                 | Share the timeline with the facilitator; observation grid with the observer.                                      | Remediation validated                                                 | Remediation                                                | 100                                       | To do                                        |
   |   | Close the exercise                                                                             |                                             |                                                                               |                                                                  |                                                                                                                                                          |                                                                                                                   |                                                                       |                                                            |                                           |                                              |
   |   | Hold the hot debrief                                                                           | 17:30                                       | All                                                                           | Hot debrief                                                      | 30–45 min: key facts, strengths, improvement areas, immediate actions.                                                                                   | Debriefing support available in the planner/facilitator panel.                                                    | Debrief conducted                                                     | Hot debrief                                                | 100                                       | To do                                        |
   |   | <span style="color: #1bb6ff"> Send the report and action plan by email </span>                 | <span style="color: #1bb6ff"> T+1 </span>   | <span style="color: #1bb6ff"> IT Team, Executive management, Sponsors </span> | <span style="color: #1bb6ff"> Report </span>                     | <span style="color: #1bb6ff"> Consolidated report, action plan, owners and deadlines; follow-up scheduled. </span>                                       | <span style="color: #1bb6ff"> Plan a cold debrief in 2–4 weeks; invite additional stakeholders if needed. </span> | <span style="color: #1bb6ff"> Report sent and actions tracked </span> | <span style="color: #1bb6ff"> Report + action plan </span> | <span style="color: #1bb6ff"> 100 </span> | <span style="color: #1bb6ff"> To do  </span> |

[Download](assets/model_inject_xlsx.xlsx)
#### 3. Result

The mapper maps every blue-highlighted row in the Excel file and creates one Inject per row.

Because, for this example, the mapper is configured with the following “Matching type in the XLS” rules:

- .\*(sms|SMS).\*
- .\*mail.\*

The scenario has been scheduled before import of the file and the **trigger time**, into the mapper, has been configured. 

![mapper_xls_inject_import_result](assets/mapper_xls_inject_import_result.png)
![mapper_inject_import_xls_interactive_view.png](assets/mapper_inject_import_xls_interactive_view.png)

Team “AI Desk” already exists and is associated with the Inject “Notify the Service Desk (user email),” while the others are created at import as contextual teams.

![mapper_import_inject_team.png](assets/mapper_import_inject_team.png)


