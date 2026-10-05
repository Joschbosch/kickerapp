create table player (
    id           uuid         primary key,
    subject      varchar(255) not null,
    display_name varchar(255) not null,
    constraint uq_player_subject unique (subject)
);

create table tournament (
    id             uuid         primary key,
    name           varchar(255) not null,
    event_date     date         not null,
    status         varchar(20)  not null,
    table_count    integer      not null,
    points_win     integer      not null,
    points_draw    integer      not null,
    points_loss    integer      not null,
    goal_limit     integer      not null,
    match_minutes  integer      not null,
    planned_rounds integer
);

create table participant (
    tournament_id uuid                     not null references tournament (id) on delete cascade,
    player_id     uuid                     not null references player (id),
    status        varchar(20)              not null,
    registered_at timestamp with time zone not null,
    primary key (tournament_id, player_id)
);

create index idx_participant_player on participant (player_id);

-- Eine Zeile pro Match. Je Platz ist entweder ein echter Spieler (dummy = false, player = Spieler)
-- oder ein Dummy (dummy = true, player = Einspringer oder null).
create table tournament_match (
    id                  uuid        primary key,
    tournament_id       uuid        not null references tournament (id) on delete cascade,
    round_number        integer     not null,
    queue_position      integer     not null,
    status              varchar(20) not null,
    table_number        integer,
    slot_a1_dummy       boolean     not null,
    slot_a1_player      uuid references player (id),
    slot_a2_dummy       boolean     not null,
    slot_a2_player      uuid references player (id),
    slot_b1_dummy       boolean     not null,
    slot_b1_player      uuid references player (id),
    slot_b2_dummy       boolean     not null,
    slot_b2_player      uuid references player (id),
    goals_a             integer,
    goals_b             integer,
    result_entered_by   uuid references player (id),
    result_entered_side varchar(1),
    result_source       varchar(10),
    constraint uq_match_position unique (tournament_id, round_number, queue_position)
);

create index idx_match_tournament on tournament_match (tournament_id);
