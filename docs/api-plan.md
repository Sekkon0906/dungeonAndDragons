# API REST

El inicio de sesión web se abre en `/oauth2/authorization/mesarol`.
Para escritura desde la sesión web, consulta `GET /api/csrf` y envía
el valor devuelto en la cabecera indicada. También puedes autenticar
la API con un JWT de acceso RSA del emisor configurado. El JWT debe
incluir audiencia, sujeto y correo verificado.

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/me` | Cuenta autenticada |
| GET | `/api/sessions?page=0` | Público |
| GET | `/api/sessions/{id}` | Público |
| POST | `/api/sessions` | Organizador |
| PUT | `/api/sessions/{id}` | Organizador propietario |
| POST | `/api/sessions/{id}/cancel` | Organizador propietario |
| POST | `/api/sessions/{id}/registrations` | Jugador |
| DELETE | `/api/sessions/{id}/registrations/me` | Jugador titular de la reserva |
| GET | `/api/me/registrations?page=0` | Jugador |
| GET | `/api/me/sessions?page=0` | Organizador |

El cuerpo de creación y edición contiene `title`, `description`,
`startsAt` en ISO 8601 con zona y `capacity` entre 1 y 20.
Las listas devuelven hasta 20 resultados por página. La reserva
usa la identidad autenticada y no recibe el ID del jugador en el cuerpo.

La API responde con 400 para datos inválidos, 401 para falta de acceso,
403 para permisos insuficientes, 404 para recursos inexistentes y
409 ante un cupo agotado, duplicado o una sesión cerrada.
