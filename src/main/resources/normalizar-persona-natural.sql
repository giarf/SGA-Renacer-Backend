-- Ejecutar al desplegar el backend que guarda PersonaNatural.
-- Conserva IDs y todas las relaciones; solo normaliza el discriminador.
BEGIN;
UPDATE entidad e
SET tipo_entidad = 'PersonaNatural'
FROM persona_natural p
WHERE p.entidad_id = e.id
  AND e.tipo_entidad IS DISTINCT FROM 'PersonaNatural';
COMMIT;
