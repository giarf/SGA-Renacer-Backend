CREATE TABLE IF NOT EXISTS persona_apoderado (
  persona_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id),
  apoderado_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id),
  parentesco TEXT NOT NULL CHECK (parentesco IN ('Madre','Padre','Abuela','Abuelo','Tutor/a','Otro')),
  es_contacto_principal BOOLEAN NOT NULL DEFAULT FALSE,
  observaciones TEXT NOT NULL DEFAULT '' CHECK (length(observaciones) <= 2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (persona_id, apoderado_id),
  CHECK (persona_id <> apoderado_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS persona_apoderado_principal
  ON persona_apoderado(persona_id) WHERE es_contacto_principal;
CREATE INDEX IF NOT EXISTS persona_apoderado_inverso ON persona_apoderado(apoderado_id);
