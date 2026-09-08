CREATE TABLE IF NOT EXISTS asistencia_evento (
  id SERIAL PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  fecha DATE NOT NULL,
  descripcion VARCHAR(2000) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS asistencia_columna (
  id SERIAL PRIMARY KEY,
  evento_id INTEGER NOT NULL REFERENCES asistencia_evento(id) ON DELETE CASCADE,
  nombre VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  tipo TEXT NOT NULL CHECK (tipo IN ('boolean', 'text', 'number')),
  UNIQUE (evento_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS asistencia_columna_nombre ON asistencia_columna(evento_id, lower(nombre));

CREATE TABLE IF NOT EXISTS asistencia_persona (
  id SERIAL PRIMARY KEY,
  evento_id INTEGER NOT NULL REFERENCES asistencia_evento(id) ON DELETE CASCADE,
  persona_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id),
  llegada TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (evento_id, persona_id),
  UNIQUE (evento_id, id)
);

CREATE TABLE IF NOT EXISTS asistencia_valor (
  evento_id INTEGER NOT NULL,
  asistencia_id INTEGER NOT NULL,
  columna_id INTEGER NOT NULL,
  valor JSONB NOT NULL,
  version INTEGER NOT NULL CHECK (version > 0),
  PRIMARY KEY (evento_id, asistencia_id, columna_id),
  FOREIGN KEY (evento_id, asistencia_id) REFERENCES asistencia_persona(evento_id, id) ON DELETE CASCADE,
  FOREIGN KEY (evento_id, columna_id) REFERENCES asistencia_columna(evento_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS asistencia_valor_columna ON asistencia_valor(evento_id, columna_id);
