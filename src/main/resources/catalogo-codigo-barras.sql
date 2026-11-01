-- Código de barras, origen de datos y fecha del precio de referencia en el catálogo.
-- Idempotente: se puede ejecutar varias veces. El backend ejecuta al arrancar solo la parte
-- de esquema (antes de la marca BACKFILL); el backfill se aplica a mano una vez.
ALTER TABLE item_catalogo ADD COLUMN IF NOT EXISTS codigo_barras text;
ALTER TABLE item_catalogo ADD COLUMN IF NOT EXISTS origen_datos text;
ALTER TABLE item_catalogo ADD COLUMN IF NOT EXISTS precio_referencia_fecha date;
CREATE UNIQUE INDEX IF NOT EXISTS item_catalogo_codigo_barras_key
  ON item_catalogo (codigo_barras) WHERE codigo_barras IS NOT NULL;

-- BACKFILL
-- Precio de referencia = mediana del precio unitario de compras no anuladas de los últimos 180 días
-- (redondeado a pesos). Los ítems sin compras en ese rango conservan su valor actual.
UPDATE item_catalogo ic
SET precio_referencia = m.mediana,
    precio_referencia_fecha = CURRENT_DATE
FROM (
  SELECT d.item_catalogo_id,
         round((percentile_cont(0.5) WITHIN GROUP (ORDER BY d.precio_unitario_ingreso))::numeric, 0) AS mediana
  FROM detalle_ingreso_recurso d
  JOIN ingreso_recurso ir ON ir.id = d.ingreso_id
  JOIN ingreso_compra c ON c.ingreso_id = ir.id
  WHERE d.precio_unitario_ingreso IS NOT NULL
    AND lower(coalesce(ir.estado, '')) <> 'anulado'
    AND ir.fecha >= CURRENT_DATE - 180
  GROUP BY d.item_catalogo_id
) m
WHERE ic.id = m.item_catalogo_id;

UPDATE item_catalogo SET origen_datos = 'manual' WHERE origen_datos IS NULL;
