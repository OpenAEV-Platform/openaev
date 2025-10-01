create table if not exists incident_types
(
    type_id   varchar(255) not null
        constraint incident_types_pkey
            primary key,
    type_name varchar(255) not null
);

create table if not exists groups
(
    group_id   varchar(255) not null
        constraint groups_pkey
            primary key,
    group_name varchar(255) not null
);

create table if not exists documents
(
    document_id          varchar(255) not null
        constraint documents_pkey
            primary key,
    document_name        varchar(255) not null,
    document_description varchar(255) default NULL::character varying,
    document_type        varchar(255) not null,
    document_path        varchar(255) not null
);

create table if not exists files
(
    file_id   varchar(255) not null
        constraint files_pkey
            primary key,
    file_name varchar(255) not null,
    file_path varchar(255) not null,
    file_type varchar(255) not null
);

create table if not exists parameters
(
    parameter_id    varchar(255) not null
        constraint parameters_pkey
            primary key,
    parameter_key   varchar(255) not null,
    parameter_value varchar(255) not null
);

create table if not exists tags
(
    tag_id   varchar(255) not null
        constraint tags_pkey
            primary key,
    tag_name varchar(255) not null
);

create table if not exists documents_tags
(
    document_id varchar(255) not null
        constraint fk_2b565702c33f7837
            references documents
            on delete cascade,
    tag_id      varchar(255) not null
        constraint fk_2b565702bad26311
            references tags
            on delete cascade,
    constraint documents_tags_pkey
        primary key (document_id, tag_id)
);

create index if not exists idx_2b565702c33f7837
    on documents_tags (document_id);

create index if not exists idx_2b565702bad26311
    on documents_tags (tag_id);

create table if not exists organizations
(
    organization_id          varchar(255) not null
        constraint organizations_pkey
            primary key,
    organization_name        varchar(255) not null,
    organization_description text
);

create table if not exists users
(
    user_id            varchar(255)               not null
        constraint users_pkey
            primary key,
    user_organization  varchar(255) default NULL::character varying
        constraint fk_1483a5e941221f7e
            references organizations
            on delete restrict,
    user_login         varchar(255)               not null,
    user_firstname     varchar(255)               not null,
    user_lastname      varchar(255)               not null,
    user_email         varchar(255)               not null,
    user_email2        varchar(255) default NULL::character varying,
    user_phone         varchar(255) default NULL::character varying,
    user_phone2        varchar(255) default NULL::character varying,
    user_phone3        varchar(255) default NULL::character varying,
    user_pgp_key       text,
    user_password      varchar(255) default NULL::character varying,
    user_admin         boolean                    not null,
    user_planificateur boolean      default false not null,
    user_status        smallint                   not null,
    user_lang          varchar(255) default NULL::character varying,
    user_latitude      double precision,
    user_longitude     double precision
);

create table if not exists users_groups
(
    group_id varchar(255) not null
        constraint fk_ff8ab7e0fe54d947
            references groups
            on delete cascade,
    user_id  varchar(255) not null
        constraint fk_ff8ab7e0a76ed395
            references users
            on delete cascade,
    constraint users_groups_pkey
        primary key (group_id, user_id)
);

create index if not exists idx_ff8ab7e0fe54d947
    on users_groups (group_id);

create index if not exists idx_ff8ab7e0a76ed395
    on users_groups (user_id);

create table if not exists exercises
(
    exercise_id              varchar(255)                                       not null
        constraint exercises_pkey
            primary key,
    exercise_owner           varchar(255) default NULL::character varying
        constraint fk_fa14991ea5611be
            references users,
    exercise_image           varchar(255) default NULL::character varying
        constraint fk_fa14991e00bf39d
            references files
            on delete set null,
    exercise_animation_group varchar(255) default NULL::character varying
        constraint fk_fa149911e7111ab
            references groups
            on delete set null,
    exercise_name            varchar(255)                                       not null,
    exercise_subtitle        text                                               not null,
    exercise_description     text                                               not null,
    exercise_start_date      timestamp(0) with time zone                        not null,
    exercise_end_date        timestamp(0) with time zone                        not null,
    exercise_mail_expediteur text                                               not null,
    exercise_message_header  varchar(255) default NULL::character varying,
    exercise_message_footer  varchar(255) default NULL::character varying,
    exercise_canceled        boolean                                            not null,
    exercise_type            varchar(255) default 'standard'::character varying not null,
    exercise_latitude        double precision,
    exercise_longitude       double precision
);

create table if not exists events
(
    event_id          varchar(255)           not null
        constraint events_pkey
            primary key,
    event_exercise    varchar(255) default NULL::character varying
        constraint fk_5387574ae8363ccc
            references exercises
            on delete cascade,
    event_image       varchar(255) default NULL::character varying
        constraint fk_5387574a8426b573
            references files
            on delete set null,
    event_title       varchar(255) default NULL::character varying,
    event_description varchar(255) default NULL::character varying,
    event_order       smallint     default 0 not null
);

create index if not exists idx_5387574ae8363ccc
    on events (event_exercise);

create index if not exists idx_5387574a8426b573
    on events (event_image);

create table if not exists planificateurs_events
(
    planificateur_event_id varchar(255) not null
        constraint fk_6598a2aac14685cc
            references events
            on delete cascade,
    planificateur_user_id  varchar(255) not null
        constraint fk_6598a2aac69bb6ef
            references users
            on delete cascade,
    constraint planificateurs_events_pkey
        primary key (planificateur_event_id, planificateur_user_id)
);

create index if not exists idx_6598a2aac14685cc
    on planificateurs_events (planificateur_event_id);

create index if not exists idx_6598a2aac69bb6ef
    on planificateurs_events (planificateur_user_id);

create table if not exists objectives
(
    objective_id          varchar(255) not null
        constraint objectives_pkey
            primary key,
    objective_exercise    varchar(255) default NULL::character varying
        constraint fk_6cb0696c157d9150
            references exercises
            on delete cascade,
    objective_title       varchar(255) default NULL::character varying,
    objective_description text,
    objective_priority    smallint
);

create index if not exists idx_6cb0696c157d9150
    on objectives (objective_exercise);

create table if not exists documents_exercises
(
    document_id varchar(255) not null
        constraint fk_6f785ecac33f7837
            references documents
            on delete cascade,
    exercise_id varchar(255) not null
        constraint fk_6f785ecae934951a
            references exercises
            on delete cascade,
    constraint documents_exercises_pkey
        primary key (document_id, exercise_id)
);

create index if not exists idx_6f785ecac33f7837
    on documents_exercises (document_id);

create index if not exists idx_6f785ecae934951a
    on documents_exercises (exercise_id);

create table if not exists audiences
(
    audience_id       varchar(255) not null
        constraint audiences_pkey
            primary key,
    audience_exercise varchar(255) default NULL::character varying
        constraint fk_89f9ac9380e3e92
            references exercises
            on delete cascade,
    audience_name     varchar(255) default NULL::character varying,
    audience_enabled  boolean
);

create table if not exists subaudiences
(
    subaudience_id       varchar(255) not null
        constraint subaudiences_pkey
            primary key,
    subaudience_audience varchar(255) default NULL::character varying
        constraint fk_9cf031f069138c0b
            references audiences
            on delete cascade,
    subaudience_name     varchar(255) not null,
    subaudience_enabled  boolean      not null
);

create index if not exists idx_9cf031f069138c0b
    on subaudiences (subaudience_audience);

create table if not exists users_subaudiences
(
    subaudience_id varchar(255) not null
        constraint fk_cfb417fccb0ca5a3
            references subaudiences
            on delete cascade,
    user_id        varchar(255) not null
        constraint fk_cfb417fca76ed395
            references users
            on delete cascade,
    constraint users_subaudiences_pkey
        primary key (subaudience_id, user_id)
);

create index if not exists idx_cfb417fccb0ca5a3
    on users_subaudiences (subaudience_id);

create index if not exists idx_cfb417fca76ed395
    on users_subaudiences (user_id);

create index if not exists idx_89f9ac9380e3e92
    on audiences (audience_exercise);

create table if not exists planificateurs_audiences
(
    planificateur_audience_id varchar(255) not null
        constraint fk_45be52529d6ccb13
            references audiences
            on delete cascade,
    planificateur_user_id     varchar(255) not null
        constraint fk_45be5252c69bb6ef
            references users
            on delete cascade,
    constraint planificateurs_audiences_pkey
        primary key (planificateur_audience_id, planificateur_user_id)
);

create index if not exists idx_45be52529d6ccb13
    on planificateurs_audiences (planificateur_audience_id);

create index if not exists idx_45be5252c69bb6ef
    on planificateurs_audiences (planificateur_user_id);

create table if not exists dryruns
(
    dryrun_id       varchar(255)                not null
        constraint dryruns_pkey
            primary key,
    dryrun_exercise varchar(255) default NULL::character varying
        constraint fk_f1c33dffc7c87328
            references exercises
            on delete cascade,
    dryrun_date     timestamp(0) with time zone not null,
    dryrun_speed    integer                     not null,
    dryrun_status   boolean                     not null
);

create index if not exists idx_f1c33dffc7c87328
    on dryruns (dryrun_exercise);

create table if not exists dryinjects
(
    dryinject_id      varchar(255)                not null
        constraint dryinjects_pkey
            primary key,
    dryinject_dryrun  varchar(255) default NULL::character varying
        constraint fk_6da84b5817861437
            references dryruns
            on delete cascade,
    dryinject_title   varchar(255)                not null,
    dryinject_content text,
    dryinject_date    timestamp(0) with time zone not null,
    dryinject_type    varchar(255)                not null
);

create table if not exists dryinjects_statuses
(
    status_id        varchar(255) not null
        constraint dryinjects_statuses_pkey
            primary key,
    status_dryinject varchar(255)                default NULL::character varying
        constraint fk_1863e729f2504301
            references dryinjects
            on delete cascade,
    status_name      varchar(255)                default NULL::character varying,
    status_message   text,
    status_date      timestamp(0) with time zone default NULL::timestamp with time zone,
    status_execution integer
);

create unique index if not exists uniq_1863e729f2504301
    on dryinjects_statuses (status_dryinject);

create index if not exists idx_6da84b5817861437
    on dryinjects (dryinject_dryrun);

create index if not exists idx_fa14991ea5611be
    on exercises (exercise_owner);

create index if not exists idx_fa14991e00bf39d
    on exercises (exercise_image);

create index if not exists idx_fa149911e7111ab
    on exercises (exercise_animation_group);

create unique index if not exists uniq_1483a5e948ca3048
    on users (user_login);

create index if not exists idx_1483a5e941221f7e
    on users (user_organization);

create unique index if not exists users_email_unique
    on users (user_email);

create table if not exists logs
(
    log_id       varchar(255)                not null
        constraint logs_pkey
            primary key,
    log_exercise varchar(255) default NULL::character varying
        constraint fk_f08fc65cc0891ec3
            references exercises
            on delete cascade,
    log_user     varchar(255) default NULL::character varying
        constraint fk_f08fc65c9cfd383c
            references users
            on delete cascade,
    log_title    varchar(255)                not null,
    log_content  text                        not null,
    log_date     timestamp(0) with time zone not null
);

create index if not exists idx_f08fc65cc0891ec3
    on logs (log_exercise);

create index if not exists idx_f08fc65c9cfd383c
    on logs (log_user);

create table if not exists comchecks
(
    comcheck_id         varchar(255)                not null
        constraint comchecks_pkey
            primary key,
    comcheck_exercise   varchar(255) default NULL::character varying
        constraint fk_4e039727729413d0
            references exercises
            on delete cascade,
    comcheck_audience   varchar(255) default NULL::character varying
        constraint fk_4e039727218352d4
            references audiences
            on delete cascade,
    comcheck_start_date timestamp(0) with time zone not null,
    comcheck_end_date   timestamp(0) with time zone not null
);

create table if not exists comchecks_statuses
(
    status_id          varchar(255)                not null
        constraint comchecks_statuses_pkey
            primary key,
    status_user        varchar(255) default NULL::character varying
        constraint fk_a25f7872b5957bdd
            references users
            on delete cascade,
    status_comcheck    varchar(255) default NULL::character varying
        constraint fk_a25f787295a4a46f
            references comchecks
            on delete cascade,
    status_last_update timestamp(0) with time zone not null,
    status_state       boolean                     not null
);

create index if not exists idx_a25f7872b5957bdd
    on comchecks_statuses (status_user);

create index if not exists idx_a25f787295a4a46f
    on comchecks_statuses (status_comcheck);

create index if not exists idx_4e039727729413d0
    on comchecks (comcheck_exercise);

create index if not exists idx_4e039727218352d4
    on comchecks (comcheck_audience);

create table if not exists incidents
(
    incident_id     varchar(255) not null
        constraint incidents_pkey
            primary key,
    incident_type   varchar(255) default NULL::character varying
        constraint fk_e65135d066d22096
            references incident_types
            on delete cascade,
    incident_event  varchar(255) default NULL::character varying
        constraint fk_e65135d0609aa8cd
            references events
            on delete cascade,
    incident_title  varchar(255) not null,
    incident_story  text         not null,
    incident_weight integer      not null,
    incident_order  smallint     not null
);

create table if not exists injects
(
    inject_id                   varchar(255)                not null
        constraint injects_pkey
            primary key,
    inject_incident             varchar(255) default NULL::character varying
        constraint fk_a60839b2e3da09ad
            references incidents
            on delete cascade,
    inject_user                 varchar(255) default NULL::character varying
        constraint fk_a60839b2e20fc097
            references users
            on delete cascade,
    inject_title                varchar(255)                not null,
    inject_description          text                        not null,
    inject_content              text,
    inject_date                 timestamp(0) with time zone not null,
    inject_type                 varchar(255)                not null,
    inject_all_audiences        boolean                     not null,
    inject_enabled              boolean                     not null,
    inject_latitude             double precision,
    inject_longitude            double precision
);

create index if not exists idx_a60839b2e3da09ad
    on injects (inject_incident);

create index if not exists idx_a60839b2e20fc097
    on injects (inject_user);

create table if not exists injects_audiences
(
    inject_id   varchar(255) not null
        constraint fk_ba0cebb87983aee
            references injects
            on delete cascade,
    audience_id varchar(255) not null
        constraint fk_ba0cebb8848cc616
            references audiences
            on delete cascade,
    constraint injects_audiences_pkey
        primary key (inject_id, audience_id)
);

create index if not exists idx_ba0cebb87983aee
    on injects_audiences (inject_id);

create index if not exists idx_ba0cebb8848cc616
    on injects_audiences (audience_id);

create table if not exists injects_subaudiences
(
    inject_id      varchar(255) not null
        constraint fk_96e1b96c7983aee
            references injects
            on delete cascade,
    subaudience_id varchar(255) not null
        constraint fk_96e1b96ccb0ca5a3
            references subaudiences
            on delete cascade,
    constraint injects_subaudiences_pkey
        primary key (inject_id, subaudience_id)
);

create index if not exists idx_96e1b96c7983aee
    on injects_subaudiences (inject_id);

create index if not exists idx_96e1b96ccb0ca5a3
    on injects_subaudiences (subaudience_id);

create table if not exists injects_statuses
(
    status_id        varchar(255) not null
        constraint injects_statuses_pkey
            primary key,
    status_inject    varchar(255)                default NULL::character varying
        constraint fk_658a47a864e0dbd
            references injects
            on delete cascade,
    status_name      varchar(255)                default NULL::character varying,
    status_message   text,
    status_date      timestamp(0) with time zone default NULL::timestamp with time zone,
    status_execution integer
);

create unique index if not exists uniq_658a47a864e0dbd
    on injects_statuses (status_inject);

create index if not exists idx_e65135d066d22096
    on incidents (incident_type);

create index if not exists idx_e65135d0609aa8cd
    on incidents (incident_event);

create table if not exists outcomes
(
    outcome_id       varchar(255) not null
        constraint outcomes_pkey
            primary key,
    outcome_incident varchar(255) default NULL::character varying
        constraint fk_6e54d0fa2db358a7
            references incidents
            on delete cascade,
    outcome_comment  text,
    outcome_result   integer      not null
);

create unique index if not exists uniq_6e54d0fa2db358a7
    on outcomes (outcome_incident);

create table if not exists subobjectives
(
    subobjective_id          varchar(255) not null
        constraint subobjectives_pkey
            primary key,
    subobjective_objective   varchar(255) default NULL::character varying
        constraint fk_33218ecf261c0e05
            references objectives
            on delete cascade,
    subobjective_title       varchar(255) not null,
    subobjective_description text         not null,
    subobjective_priority    smallint     not null
);

create table if not exists incidents_subobjectives
(
    incident_id     varchar(255) not null
        constraint fk_4a01cb2559e53fb9
            references incidents
            on delete cascade,
    subobjective_id varchar(255) not null
        constraint fk_4a01cb25c80c8e53
            references subobjectives
            on delete cascade,
    constraint incidents_subobjectives_pkey
        primary key (incident_id, subobjective_id)
);

create index if not exists idx_4a01cb2559e53fb9
    on incidents_subobjectives (incident_id);

create index if not exists idx_4a01cb25c80c8e53
    on incidents_subobjectives (subobjective_id);

create index if not exists idx_33218ecf261c0e05
    on subobjectives (subobjective_objective);

create table if not exists grants
(
    grant_id       varchar(255) not null
        constraint grants_pkey
            primary key,
    grant_group    varchar(255) default NULL::character varying
        constraint fk_64adc7d620b0bd5e
            references groups
            on delete cascade,
    grant_exercise varchar(255) default NULL::character varying
        constraint fk_64adc7d6cae7a988
            references exercises
            on delete cascade,
    grant_name     varchar(255) not null
);

create index if not exists idx_64adc7d620b0bd5e
    on grants (grant_group);

create index if not exists idx_64adc7d6cae7a988
    on grants (grant_exercise);

create unique index if not exists "grant"
    on grants (grant_group, grant_exercise, grant_name);

create table if not exists tokens
(
    token_id         varchar(255)                not null
        constraint tokens_pkey
            primary key,
    token_user       varchar(255) default NULL::character varying
        constraint fk_aa5a118eef97e32b
            references users
            on delete cascade,
    token_value      varchar(255)                not null,
    token_created_at timestamp(0) with time zone not null
);

create index if not exists idx_aa5a118eef97e32b
    on tokens (token_user);

create unique index if not exists tokens_value_unique
    on tokens (token_value);

create table if not exists migrations
(
    installed_rank integer                 not null
        constraint migrations_pk
            primary key,
    version        varchar(50),
    description    varchar(200)            not null,
    type           varchar(20)             not null,
    script         varchar(1000)           not null,
    checksum       integer,
    installed_by   varchar(100)            not null,
    installed_on   timestamp default now() not null,
    execution_time integer                 not null,
    success        boolean                 not null
);

create index if not exists migrations_s_idx
    on migrations (success);

