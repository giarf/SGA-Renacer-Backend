# Servidor MCP de campañas

El backend publica un servidor [MCP](https://modelcontextprotocol.io) **de solo lectura** con las campañas,
para consultarlas desde un asistente (Claude, ChatGPT, Cursor, etc.).

- **URL:** `https://<host del API>/mcp` (p. ej. `https://api.familiarenacer.cl/mcp`)
- **Transporte:** Streamable HTTP, protocolo `2025-06-18` (acepta también `2025-03-26` y `2024-11-05`).
  Solo `POST` con respuestas `application/json`; no hay SSE (`GET /mcp` responde 405).
- **Acceso:** el token de Authentik del usuario, igual que la web. Exige grupo `renacer-miembros` o `renacer-admin`.

## Conectar un cliente

El cliente descubre la autorización solo: `POST /mcp` sin token responde `401` con
`WWW-Authenticate: Bearer resource_metadata=".../.well-known/oauth-protected-resource/mcp"`, y ese documento
(público, RFC 9728) apunta al emisor de Authentik.

Authentik no tiene registro dinámico de clientes, así que en el cliente MCP hay que indicar a mano:

| Dato | Valor |
|------|-------|
| URL del servidor | `https://api.familiarenacer.cl/mcp` |
| Client ID | `sga-renacer-frontend` (el mismo de la web; sin secreto, con PKCE) |
| Redirect URI | `http://127.0.0.1:19876/callback` |
| Scopes | `openid profile email offline_access` |

**Una vez, en Authentik:** agregar `http://127.0.0.1:19876/callback` a las *Redirect URIs* del proveedor
`sga-renacer` (modo *strict*), y verificar que el scope `offline_access` esté habilitado para que el cliente
reciba refresh token y no pida iniciar sesión cada vez que vence el token.

Ejemplo con Claude Code (revisa `claude mcp add --help` en tu versión):

```sh
claude mcp add --transport http sga-campanas https://api.familiarenacer.cl/mcp \
  --client-id sga-renacer-frontend --callback-port 19876
```

| Variable | Valor por defecto | Uso |
|----------|-------------------|-----|
| `MCP_PUBLIC_URL` | `https://api.familiarenacer.cl/mcp` | URL pública exacta de `/mcp` que va en `resource` y en `WWW-Authenticate`. En producción no hace falta definirla; sirve para local u otro dominio |

## Herramientas

Todas son de solo lectura (`readOnlyHint: true`); no hay herramientas que escriban o borren.

| Herramienta | Parámetros | Devuelve |
|-------------|-----------|----------|
| `listar_campanas` | `estado?` (`borrador`/`activa`/`cerrada`), `texto?` | Campañas con id, fecha, estado, total de participantes y sin padrino |
| `ver_campana` | `id`, `buscar?`, `sin_padrino?`, `columnas?` (nombres), `limite?` (50, máx. 200), `offset?` | Columnas (nombre, tipo, dato de ficha) y participantes con beneficiario, padrino, gestor y valores por **nombre** de columna; `totalFiltrados` y `hayMas` |
| `resumen_campana` | `id` | Totales con/sin padrino, padrinos con más ahijados, ahijados por gestor y, por columna, celdas llenas/marcadas/vacías o mensajes enviados/pendientes |
| `buscar_en_campanas` | `texto` | Personas cuyo nombre coincide, con cada campaña en que aparecen y su rol (beneficiario o padrino) |

Las búsquedas no distinguen mayúsculas ni acentos. Los errores de uso (campaña inexistente, columna que no existe)
vuelven como resultado con `isError: true`.

## Probar en local

Sin base de datos, con campañas en memoria y sin autenticación:

```sh
AUTH_MODE=off MCP_PUBLIC_URL=http://localhost:8080/mcp sbt "Test/runMain cl.familiarenacer.sga.McpLocal"
curl -s localhost:8080/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"resumen_campana","arguments":{"id":1}}}'
```

Código: `mcp/ServidorMcp.scala` (JSON-RPC, instrucciones, metadatos OAuth), `mcp/HerramientasCampanas.scala`
(herramientas) y `api/routes/McpRoutes.scala`. Pruebas: `McpSpec`.
