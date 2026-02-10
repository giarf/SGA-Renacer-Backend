# SGA Renacer - Backend API

Sistema de Gestión Administrativa para Fundación Renacer.

## 🚀 Inicio Rápido

### Requisitos Previos

- **Scala**: 2.13.x
- **SBT**: 1.8.x o superior
- **PostgreSQL**: 14.x o superior
- **Java**: 11 o superior

### Instalación

```bash
# Clonar el repositorio
git clone <repository-url>
cd SGA-Renacer-Backend

# Compilar el proyecto
sbt compile

# Ejecutar el servidor
sbt run
```

El servidor estará disponible en `http://localhost:8080`

## 📋 Endpoints de la API

### Personas (CRUD)

#### Listar Personas
```http
GET /api/entidades?tipo=Persona&q=<búsqueda>
```

**Parámetros de Query:**
- `tipo` (opcional): Filtrar por tipo de entidad (`Persona` o `Institucion`)
- `q` (opcional): Término de búsqueda (busca en nombres, apellidos, RUT)

**Respuesta:**
```json
[
  {
    "id": 1,
    "identificador": "12.345.678-9",
    "nombreCompleto": "Juan Pérez",
    "tipoEntidad": "Persona",
    "correo": "juan@example.com",
    "telefono": "+56912345678",
    "direccion": "Calle Falsa 123",
    "comuna": "Quillota",
    "genero": "Masculino"
  }
]
```

#### Obtener Persona por ID
```http
GET /api/personas?id=<id>
```

**Respuesta:**
```json
{
  "id": 1,
  "rut": "12.345.678-9",
  "tipoEntidad": "Persona",
  "telefono": "+56912345678",
  "correo": "juan@example.com",
  "direccion": "Calle Falsa 123",
  "comuna": "Quillota",
  "nombres": "Juan",
  "apellidos": "Pérez",
  "genero": "Masculino"
}
```

#### Crear Persona
```http
POST /api/entidades/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "rut": "11.222.333-4",
  "tipoEntidad": "Persona",
  "telefono": "+56987654321",
  "correo": "maria@example.com",
  "direccion": "Av. Principal 456",
  "comuna": "Quillota",
  "nombres": "María",
  "apellidos": "González",
  "genero": "Femenino"
}
```

**Respuesta:**
```json
{
  "mensaje": "Persona creada exitosamente",
  "id": 2
}
```

#### Actualizar Persona
```http
PUT /api/personas/editar
Content-Type: application/json
```

**Body:**
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
  "apellidos": "Pérez González",
  "genero": "Masculino"
}
```

**Respuesta:**
```json
{
  "mensaje": "Persona actualizada exitosamente",
  "filasActualizadas": 2
}
```

### Donaciones

#### Registrar Donación Pecuniaria
```http
POST /api/ingresos/donacion
Content-Type: application/json
```

**Body:**
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
    "metodoPago": "Transferencia"
  }
}
```

#### Registrar Donación de Bienes
```http
POST /api/ingresos/donacion-bienes
Content-Type: application/json
```

**Body:**
```json
{
  "ingreso": {
    "id": 0,
    "origenEntidadId": 2,
    "responsableInternoId": 5,
    "montoTotal": 40000,
    "tipoTransaccion": "Donacion",
    "estado": "Cerrado"
  },
  "donacion": {
    "ingresoId": 0,
    "numeroCertificado": "DON-BIEN-2026-001",
    "propositoEspecifico": "Campaña Invierno"
  },
  "items": [
    {
      "id": 0,
      "itemCatalogoId": 0,
      "nombre": "Frazadas Térmicas",
      "cantidad": 10,
      "precio": 4000,
      "categoria": "Textiles",
      "unidad": "Unidad"
    }
  ]
}
```

**Características:**
- **Transaccional**: Todas las operaciones se ejecutan en una sola transacción
- **Upsert de ítems**: Busca por `itemCatalogoId` o `nombre`, crea si no existe
- **Cálculo PPP**: Calcula automáticamente el Precio Ponderado Promedio
- **Bloqueo optimista**: Usa `FOR UPDATE` para prevenir condiciones de carrera

### Catálogo e Inventario

#### Buscar Ítems
```http
GET /api/catalogo/buscar?q=<término>
```

**Búsqueda:**
- Accent-insensitive (ignora tildes)
- Partial matching en nombre y categoría
- Máximo 15 resultados

**Respuesta:**
```json
[
  {
    "id": 1,
    "nombre": "Frazadas Térmicas",
    "categoria": "Textiles",
    "stock": 50,
    "unidadMedida": "Unidad",
    "precioPonderado": 4500.00
  }
]
```

#### Listar Categorías y Unidades
```http
GET /api/catalogo/utilitarios
```

**Respuesta:**
```json
{
  "categorias": ["Alimentos", "Textiles", "Higiene", "Educación"],
  "unidades": ["Unidad", "Kg", "Litro", "Caja", "Paquete"]
}
```

### Solicitudes

#### Crear Solicitud de Material
```http
POST /api/solicitudes
Content-Type: application/json
```

**Body:**
```json
{
  "solicitud": {
    "id": 0,
    "beneficiarioId": 10,
    "responsableId": 5,
    "fechaSolicitud": "2026-02-09",
    "estado": "Pendiente"
  },
  "items": [
    {
      "id": 0,
      "solicitudId": 0,
      "itemCatalogoId": 1,
      "cantidadSolicitada": 5
    }
  ]
}
```

### Validaciones

#### Verificar RUT Existente
```http
GET /api/entidades/existe-rut?rut=12.345.678-9
```

**Respuesta:**
```json
{
  "existe": true
}
```

## 🔧 Características Técnicas

### CORS
El servidor está configurado para permitir peticiones desde `http://localhost:5173` con los siguientes headers:
- `Access-Control-Allow-Origin`
- `Access-Control-Allow-Methods`: GET, POST, PUT, DELETE, OPTIONS
- `Access-Control-Allow-Headers`: Content-Type, Authorization

### Búsqueda Avanzada
- **Accent-insensitive**: Utiliza la extensión `unaccent` de PostgreSQL
- **Ranking de relevancia**: Prioriza coincidencias al inicio de nombres
- **Multi-campo**: Busca en nombres, apellidos, razón social y RUT simultáneamente

### Base de Datos

**Configuración requerida:**
```sql
-- Habilitar extensión unaccent para búsquedas sin acentos
CREATE EXTENSION IF NOT EXISTS unaccent;
```

### Stack Tecnológico

- **Framework Web**: Cask
- **ORM**: Quill con PostgreSQL
- **JSON**: Play JSON
- **Base de Datos**: PostgreSQL 14+

## 📝 Convenciones

### Formato de Fechas
- `LocalDate`: Formato ISO 8601 (YYYY-MM-DD)
- `LocalDateTime`: Formato ISO 8601 (YYYY-MM-DDTHH:mm:ss)

### Formato de RUT
Los RUTs se almacenan con puntos y guión: `12.345.678-9`

### Estados de Transacciones
- `Pendiente`: Transacción iniciada pero no completada
- `Cerrado`: Transacción completada y confirmada
- `Anulado`: Transacción cancelada

### Tipos de Entidad
- `Persona`: Persona Natural
- `Institucion`: Persona Jurídica (Empresas, ONG, etc.)

## 🐛 Manejo de Errores

La API retorna códigos de estado HTTP estándar:

- `200 OK`: Operación exitosa
- `201 Created`: Recurso creado exitosamente
- `204 No Content`: Operación exitosa sin contenido de respuesta
- `400 Bad Request`: Error en la validación de datos
- `404 Not Found`: Recurso no encontrado
- `500 Internal Server Error`: Error del servidor

**Formato de errores:**
```json
{
  "error": "Descripción del error"
}
```

## 📦 Estructura del Proyecto

```
src/main/scala/cl/familiarenacer/sga/
├── api/
│   └── SgaApiApp.scala          # Endpoints y rutas
├── modelos/
│   ├── Base.scala               # Modelos base (Entidad, Familia, etc.)
│   ├── Personas.scala           # PersonaNatural, Institución, Roles
│   ├── Inventario.scala         # ItemCatalogo
│   ├── Ingresos.scala           # IngresoRecurso, Donaciones
│   └── Dto.scala                # DTOs para respuestas
└── repositorios/
    ├── DB.scala                 # Configuración de DB
    ├── EntidadRepository.scala  # Operaciones de Entidades
    ├── InventarioRepository.scala # Operaciones de Inventario
    └── DonacionRepository.scala # Operaciones de Donaciones
```

## 🔐 Seguridad

- Validación de RUT con formato chileno
- Transacciones atómicas para operaciones críticas
- Bloqueo pesimista (`FOR UPDATE`) en actualizaciones de inventario
- Validación de integridad referencial en base de datos

## 🚦 Estado del Proyecto

✅ **Funcionalidades Implementadas:**
- CRUD completo de Personas
- Registro de Donaciones (Pecuniarias y Bienes)
- Búsqueda avanzada de Entidades con ranking de relevancia
- Gestión de Inventario con PPP
- Solicitudes de Material
- CORS configurado para frontend Vue

## 📞 Contacto

Para reportar problemas o sugerencias, crear un issue en el repositorio del proyecto.
