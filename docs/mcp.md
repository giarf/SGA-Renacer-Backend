# Servidor MCP de campañas

El backend publica un servidor [MCP](https://modelcontextprotocol.io) **de solo lectura** con las campañas,
para consultarlas desde un asistente (Claude, ChatGPT, Cursor, etc.).

- **URL:** `https://<host del API>/mcp` (p. ej. `https://api.familiarenacer.cl/mcp`)
- **Transporte:** Streamable HTTP, protocolo `2025-06-18` (acepta también `2025-03-26` y `2024-11-05`).
  Solo `POST` con respuestas `application/json`; no hay SSE (`GET /mcp` responde 405).
- **Acceso:** el token de Authentik del usuario, igual que la web. Exige grupo `renacer-miembros` o `renacer-admin`.

## Conectar un cliente

Basta con la URL del servidor: `https://api.familiarenacer.cl/mcp`. No hace falta escribir client_id
(sirve igual para el conector de Orca, OpenCode, Claude Code y cualquier cliente MCP con OAuth).

Cómo se descubre todo:

1. `POST /mcp` sin token responde `401` con
   `WWW-Authenticate: Bearer resource_metadata="https://api.familiarenacer.cl/.well-known/oauth-protected-resource/mcp"`.
2. Ese documento (RFC 9728) dice que el servidor de autorización es `https://api.familiarenacer.cl`.
3. `GET /.well-known/oauth-authorization-server` (también `/.well-known/openid-configuration`) devuelve los
   endpoints de **Authentik** (`authorize`, `token`, `jwks`, `revoke`, `userinfo`), leídos de su
   `openid-configuration` y guardados en caché 30 minutos, más `registration_endpoint` = `/oauth/register`.
4. `POST /oauth/register` (RFC 7591) es un registro **falso**: no guarda nada y siempre responde `201` con
   `client_id` = `sga-renacer-frontend` (el cliente público de la web, sin secreto, PKCE S256). Solo acepta
   `redirect_uris` loopback (`http://127.0.0.1:<puerto>/...` o `http://localhost:<puerto>/...`); otras dan
   `400 invalid_redirect_uri`.
5. El cliente abre el login de Authentik, recibe el código en su redirect local y lo canjea en el token
   endpoint de Authentik. El backend valida el token igual que el de la web.

Sobre `issuer`: los metadatos dicen `issuer` = `https://api.familiarenacer.cl` (la base del backend), no el
de Authentik, porque el SDK de MCP (el que usa OpenCode 2.x) exige que coincida con la URL de
`authorization_servers`. No rompe el flujo porque Authentik no envía `iss` en la redirección de vuelta y el
canje del código no lo revisa. El emisor real queda informativo en `authentik_issuer`; el backend sigue
validando que los tokens vengan de `AUTHENTIK_ISSUER`.

**Una vez, en Authentik** (proveedor `sga-renacer`):

- En *Redirect URIs* agregar una entrada **regex**: `http://(127\.0\.0\.1|localhost):\d+/.*`. Los clientes
  eligen puerto y ruta (OpenCode usa `http://127.0.0.1:19876/mcp/oauth/callback`, otros `/callback`).
- Verificar que el scope `offline_access` esté en el proveedor, para que el cliente reciba refresh token.

Si un cliente sí permite escribir client_id, también funciona con `sga-renacer-frontend` y la misma redirect.

| Variable | Valor por defecto | Uso |
|----------|-------------------|-----|
| `MCP_PUBLIC_URL` | `https://api.familiarenacer.cl/mcp` | URL pública exacta de `/mcp` que va en `resource` y en `WWW-Authenticate`; su origen es también el `issuer` y la base de `/oauth/register`. En producción no hace falta definirla; sirve para local u otro dominio |

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

Código: `mcp/ServidorMcp.scala` (JSON-RPC, instrucciones, metadatos OAuth), `mcp/AutorizacionMcp.scala` (fachada de autorización y registro), `mcp/HerramientasCampanas.scala`
(herramientas) y `api/routes/McpRoutes.scala`. Pruebas: `McpSpec`.
