-- Esquema de huellas (SourceAFIS). Se aplica a mano: el backend no ejecuta DDL de huellas al arrancar.
CREATE TABLE IF NOT EXISTS persona_huella (
  id SERIAL PRIMARY KEY,
  persona_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id) ON DELETE CASCADE,
  dedo TEXT NOT NULL,
  formato TEXT NOT NULL DEFAULT 'sourceafis-3.18.1',
  calidad INTEGER,
  registrada TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (persona_id, dedo)
);

CREATE TABLE IF NOT EXISTS persona_huella_toma (
  huella_id INTEGER NOT NULL REFERENCES persona_huella(id) ON DELETE CASCADE,
  toma SMALLINT NOT NULL CHECK (toma BETWEEN 1 AND 5),
  plantilla BYTEA NOT NULL,
  minucias INTEGER NOT NULL,
  PRIMARY KEY (huella_id, toma)
);
