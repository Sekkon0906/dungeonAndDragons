CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ORGANIZER', 'PLAYER'))
);

CREATE TABLE game_sessions (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 20),
    organizer_id UUID NOT NULL REFERENCES user_accounts(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED'))
);

CREATE INDEX ix_sessions_starts_at ON game_sessions(starts_at);
CREATE INDEX ix_sessions_organizer ON game_sessions(organizer_id);

CREATE TABLE registrations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id),
    player_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_registration UNIQUE (session_id, player_id)
);

CREATE INDEX ix_registrations_player ON registrations(player_id);
