# SGA Renacer - Backend API

Sistema de Gestión Administrativa para Fundación Renacer.

## 🚀 Quick Start

### Prerequisites

- **Scala**: 2.13.x
- **SBT**: 1.8.x or later
- **PostgreSQL**: 14.x or later (with `unaccent` extension)
- **Java**: 11 or later

### Installation

```bash
# Clone the repository
git clone <repository-url>
cd SGA-Renacer-Backend

# Compile the project
sbt compile

# Run the server
sbt run
```

The server will be available at `http://localhost:8080`

---

## 📋 API Reference

The API follows a **resource-oriented** design. Root entities (Personas, Instituciones) serve as base resources, and relationships (roles, memberships) are modeled as sub-resources.

### Personas

Full CRUD for natural persons. Each persona is an `Entidad` (base) + `PersonaNatural` (specific data).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/personas` | List all personas |
| `POST` | `/api/personas` | Create persona + entity |
| `GET` | `/api/personas/:id` | Get full persona by ID |
| `PUT` | `/api/personas/:id` | Update persona + entity |
| `DELETE` | `/api/personas/:id` | Delete persona |

<details>
<summary><strong>POST /api/personas</strong> — Request body</summary>

```json
{
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
}
```
</details>

<details>
<summary><strong>PUT /api/personas/:id</strong> — Request body</summary>

```json
{
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
}
```
</details>

---

### Instituciones

Full CRUD for institutions/organizations.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/instituciones` | List all institutions |
| `POST` | `/api/instituciones` | Create institution + entity |
| `GET` | `/api/instituciones/:id` | Get full institution by ID |
| `PUT` | `/api/instituciones/:id` | Update institution + entity |
| `DELETE` | `/api/instituciones/:id` | Delete institution |

<details>
<summary><strong>POST /api/instituciones</strong> — Request body</summary>

```json
{
  "rut": "76.543.210-K",
  "telefono": "+56933333333",
  "correo": "contacto@ong.cl",
  "direccion": "Av. Principal 100",
  "comuna": "Santiago",
  "razonSocial": "ONG Solidaria",
  "nombreFantasia": "Solidaria",
  "subtipoInstitucion": "ONG",
  "rubro": "Asistencia Social"
}
```
</details>

---

### Persona Sub-Roles

Roles are modeled as **sub-resources** of a persona for CRUD operations, and also available as **independent lists** for enriched views.

#### Independent Listings (enriched with persona data)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/beneficiarios` | List all beneficiarios + persona data |
| `GET` | `/api/colaboradores` | List all colaboradores + persona data |
| `GET` | `/api/trabajadores` | List all trabajadores + persona data |
| `GET` | `/api/directivos` | List all directivos + persona data |

Each returns a flat JSON array with persona fields (`id`, `rut`, `nombres`, `apellidos`, `genero`, `telefono`, `correo`, `direccion`, `comuna`) merged with role-specific fields.

#### Beneficiario (sub-resource)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/personas/:id/beneficiario` | Get beneficiario info |
| `POST` | `/api/personas/:id/beneficiario` | Assign beneficiario role |
| `PUT` | `/api/personas/:id/beneficiario` | Update beneficiario info |
| `DELETE` | `/api/personas/:id/beneficiario` | Remove beneficiario role |

<details>
<summary><strong>POST /api/personas/:id/beneficiario</strong> — Request body</summary>

```json
{
  "personaId": 0,
  "familiaId": 1,
  "escolaridad": "Media Completa",
  "tallaRopa": "L",
  "observacionesMedicas": "Diabetes tipo 2"
}
```
> `personaId` is overridden by the `:id` path parameter.
</details>

#### Colaborador

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/personas/:id/colaborador` | Get colaborador info |
| `POST` | `/api/personas/:id/colaborador` | Assign colaborador role |
| `PUT` | `/api/personas/:id/colaborador` | Update colaborador info |
| `DELETE` | `/api/personas/:id/colaborador` | Remove colaborador role |

<details>
<summary><strong>POST /api/personas/:id/colaborador</strong> — Request body</summary>

```json
{
  "personaId": 0,
  "tipoColaborador": "Voluntario",
  "esAnonimo": false
}
```
</details>

#### Trabajador

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/personas/:id/trabajador` | Get trabajador info |
| `POST` | `/api/personas/:id/trabajador` | Assign trabajador role |
| `PUT` | `/api/personas/:id/trabajador` | Update trabajador info |
| `DELETE` | `/api/personas/:id/trabajador` | Remove trabajador role |

<details>
<summary><strong>POST /api/personas/:id/trabajador</strong> — Request body</summary>

```json
{
  "personaId": 0,
  "cargo": "Asistente Social",
  "fechaIngreso": "2025-03-01"
}
```
</details>

#### Directivo

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/personas/:id/directivo` | Get directivo info |
| `POST` | `/api/personas/:id/directivo` | Assign directivo role |
| `PUT` | `/api/personas/:id/directivo` | Update directivo info |
| `DELETE` | `/api/personas/:id/directivo` | Remove directivo role |

<details>
<summary><strong>POST /api/personas/:id/directivo</strong> — Request body</summary>

```json
{
  "personaId": 0,
  "cargo": "Presidente",
  "firmaDigitalUrl": "/firmas/presidente.png"
}
```
</details>

---

### Familias

Family management with membership (beneficiarios belonging to a family).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/familias` | List all families |
| `POST` | `/api/familias` | Create family |
| `GET` | `/api/familias/:id` | Get family by ID |
| `PUT` | `/api/familias/:id` | Update family |
| `DELETE` | `/api/familias/:id` | Delete family |
| `GET` | `/api/familias/:id/beneficiarios` | List family members |
| `POST` | `/api/familias/:id/beneficiarios` | Add member to family |
| `DELETE` | `/api/familias/:id/beneficiarios/:personaId` | Remove member |

<details>
<summary><strong>POST /api/familias</strong> — Request body</summary>

```json
{
  "id": 0,
  "nombreFamilia": "Familia Pérez",
  "puntosVulnerabilidad": 85,
  "jefeHogarId": 3
}
```
</details>

<details>
<summary><strong>POST /api/familias/:id/beneficiarios</strong> — Add member</summary>

```json
{
  "personaId": 5
}
```
</details>

---

### Ingresos (Income)

Typed ingreso creation with a common listing endpoint.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/ingresos` | List all ingresos |
| `POST` | `/api/ingresos/donacion` | Register monetary donation |
| `POST` | `/api/ingresos/donacion-bienes` | Register goods donation |
| `POST` | `/api/ingresos/compra` | Register purchase |
| `POST` | `/api/ingresos/pecuniario` | Register standalone monetary income |
| `POST` | `/api/ingresos/subvencion` | Register subsidy |
| `GET` | `/api/ingresos/:id` | Get ingreso by ID |
| `DELETE` | `/api/ingresos/:id` | Delete ingreso |

<details>
<summary><strong>POST /api/ingresos/donacion</strong> — Monetary donation</summary>

```json
{
  "ingreso": {
    "id": 0,
    "origenEntidadId": 2,
    "responsableInternoId": 5,
    "montoTotal": 50000,
    "tipoTransaccion": "Donacion",
    "estado": "Cerrado"
  },
  "donacion": {
    "ingresoId": 0,
    "numeroCertificado": "DON-2026-001",
    "propositoEspecifico": "Programa Invierno"
  },
  "pecuniario": {
    "ingresoId": 0,
    "cuentaDestinoId": 1,
    "metodoTransferencia": "Transferencia"
  }
}
```
</details>

<details>
<summary><strong>POST /api/ingresos/compra</strong> — Purchase</summary>

```json
{
  "ingreso": {
    "id": 0,
    "origenEntidadId": 10,
    "responsableInternoId": 5,
    "montoTotal": 120000,
    "tipoTransaccion": "Compra",
    "estado": "Cerrado"
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
}
```
</details>

<details>
<summary><strong>POST /api/ingresos/subvencion</strong> — Subsidy</summary>

```json
{
  "ingreso": {
    "id": 0,
    "origenEntidadId": 15,
    "responsableInternoId": 5,
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
}
```
</details>

---

### Egresos (Expenses)

Typed egreso creation.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/egresos` | List all egresos |
| `POST` | `/api/egresos/ayuda-social` | Register social aid delivery |
| `POST` | `/api/egresos/consumo-interno` | Register internal consumption |
| `GET` | `/api/egresos/:id` | Get egreso by ID |

<details>
<summary><strong>POST /api/egresos/ayuda-social</strong> — Social aid</summary>

```json
{
  "egreso": {
    "id": 0,
    "fecha": "2026-02-15",
    "tipoEgreso": "AyudaSocial",
    "montoValorizadoTotal": 25000,
    "creadoPorId": 5
  },
  "ayuda": {
    "egresoId": 0,
    "beneficiarioPersonaId": 3,
    "motivoEntrega": "Necesidad urgente por bajas temperaturas"
  },
  "detalles": [
    {
      "id": 0,
      "egresoId": 0,
      "itemCatalogoId": 1,
      "cantidad": 2,
      "precioUnitarioPpp": 4500
    }
  ]
}
```
</details>

<details>
<summary><strong>POST /api/egresos/consumo-interno</strong> — Internal consumption</summary>

```json
{
  "egreso": {
    "id": 0,
    "fecha": "2026-02-15",
    "tipoEgreso": "ConsumoInterno",
    "montoValorizadoTotal": 15000,
    "creadoPorId": 5
  },
  "consumo": {
    "egresoId": 0,
    "programaEvento": "Taller de Arte - Febrero",
    "responsablePersonaId": 7
  },
  "detalles": [
    {
      "id": 0,
      "egresoId": 0,
      "itemCatalogoId": 12,
      "cantidad": 10,
      "precioUnitarioPpp": 1500
    }
  ]
}
```
</details>

---

### Catálogo (Inventory Catalog)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/catalogo` | List all catalog items |
| `POST` | `/api/catalogo` | Create new catalog item |
| `PUT` | `/api/catalogo/:id` | Update catalog item |
| `GET` | `/api/catalogo/buscar?q=...` | Search items by name |
| `GET` | `/api/catalogo/utilitarios` | Get categories & units |
| `GET` | `/api/catalogo/utilitarios/categorias` | Get unique categories |

---

### Cuentas Financieras (Financial Accounts)

Manage financial accounts (cajas) and view their associated movements.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/cuentas` | List all accounts |
| `POST` | `/api/cuentas` | Create account |
| `GET` | `/api/cuentas/:id` | Get account details + balance |
| `PUT` | `/api/cuentas/:id` | Update account name |
| `DELETE` | `/api/cuentas/:id` | Delete account (if no movements) |
| `GET` | `/api/cuentas/:id/movimientos` | List all movements in/out |

<details>
<summary><strong>POST /api/cuentas</strong> — Create account</summary>

```json
{
  "id": 0,
  "nombre": "Caja Chica",
  "saldoActual": 0
}
```
</details>

<details>
<summary><strong>PUT /api/cuentas/:id</strong> — Update name</summary>

```json
{
  "nombre": "Banco Estado - Cuenta Corriente"
}
```
</details>

<details>
<summary><strong>GET /api/cuentas/:id/movimientos</strong> — Response</summary>

Returns all ingresos (pecuniarios deposited into this account) and egresos (compras paid from this account):

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
</details>

---

### Solicitudes (Material Requests)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/solicitudes` | List all solicitudes |
| `POST` | `/api/solicitudes` | Create solicitud with items |
| `GET` | `/api/solicitudes/:id` | Get solicitud + items |
| `PUT` | `/api/solicitudes/:id` | Update status / authorize |

<details>
<summary><strong>PUT /api/solicitudes/:id</strong> — Update status</summary>

```json
{
  "estado": "Aprobado",
  "autorizadorId": 2
}
```
</details>

---

### Entidades (Unified Search)

Legacy unified entity search endpoint. Useful for cross-type searching.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/entidades?tipo=...&q=...` | Search entities |
| `GET` | `/api/entidades/existe-rut?rut=...` | Check if RUT exists |

---

## 🔧 Technical Details

### CORS

Configured for `http://localhost:5173`. All mutable endpoints include `OPTIONS` handlers for preflight requests.

### Advanced Search

- **Accent-insensitive**: Uses PostgreSQL `unaccent` extension
- **Relevance ranking**: Prioritizes prefix matches
- **Multi-field**: Searches names, surnames, razón social, RUT, ocupacion, anotaciones, nombreFantasia

### Database Setup

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Web Framework | Cask |
| ORM | Quill (PostgreSQL) |
| JSON | Play JSON |
| Database | PostgreSQL 14+ |

### Conventions

- **Dates**: ISO 8601 (`YYYY-MM-DD` / `YYYY-MM-DDTHH:mm:ss`)
- **RUT format**: Stored with dots and hyphen: `12.345.678-9`
- **Transaction states**: `Pendiente`, `Cerrado`, `Anulado`
- **Entity types**: `Persona`, `Institucion`

### Error Handling

| Code | Meaning |
|------|---------|
| `200` | Success |
| `201` | Resource created |
| `204` | Success (no content, CORS preflight) |
| `400` | Invalid request / validation error |
| `404` | Resource not found |
| `409` | Conflict (duplicate RUT, FK constraint) |
| `500` | Internal server error |

```json
{ "error": "Descripción del error" }
```

---

## 📦 Project Structure

```
src/main/scala/cl/familiarenacer/sga/
├── api/
│   └── SgaApiApp.scala              # Endpoints & routing
├── modelos/
│   ├── Base.scala                    # Entidad, Familia, CuentaFinanciera
│   ├── Personas.scala               # PersonaNatural, Institucion, Roles
│   ├── Inventario.scala             # ItemCatalogo
│   ├── Ingresos.scala               # IngresoRecurso, Donacion, Compra, Subvencion
│   ├── Egresos.scala                # EgresoRecurso, AyudaSocial, ConsumoInterno
│   ├── Solicitudes.scala            # SolicitudMaterial, ItemSolicitud
│   └── Dto.scala                    # DTOs
└── repositorios/
    ├── DB.scala                     # Database configuration
    ├── EntidadRepository.scala      # Entity CRUD & unified search
    ├── InstitucionRepository.scala  # Institution CRUD
    ├── RolesRepository.scala        # Persona sub-roles CRUD
    ├── FamiliaRepository.scala      # Family management & membership
    ├── DonacionRepository.scala     # All ingreso types
    ├── EgresoRepository.scala       # All egreso types
    ├── SolicitudRepository.scala    # Solicitudes CRUD
    ├── CuentaFinancieraRepository.scala # Financial account CRUD + movements
    └── InventarioRepository.scala   # Inventory & catalog operations
```

## 🔐 Security

- Chilean RUT format validation
- Atomic transactions for critical operations
- Pessimistic locking (`FOR UPDATE`) on inventory updates
- Referential integrity enforced at DB level
- FK constraint violations return `409 Conflict`

## 📞 Contact

To report issues or suggestions, create an issue in the project repository.
