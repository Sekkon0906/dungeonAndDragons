# Verificación de MesaRol

La entrega se probó con PostgreSQL 17.12 en Windows. La suite incluye
la carrera por el último cupo y controles de identidad, permisos y CSRF.

En Edge se abrió la taberna, se llegó al tablón con un clic y se
inició sesión en Keycloak con una organizadora y dos jugadores. Se
publicó y editó una partida, se enviaron dos reservas simultáneas,
se liberó el cupo y se canceló la partida. También se ejecutó un turno
de práctica en la cripta.

La [guía de entrega](entrega.md) indica cómo reproducir y evidenciar
el despliegue en el clúster desde un runner Windows.

## Registro inicial con H2

Fecha: 23 de noviembre de 2026, zona America/Bogota.

Comando ejecutado: `mvnw.cmd -B verify`.

Resultado: **BUILD SUCCESS**. 11 pruebas ejecutadas, 0 fallos, 0 errores,
0 omitidas. Se generó el JAR ejecutable en `target/`.

Entorno de verificación: Windows, JDK 23.0.1, compilación con objetivo Java 21,
Maven 3.10.1 mediante wrapper, Spring Boot 4.2.0.

Las pruebas cargaron el contexto Spring, ejecutaron la migración Flyway y validaron
el esquema JPA sobre H2 en modo PostgreSQL. Incluyeron duplicados, cupos completos,
cancelación y nueva reserva, sesión cancelada, permisos para cancelar, rol de jugador,
dos solicitudes simultáneas por un cupo y límites de fecha/capacidad.

No se verificó ejecución con JDK 21 directamente ni con PostgreSQL real.
No se encontró Docker disponible como comando en este entorno, por lo que no se
ejecutó Compose. Tampoco se desplegó la aplicación ni se configuraron recursos remotos.

La prueba de concurrencia confirma el comportamiento en H2; la validación con
PostgreSQL queda pendiente antes de considerar completa la persistencia del MVP.
