CREATE TABLE IF NOT EXISTS persona_huella (
  id SERIAL PRIMARY KEY,
  persona_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id) ON DELETE CASCADE,
  dedo TEXT NOT NULL,
  plantilla TEXT NOT NULL CHECK (length(plantilla) > 0),
  formato TEXT NOT NULL DEFAULT '',
  calidad INTEGER,
  registrada TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (persona_id, dedo)
);
