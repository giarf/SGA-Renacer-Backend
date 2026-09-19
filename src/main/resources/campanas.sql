CREATE TABLE IF NOT EXISTS campana (
  id SERIAL PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  fecha DATE NOT NULL,
  descripcion VARCHAR(2000) NOT NULL DEFAULT '',
  estado TEXT NOT NULL DEFAULT 'activa' CHECK (estado IN ('borrador', 'activa', 'cerrada')),
  version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)
);
CREATE TABLE IF NOT EXISTS campana_participante (
  id SERIAL PRIMARY KEY,
  campana_id INTEGER NOT NULL REFERENCES campana(id) ON DELETE CASCADE,
  beneficiario_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id),
  padrino_id INTEGER REFERENCES persona_natural(entidad_id),
  version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
  CHECK (beneficiario_id <> padrino_id),
  UNIQUE (campana_id, beneficiario_id),
  UNIQUE (campana_id, id)
);
CREATE INDEX IF NOT EXISTS campana_participante_padrino ON campana_participante(padrino_id);
CREATE TABLE IF NOT EXISTS campana_columna (
  id SERIAL PRIMARY KEY,
  campana_id INTEGER NOT NULL REFERENCES campana(id) ON DELETE CASCADE,
  nombre VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  tipo TEXT NOT NULL CHECK (tipo IN ('boolean', 'text', 'number')),
  clave TEXT,
  UNIQUE (campana_id, id),
  UNIQUE (campana_id, clave)
);
CREATE UNIQUE INDEX IF NOT EXISTS campana_columna_nombre ON campana_columna(campana_id, lower(nombre));
ALTER TABLE campana_columna DROP CONSTRAINT IF EXISTS campana_columna_tipo_check;
ALTER TABLE campana_columna ADD CONSTRAINT campana_columna_tipo_check CHECK (tipo IN ('boolean', 'text', 'number', 'whatsapp'));
CREATE TABLE IF NOT EXISTS campana_valor (
  campana_id INTEGER NOT NULL,
  participante_id INTEGER NOT NULL,
  columna_id INTEGER NOT NULL,
  valor JSONB NOT NULL,
  version INTEGER NOT NULL CHECK (version > 0),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (campana_id, participante_id, columna_id),
  FOREIGN KEY (campana_id, participante_id) REFERENCES campana_participante(campana_id, id) ON DELETE CASCADE,
  FOREIGN KEY (campana_id, columna_id) REFERENCES campana_columna(campana_id, id) ON DELETE CASCADE
);
