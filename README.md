# SGA Renacer — Backend API

> Sistema de Gestión Administrativa para Fundación Familia Renacer.  
> API REST construida con **Cask** + **Quill** (Scala 2.13) sobre PostgreSQL.

---

## 🚀 Quick Start

### Prerrequisitos

| Herramienta | Versión mínima |
|-------------|---------------|
| Java (JDK)  | 11+           |
| Scala       | 2.13.x        |
| SBT         | 1.8+          |
| PostgreSQL  | 14+ (con extensión `unaccent`) |

### Instalación y ejecución

```bash
git clone <repository-url>
cd SGA-Renacer-Backend

# Habilitar extensión de búsqueda sin acentos (solo una vez)
psql -d sga_renacer -c "CREATE EXTENSION IF NOT EXISTS unaccent;"

# Compilar y correr
sbt compile
sbt run
```

El servidor estará disponible en **`http://localhost:8080`**.

---

## 📋 API Reference

Base URL: `http://localhost:8080`

> Todos los ejemplos usan `curl`. Reemplaza los IDs y datos de ejemplo según corresponda.

---

### 👤 Personas

CRUD completo de personas naturales. Cada persona se compone de una `Entidad` (datos base) + `PersonaNatural` (datos específicos).

| Método   | Endpoint               | Descripción                |
|----------|------------------------|----------------------------|
| `GET`    | `/api/personas`        | Listar todas las personas  |
| `POST`   | `/api/personas`        | Crear persona + entidad    |
| `GET`    | `/api/personas/:id`    | Obtener persona por ID     |
| `PUT`    | `/api/personas/:id`    | Actualizar persona         |
| `DELETE` | `/api/personas/:id`    | Eliminar persona           |

#### Ejemplo — `POST /api/personas`

```bash
curl -X POST http://localhost:8080/api/personas \
  -H "Content-Type: application/json" \
  -d '{
    "rut": "12.345.678-9",
    "telefono": "+56912345678",
    "correo": "juan@example.com",
    "direccion": "Calle Falsa 123",
    "comuna": "Quillota",
    "nombres": "Juan",
    "apellidos": "Pérez",
    "genero": "Masculino",
    "ocupacion": "Carpintero",
    "fechaNacimiento": "1990-05-15"
  }'
```

> Nota: Si el ítem ya existe en el catálogo, incluye `itemCatalogoId` con su valor; si es uno nuevo, omite ese campo y el backend lo creará automáticamente.

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "mensaje": "Persona registrada exitosamente",
  "id": 1
}
```
</details>

#### Ejemplo — `PUT /api/personas/:id`

```bash
curl -X PUT http://localhost:8080/api/personas/1 \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "rut": "12.345.678-9",
    "tipoEntidad": "Persona",
    "telefono": "+56912345678",
    "correo": "nuevo@email.com",
    "direccion": "Calle Nueva 789",
    "comuna": "Quillota",
    "nombres": "Juan Carlos",
    "apellidos": "Pérez",
    "genero": "Masculino",
    "ocupacion": "Carpintero",
    "fechaNacimiento": "1990-05-15"
  }'
```

---

### 🏢 Instituciones

CRUD completo de instituciones y organizaciones.

| Método   | Endpoint                    | Descripción                     |
|----------|-----------------------------|---------------------------------|
| `GET`    | `/api/instituciones`        | Listar todas las instituciones  |
| `POST`   | `/api/instituciones`        | Crear institución + entidad     |
| `GET`    | `/api/instituciones/:id`    | Obtener institución por ID      |
| `PUT`    | `/api/instituciones/:id`    | Actualizar institución          |
| `DELETE` | `/api/instituciones/:id`    | Eliminar institución            |

#### Ejemplo — `POST /api/instituciones`

```bash
curl -X POST http://localhost:8080/api/instituciones \
  -H "Content-Type: application/json" \
  -d '{
    "rut": "76.543.210-K",
    "telefono": "+56933333333",
    "correo": "contacto@ong.cl",
    "direccion": "Av. Principal 100",
    "comuna": "Santiago",
    "razonSocial": "ONG Solidaria",
    "nombreFantasia": "Solidaria",
    "subtipoInstitucion": "ONG",
    "rubro": "Asistencia Social"
  }'
```

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "mensaje": "Institución registrada exitosamente",
  "id": 5
}
```
</details>

#### Ejemplo — `PUT /api/instituciones/:id`

```bash
curl -X PUT http://localhost:8080/api/instituciones/5 \
  -H "Content-Type: application/json" \
  -d '{
    "id": 5,
    "rut": "76.543.210-K",
    "tipoEntidad": "Institucion",
    "telefono": "+56933333333",
    "correo": "info@ong.cl",
    "direccion": "Av. Principal 200",
    "comuna": "Santiago",
    "razonSocial": "ONG Solidaria Chile",
    "nombreFantasia": "Solidaria",
    "subtipoInstitucion": "ONG",
    "rubro": "Asistencia Social"
  }'
```

---

### 🎭 Roles de Persona (Sub-recursos)

Los roles se modelan como **sub-recursos** de una persona. También existen **listados independientes** enriquecidos con datos de la persona.

#### Listados independientes

| Método | Endpoint              | Descripción                          |
|--------|-----------------------|--------------------------------------|
| `GET`  | `/api/beneficiarios`  | Listar todos los beneficiarios       |
| `GET`  | `/api/colaboradores`  | Listar todos los colaboradores       |
| `GET`  | `/api/trabajadores`   | Listar todos los trabajadores        |
| `GET`  | `/api/directivos`     | Listar todos los directivos          |

#### Beneficiario

| Método   | Endpoint                            | Descripción             |
|----------|-------------------------------------|-------------------------|
| `GET`    | `/api/personas/:id/beneficiario`    | Obtener info            |
| `POST`   | `/api/personas/:id/beneficiario`    | Asignar rol             |
| `PUT`    | `/api/personas/:id/beneficiario`    | Actualizar info         |
| `DELETE` | `/api/personas/:id/beneficiario`    | Remover rol             |

##### Ejemplo — `POST /api/personas/:id/beneficiario`

```bash
curl -X POST http://localhost:8080/api/personas/3/beneficiario \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 0,
    "familiaId": 1,
    "escolaridad": "Media Completa",
    "tallaRopa": "L",
    "observacionesMedicas": "Diabetes tipo 2"
  }'
```

> **Nota:** `personaId` se sobreescribe con el `:id` del path.

#### Colaborador

| Método   | Endpoint                            | Descripción             |
|----------|-------------------------------------|-------------------------|
| `GET`    | `/api/personas/:id/colaborador`     | Obtener info            |
| `POST`   | `/api/personas/:id/colaborador`     | Asignar rol             |
| `PUT`    | `/api/personas/:id/colaborador`     | Actualizar info         |
| `DELETE` | `/api/personas/:id/colaborador`     | Remover rol             |

##### Ejemplo — `POST /api/personas/:id/colaborador`

```bash
curl -X POST http://localhost:8080/api/personas/4/colaborador \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 0,
    "tipoColaborador": "Voluntario",
    "esAnonimo": false
  }'
```

#### Trabajador

| Método   | Endpoint                            | Descripción             |
|----------|-------------------------------------|-------------------------|
| `GET`    | `/api/personas/:id/trabajador`      | Obtener info            |
| `POST`   | `/api/personas/:id/trabajador`      | Asignar rol             |
| `PUT`    | `/api/personas/:id/trabajador`      | Actualizar info         |
| `DELETE` | `/api/personas/:id/trabajador`      | Remover rol             |

##### Ejemplo — `POST /api/personas/:id/trabajador`

```bash
curl -X POST http://localhost:8080/api/personas/5/trabajador \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 0,
    "cargo": "Asistente Social",
    "fechaIngreso": "2025-03-01"
  }'
```

#### Directivo

| Método   | Endpoint                            | Descripción             |
|----------|-------------------------------------|-------------------------|
| `GET`    | `/api/personas/:id/directivo`       | Obtener info            |
| `POST`   | `/api/personas/:id/directivo`       | Asignar rol             |
| `PUT`    | `/api/personas/:id/directivo`       | Actualizar info         |
| `DELETE` | `/api/personas/:id/directivo`       | Remover rol             |

##### Ejemplo — `POST /api/personas/:id/directivo`

```bash
curl -X POST http://localhost:8080/api/personas/6/directivo \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 0,
    "cargo": "Presidente",
    "firmaDigitalUrl": "/firmas/presidente.png"
  }'
```

---

### 👨‍👩‍👧‍👦 Familias

Gestión de familias y membresía de beneficiarios.

| Método   | Endpoint                                     | Descripción             |
|----------|----------------------------------------------|-------------------------|
| `GET`    | `/api/familias`                              | Listar familias         |
| `POST`   | `/api/familias`                              | Crear familia           |
| `GET`    | `/api/familias/:id`                          | Obtener familia         |
| `PUT`    | `/api/familias/:id`                          | Actualizar familia      |
| `DELETE` | `/api/familias/:id`                          | Eliminar familia        |
| `GET`    | `/api/familias/:id/beneficiarios`            | Listar miembros         |
| `POST`   | `/api/familias/:id/beneficiarios`            | Agregar miembro         |
| `DELETE` | `/api/familias/:id/beneficiarios/:personaId` | Remover miembro         |

#### Ejemplo — `POST /api/familias`

```bash
curl -X POST http://localhost:8080/api/familias \
  -H "Content-Type: application/json" \
  -d '{
    "id": 0,
    "nombreFamilia": "Familia Pérez",
    "puntosVulnerabilidad": 85,
    "jefeHogarId": 3
  }'
```

#### Ejemplo — `POST /api/familias/:id/beneficiarios`

```bash
curl -X POST http://localhost:8080/api/familias/1/beneficiarios \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 5
  }'
```

---

### 💰 Ingresos

Registro tipificado de ingresos (donaciones, compras, subvenciones).

| Método   | Endpoint                          | Descripción                      |
|----------|-----------------------------------|----------------------------------|
| `GET`    | `/api/ingresos`                   | Listar todos los ingresos        |
| `POST`   | `/api/ingresos/donacion`          | Donación monetaria               |
| `POST`   | `/api/ingresos/donacion-bienes`   | Donación de bienes               |
| `POST`   | `/api/ingresos/compra`            | Compra                           |
| `POST`   | `/api/ingresos/pecuniario`        | Ingreso pecuniario independiente |
| `POST`   | `/api/ingresos/subvencion`        | Subvención                       |
| `GET`    | `/api/ingresos/:id`               | Obtener ingreso por ID           |
| `DELETE` | `/api/ingresos/:id`               | Eliminar ingreso                 |

#### Ejemplo — `POST /api/ingresos/donacion`

```bash
curl -X POST http://localhost:8080/api/ingresos/donacion \
  -H "Content-Type: application/json" \
  -d '{
    "ingreso": {
      "origenEntidadId": 2,
      "responsableInternoId": 5,
      "fecha": "2026-02-27",
      "montoTotal": 50000,
      "tipoTransaccion": "Donacion",
      "estado": "Cerrado",
      "anotaciones": "Colecta municipal julio"
    },
    "donacion": {
      "ingresoId": 0,
      "propositoEspecifico": "Programa Invierno",
      "gestorId": 7
    },
    "pecuniario": {
      "ingresoId": 0,
      "cuentaDestinoId": 1,
      "metodoTransferencia": "Transferencia"
    }
  }'
```

> Nota: Si omites `fecha`, el backend usa automáticamente la fecha actual (`LocalDate.now()`), pero puedes enviarla para registrar movimientos de días anteriores.

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "id_ingreso": 10,
  "status": "registrado"
}
```
</details>

#### Ejemplo — `POST /api/ingresos/donacion-bienes`

```bash
curl -X POST http://localhost:8080/api/ingresos/donacion-bienes \
  -H "Content-Type: application/json" \
  -d '{
    "ingreso": {
      "origenEntidadId": 2,
      "responsableInternoId": 5,
      "fecha": "2026-02-27",
      "montoTotal": 30000,
      "tipoTransaccion": "Donacion",
      "estado": "Cerrado",
      "anotaciones": "Entrega campaña abrigo"
    },
    "donacion": {
      "ingresoId": 0,
      "propositoEspecifico": "Donación de ropa",
      "gestorId": 7
    },
    "items": [
      {
        "nombre": "Chaqueta Polar",
        "categoria": "Vestuario",
        "unidad": "Unidades",
        "cantidad": 10,
        "precio": 3000
      }
    ]
  }'
```

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "id_ingreso": 11,
  "mensaje": "Donación de bienes registrada exitosamente"
}
```
</details>

#### Ejemplo — `POST /api/ingresos/compra`

```bash
curl -X POST http://localhost:8080/api/ingresos/compra \
  -H "Content-Type: application/json" \
  -d '{
    "ingreso": {
      "origenEntidadId": 10,
      "responsableInternoId": 5,
      "fecha": "2026-02-27",
      "montoTotal": 120000,
      "tipoTransaccion": "Compra",
      "estado": "Cerrado",
      "anotaciones": "Compra operativa"
    },
    "compra": {
      "ingresoId": 0,
      "cuentaOrigenId": 1,
      "numeroFacturaBoleta": "F-001234",
      "montoNeto": 100840,
      "montoIva": 19160
    },
    "detalles": [
      {
        "id": 0,
        "ingresoId": 0,
        "itemCatalogoId": 5,
        "cantidad": 20,
        "precioUnitarioIngreso": 6000
      }
    ]
  }'
```

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "id_ingreso": 12,
  "mensaje": "Compra registrada exitosamente"
}
```
</details>

#### Ejemplo — `POST /api/ingresos/subvencion`

```bash
curl -X POST http://localhost:8080/api/ingresos/subvencion \
  -H "Content-Type: application/json" \
  -d '{
    "ingreso": {
      "origenEntidadId": 15,
      "responsableInternoId": 5,
      "fecha": "2026-02-27",
      "montoTotal": 5000000,
      "tipoTransaccion": "Subvencion",
      "estado": "Abierto"
    },
    "subvencion": {
      "ingresoId": 0,
      "nombreProyecto": "Proyecto Invierno 2026",
      "fechaRendicionLimite": "2026-12-31"
    },
    "pecuniario": {
      "ingresoId": 0,
      "cuentaDestinoId": 1,
      "metodoTransferencia": "Transferencia"
    }
  }'
```

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "id_ingreso": 13,
  "mensaje": "Subvención registrada exitosamente"
}
```
</details>

#### Ejemplo — `GET /api/ingresos`

```bash
curl http://localhost:8080/api/ingresos
```

```json
[
  {
    "id": 42,
    "fecha": "2026-02-25",
    "tipo": "DonacionPecuniaria",
    "montoTotal": 50000,
    "estado": "Cerrado",
    "descripcion": "Programa Invierno"
  },
  {
    "id": 41,
    "fecha": "2026-02-24",
    "tipo": "DonacionBienes",
    "montoTotal": 30000,
    "estado": "Cerrado",
    "descripcion": "Donación de ropa"
  }
]
```

> Los posibles valores de `tipo` son `DonacionPecuniaria`, `DonacionBienes`, `Compra`, `Subvencion` e `IngresoPecuniario`. Se ordenan por fecha (más recientes primero) para servir como historial.

---

### 📤 Egresos

Registro unificado de egresos (pecuniarios y/o con detalle de ítems).

| Método   | Endpoint                                       | Descripción                                      |
|----------|------------------------------------------------|--------------------------------------------------|
| `POST`   | `/api/egresos`                                 | Crear egreso (con o sin `egreso_pecuniario`)    |
| `GET`    | `/api/egresos`                                 | Listar egresos con filtros opcionales            |
| `GET`    | `/api/egresos/:id`                             | Detalle (cabecera + pecuniario + detalles)       |
| `PUT`    | `/api/egresos/:id`                             | Actualizar egreso                                |
| `DELETE` | `/api/egresos/:id`                             | Eliminar egreso (revierte saldo si pecuniario)   |

#### Ejemplo — `POST /api/egresos` (con detalles de ítems)

```bash
curl -X POST http://localhost:8080/api/egresos \
  -H "Content-Type: application/json" \
  -d '{
    "egreso": {
      "fecha": "2026-02-27",
      "tipoEgreso": "Ayuda Social",
      "montoTotal": 25000,
      "responsableInternoId": 5,
      "anotaciones": "Entrega por campaña de invierno",
      "destinoEntidadId": 3,
      "propositoEspecifico": "Apoyo social urgente"
    },
    "detalles": [
      {
        "itemCatalogoId": 1,
        "cantidad": 2
      }
    ]
  }'
```

#### Ejemplo — `POST /api/egresos` (pecuniario)

```bash
curl -X POST http://localhost:8080/api/egresos \
  -H "Content-Type: application/json" \
  -d '{
    "egreso": {
      "fecha": "2026-02-27",
      "tipoEgreso": "Consumo Interno",
      "montoTotal": 15000,
      "responsableInternoId": 5,
      "anotaciones": "Pago operativo de taller",
      "destinoEntidadId": 7,
      "propositoEspecifico": "Compra insumos taller"
    },
    "pecuniario": {
      "cuentaOrigenId": 2,
      "metodoTransferencia": "Transferencia"
    },
    "detalles": []
  }'
```

> Nota: La API aplica `fecha = hoy` cuando no se envía.  
> Nota: `montoTotal` viene directo cuando existe `pecuniario`; si hay `detalles`, se calcula sumando `cantidad * precioUnitarioPpp`.

---

### 📦 Catálogo de Inventario

Gestión de ítems de inventario, búsqueda y utilitarios.

| Método | Endpoint                               | Descripción                 |
|--------|----------------------------------------|-----------------------------|
| `GET`  | `/api/catalogo`                        | Listar todos los ítems      |
| `POST` | `/api/catalogo`                        | Crear ítem de catálogo      |
| `PUT`  | `/api/catalogo/:id`                    | Actualizar ítem             |
| `GET`  | `/api/catalogo/buscar?q=...`           | Buscar ítems por nombre     |
| `GET`  | `/api/catalogo/utilitarios`            | Categorías + unidades       |
| `GET`  | `/api/catalogo/utilitarios/categorias` | Solo categorías únicas      |

#### Ejemplo — `POST /api/catalogo`

```bash
curl -X POST http://localhost:8080/api/catalogo \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Arroz Grado 2",
    "categoria": "Alimentos",
    "unidadMedidaEstandar": "Kilos",
    "precioReferencia": 1200
  }'
```

> Nota: El backend inicializa `stockActual`, `valorTotalStock` y `precioPromedioPonderado` en 0 automáticamente al crear un ítem.

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "id": 15,
  "mensaje": "Ítem registrado exitosamente en el catálogo"
}
```
</details>

#### Ejemplo — `PUT /api/catalogo/:id`

```bash
curl -X PUT http://localhost:8080/api/catalogo/15 \
  -H "Content-Type: application/json" \
  -d '{
    "id": 15,
    "nombre": "Arroz Integral",
    "categoria": "Alimentos",
    "unidadMedidaEstandar": "Kilos",
    "precioReferencia": 1500
  }'
```

---

### 🏦 Cuentas Financieras

Gestión de cajas/cuentas bancarias y consulta de movimientos.

| Método   | Endpoint                        | Descripción                    |
|----------|---------------------------------|--------------------------------|
| `GET`    | `/api/cuentas`                  | Listar todas las cuentas       |
| `POST`   | `/api/cuentas`                  | Crear cuenta                   |
| `GET`    | `/api/cuentas/:id`              | Obtener cuenta + saldo         |
| `PUT`    | `/api/cuentas/:id`              | Actualizar nombre              |
| `DELETE` | `/api/cuentas/:id`              | Eliminar (si no hay movimientos)|
| `GET`    | `/api/cuentas/:id/movimientos`  | Ver ingresos/egresos asociados |

#### Ejemplo — `POST /api/cuentas`

```bash
curl -X POST http://localhost:8080/api/cuentas \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Caja Chica"
  }'
```

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "id": 2,
  "mensaje": "Cuenta creada exitosamente"
}
```
</details>

#### Ejemplo — `PUT /api/cuentas/:id`

```bash
curl -X PUT http://localhost:8080/api/cuentas/2 \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Banco Estado - Cuenta Corriente"
  }'
```

#### Ejemplo — `GET /api/cuentas/:id/movimientos` (respuesta)

```json
{
  "ingresos": [
    {
      "id": 5,
      "origenEntidadId": 2,
      "tipoTransaccion": "Donacion",
      "montoTotal": 50000,
      "estado": "Cerrado"
    }
  ],
  "egresos": [
    {
      "id": 8,
      "origenEntidadId": 10,
      "tipoTransaccion": "Compra",
      "montoTotal": 120000,
      "estado": "Cerrado"
    }
  ]
}
```

---

### 📝 Solicitudes de Materiales

Creación y gestión de solicitudes internas de recursos.

| Método | Endpoint               | Descripción                      |
|--------|------------------------|----------------------------------|
| `GET`  | `/api/solicitudes`     | Listar todas las solicitudes     |
| `POST` | `/api/solicitudes`     | Crear solicitud con ítems        |
| `GET`  | `/api/solicitudes/:id` | Obtener solicitud + ítems        |
| `PUT`  | `/api/solicitudes/:id` | Actualizar estado / autorizar    |

#### Ejemplo — `POST /api/solicitudes`

```bash
curl -X POST http://localhost:8080/api/solicitudes \
  -H "Content-Type: application/json" \
  -d '{
    "solicitud": {
      "id": 0,
      "solicitanteId": 7,
      "programa": "Taller de Carpintería",
      "fechaSolicitud": "2026-02-20T10:00:00",
      "estado": "Pendiente",
      "autorizadorId": null
    },
    "items": [
      {
        "id": 0,
        "solicitudId": null,
        "itemCatalogoId": 5,
        "descripcionManual": null,
        "cantidadRequerida": 10,
        "cantidadEntregada": null
      },
      {
        "id": 0,
        "solicitudId": null,
        "itemCatalogoId": null,
        "descripcionManual": "Tornillos 2 pulgadas",
        "cantidadRequerida": 100,
        "cantidadEntregada": null
      }
    ]
  }'
```

<details>
<summary>Respuesta exitosa — <code>201</code></summary>

```json
{
  "mensaje": "Solicitud recibida",
  "items_count": 2
}
```
</details>

#### Ejemplo — `PUT /api/solicitudes/:id`

```bash
curl -X PUT http://localhost:8080/api/solicitudes/1 \
  -H "Content-Type: application/json" \
  -d '{
    "estado": "Aprobado",
    "autorizadorId": 2
  }'
```

---

### 🔍 Entidades (Búsqueda Unificada)

Búsqueda cross-type y verificación de RUT.

| Método | Endpoint                          | Descripción              |
|--------|-----------------------------------|--------------------------|
| `GET`  | `/api/entidades?tipo=...&q=...`   | Buscar entidades         |
| `GET`  | `/api/entidades/existe-rut?rut=...` | Verificar si RUT existe |
| `POST` | `/api/entidades/actualizar`       | Actualizar persona (legacy) |

#### Ejemplo — `POST /api/entidades/actualizar`

```bash
curl -X POST http://localhost:8080/api/entidades/actualizar \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "rut": "12.345.678-9",
    "tipoEntidad": "Persona",
    "telefono": "+56912345678",
    "correo": "actualizado@email.com",
    "direccion": "Nueva Dirección 456",
    "comuna": "Viña del Mar",
    "nombres": "Juan Carlos",
    "apellidos": "Pérez López",
    "genero": "Masculino",
    "ocupacion": "Albañil",
    "fechaNacimiento": "1990-05-15"
  }'
```

#### Ejemplo — `GET /api/entidades` (búsqueda)

```bash
# Buscar personas por texto
curl "http://localhost:8080/api/entidades?tipo=Persona&q=Juan"

# Verificar si un RUT ya existe
curl "http://localhost:8080/api/entidades/existe-rut?rut=12.345.678-9"
```

---

## 🔧 Detalles Técnicos

### Stack Tecnológico

| Componente     | Tecnología          |
|----------------|---------------------|
| Web Framework  | Cask                |
| ORM            | Quill (PostgreSQL)  |
| JSON           | Play JSON           |
| Base de Datos  | PostgreSQL 14+      |
| Lenguaje       | Scala 2.13          |

### CORS

Configurado para `http://localhost:5173`. Todos los endpoints mutables incluyen handlers `OPTIONS` para requests preflight.

### Búsqueda Avanzada

- **Sin acentos**: Usa la extensión `unaccent` de PostgreSQL
- **Ranking de relevancia**: Prioriza coincidencias por prefijo
- **Multi-campo**: Busca en nombres, apellidos, razón social, RUT, ocupación, anotaciones, nombre fantasía

### Convenciones

| Concepto           | Formato / Valores                              |
|--------------------|-------------------------------------------------|
| Fechas             | ISO 8601 (`YYYY-MM-DD` / `YYYY-MM-DDTHH:mm:ss`)|
| RUT                | Con puntos y guión: `12.345.678-9`              |
| Estados transacción| `Pendiente`, `Cerrado`, `Anulado`               |
| Tipos de entidad   | `Persona`, `Institucion`                        |

### Códigos de Error

| Código | Significado                              |
|--------|------------------------------------------|
| `200`  | Éxito                                    |
| `201`  | Recurso creado                           |
| `204`  | Sin contenido (preflight CORS)           |
| `400`  | Request inválido / error de validación   |
| `404`  | Recurso no encontrado                    |
| `409`  | Conflicto (RUT duplicado, FK constraint) |
| `500`  | Error interno del servidor               |

Formato de error estándar:
```json
{ "error": "Descripción del error" }
```

---

## 📦 Estructura del Proyecto

```
src/main/scala/cl/familiarenacer/sga/
├── api/
│   ├── ApiSupport.scala                # Trait compartido (CORS, respond, JSON formatters)
│   ├── SgaApiApp.scala                 # Entry point — orquesta todas las rutas
│   └── routes/
│       ├── PersonasRoutes.scala        # /api/personas + /api/personas/:id/rol
│       ├── EntidadesRoutes.scala       # /api/entidades (búsqueda unificada)
│       ├── InstitucionesRoutes.scala   # /api/instituciones
│       ├── RolesRoutes.scala           # /api/beneficiarios, colaboradores, etc.
│       ├── FamiliasRoutes.scala        # /api/familias
│       ├── IngresosRoutes.scala        # /api/ingresos/*
│       ├── EgresosRoutes.scala         # /api/egresos/*
│       ├── CatalogoRoutes.scala        # /api/catalogo
│       ├── CuentasRoutes.scala         # /api/cuentas
│       └── SolicitudesRoutes.scala     # /api/solicitudes
├── modelos/
│   ├── Base.scala                      # Entidad, Familia, CuentaFinanciera
│   ├── Personas.scala                  # PersonaNatural, Institucion, Roles
│   ├── Inventario.scala                # ItemCatalogo
│   ├── Ingresos.scala                  # IngresoRecurso, Donacion, Compra, Subvencion
│   ├── Egresos.scala                   # EgresoRecurso, AyudaSocial, ConsumoInterno
│   ├── Solicitudes.scala              # SolicitudMaterial, ItemSolicitud
│   └── Dto.scala                       # DTOs compartidos
└── repositorios/
    ├── DB.scala                        # Configuración de base de datos
    ├── EntidadRepository.scala         # CRUD de entidades + búsqueda unificada
    ├── InstitucionRepository.scala     # CRUD de instituciones
    ├── RolesRepository.scala           # Sub-roles de persona
    ├── FamiliaRepository.scala         # Familias + membresía
    ├── DonacionRepository.scala        # Todos los tipos de ingreso
    ├── EgresoRepository.scala          # Todos los tipos de egreso
    ├── SolicitudRepository.scala       # CRUD de solicitudes
    ├── CuentaFinancieraRepository.scala # Cuentas + movimientos
    └── InventarioRepository.scala      # Inventario + catálogo
```

---

## 🔐 Seguridad

- Validación de formato RUT chileno
- Transacciones atómicas para operaciones críticas
- Bloqueo pesimista (`SELECT ... FOR UPDATE`) en actualizaciones de inventario
- Integridad referencial a nivel de base de datos
- Violaciones de FK retornan `409 Conflict`

---

## 📞 Contacto

Para reportar problemas o sugerencias, crea un issue en el repositorio del proyecto.
