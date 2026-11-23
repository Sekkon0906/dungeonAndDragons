# MesaRol · La Linterna Errante

MesaRol organiza partidas de rol desde una taberna 3D. Una directora publica una
sesión y los jugadores reservan cupos. La Cripta de los ecos ofrece un encuentro
táctico individual de práctica, con movimiento por casillas y una acción por
turno. La práctica se ejecuta en el navegador y no modifica las reservas.

## Demostración local

Requisitos: JDK 21 y Docker con Compose. Desde PowerShell en la raíz:

```powershell
Copy-Item .env.example .env
# Edita .env y elige claves locales antes del primer arranque.
docker compose up -d
Invoke-RestMethod http://localhost:8180/realms/mesarol/.well-known/openid-configuration
Get-Content .env | Where-Object { $_ -match '^[A-Z_][A-Z0-9_]*=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
}
$env:SPRING_PROFILES_ACTIVE = 'oidc'
.\mvnw.cmd spring-boot:run
```

Si el proveedor aún arranca, vuelve a consultar el endpoint de descubrimiento
antes de iniciar Spring Boot. Abre [MesaRol](http://localhost:8080/) usando
`localhost`, que coincide con la URL de retorno registrada.

| Cuenta local | Rol | Clave en `.env` |
|---|---|---|
| `directora` | Organizadora | `MESAROL_DEMO_ORGANIZER_PASSWORD` |
| `jugador` | Jugador | `MESAROL_DEMO_PLAYER_PASSWORD` |
| `otro` | Jugador | `MESAROL_DEMO_OTHER_PASSWORD` |

1. Entra como directora. Abre **Mi libro → Nueva partida** y publica una sesión futura con un cupo.
2. En otro perfil del navegador entra como jugador. Haz clic en el marcador del tablón. El personaje caminará hasta él y abrirá las partidas.
3. Abre la sesión y reserva. En **Mi libro** puedes liberar el cupo. Usa un tercer perfil para comprobar que otro jugador solo consigue el cupo cuando queda libre.
4. Desde **Cripta** puedes probar el encuentro táctico. En la taberna también funcionan WASD, flechas y E.

Las cuentas de Compose son de prueba y se importan en el primer arranque.
Para un proveedor real, registra y verifica los correos en el proveedor. MesaRol
crea la cuenta local al primer acceso y concede el rol de organizador según
`MESAROL_ORGANIZERS`, una lista de correos verificados separados por coma.

## Pruebas

`mvnw.cmd -B verify` ejecuta las pruebas con H2. Para PostgreSQL, proporciona
`TEST_DATABASE_URL`, `TEST_DATABASE_USER` y `TEST_DATABASE_PASSWORD` de una base
exclusiva para pruebas. La suite modifica y elimina datos en esa base.
GitHub Actions levanta PostgreSQL 17 para ejecutar la misma suite.

## Cómo funciona

- El navegador sirve Three.js y los paneles desde el mismo origen que la API. La taberna ofrece una ruta por casillas que evita los muebles. Los paneles también se pueden abrir desde el menú.
- Spring Boot expone la API REST. PostgreSQL conserva las cuentas, sesiones e inscripciones y Flyway aplica las migraciones.
- El inicio de sesión usa OAuth 2.0/OpenID Connect con código de autorización. La sesión web protege las solicitudes de escritura con CSRF. La API también acepta JWT RSA del emisor configurado con la audiencia de MesaRol.
- Las reservas bloquean la fila de la sesión dentro de la transacción antes de comprobar cupos. Una restricción de la base impide duplicados por jugador y sesión.

Consulta el [contrato REST](docs/api-plan.md) y la [guía de la entrega y despliegue](docs/entrega.md).
