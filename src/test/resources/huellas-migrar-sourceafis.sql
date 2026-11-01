-- Migración única desde libfprint (1-11-2026). Borra las huellas libfprint de prueba; se vuelven a registrar.
BEGIN;
DELETE FROM persona_huella;
ALTER TABLE persona_huella DROP COLUMN IF EXISTS plantilla;
ALTER TABLE persona_huella ALTER COLUMN formato SET DEFAULT 'sourceafis-3.18.1';
CREATE TABLE IF NOT EXISTS persona_huella_toma (
  huella_id INTEGER NOT NULL REFERENCES persona_huella(id) ON DELETE CASCADE,
  toma SMALLINT NOT NULL CHECK (toma BETWEEN 1 AND 5),
  plantilla BYTEA NOT NULL,
  minucias INTEGER NOT NULL,
  PRIMARY KEY (huella_id, toma)
);
COMMIT;
