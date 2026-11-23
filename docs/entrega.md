# Entrega y despliegue

El flujo se define en `.github/workflows/ci-cd.yml`. Los pull requests
compilan y ejecutan las pruebas con PostgreSQL. Los cambios en `main`
publican en GHCR una imagen etiquetada con el SHA del commit. Después,
el runner Windows actualiza Kubernetes por digest y verifica el estado.

## Preparar Kubernetes una vez

Usa un clúster con nodos Linux, almacenamiento dinámico y un endpoint
accesible para la aplicación. El runner se ejecuta en Windows y utiliza
`kubectl` para comunicarse con el clúster. El proveedor OIDC debe ser
accesible tanto por el navegador como por el pod de Spring Boot.

1. Configura en el proveedor un cliente confidencial con código de autorización,
   scopes `openid profile email` y retorno `https://TU_HOST/login/oauth2/code/mesarol`.
   Usa RS256 y una audiencia `mesarol` para los tokens de acceso de la API.
   Para acceso local con port-forward, registra el retorno de `localhost`.
2. Aplica `k8s/namespace.yaml`. En ese espacio crea el Secret `mesarol-config`
   con `DATABASE_PASSWORD`, `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`,
   `OIDC_AUDIENCE`, `MESAROL_ORGANIZERS` y `SESSION_COOKIE_SECURE=true` para
   HTTPS. Para una demostración local por HTTP, usa `false` en esta última variable.
   Puedes cargar los valores desde un archivo local sin seguimiento mediante
   `kubectl -n mesarol create secret generic mesarol-config --from-env-file=.env.k8s`.
3. Para una imagen privada, crea en ese espacio el Secret de registro
   `ghcr-pull` con un token con acceso de lectura al paquete. Si la imagen
   es pública, puedes omitir la referencia a ese Secret en el Deployment.
4. Aplica `k8s/database.yaml` y `k8s/service.yaml`. Después de la primera
   publicación a GHCR, instala el Deployment usando su imagen real.

```powershell
$image = 'ghcr.io/propietario/mesa-rol:SHA_DEL_COMMIT'
kubectl apply -f k8s/namespace.yaml
# Crea ahora mesarol-config y, para paquete privado, ghcr-pull.
kubectl apply -f k8s/database.yaml -f k8s/service.yaml
kubectl set image --local -f k8s/app.yaml "app=$image" -o yaml | kubectl apply -f -
kubectl -n mesarol rollout status deployment/mesarol --timeout=180s
```

Configura el acceso HTTPS mediante el ingress o proxy de tu clúster. Para
una demostración local, puedes mantener abierto
`kubectl -n mesarol port-forward svc/mesarol 8080:80` y usar
`http://localhost:8080/actuator/health`.

## Habilitar el runner Windows

En el repositorio, registra un runner propio con la etiqueta adicional
`mesarol-deploy`. Usa un entorno dedicado, con PowerShell 7 y `kubectl`.
La cuenta que ejecuta el runner debe tener un contexto Kubernetes configurado
y permisos de lectura y actualización del Deployment de MesaRol.

En las variables de Actions del repositorio, configura:

| Variable | Valor |
|---|---|
| `KUBE_CONTEXT` | Contexto disponible para la cuenta del runner |
| `MESAROL_HEALTH_URL` | URL completa a `/actuator/health` accesible desde Windows |
| `DEPLOY_ENABLED` | `true` una vez comprobados el clúster y el runner |

El job de Windows se omite hasta que se habilita el despliegue. Los PR se
ejecutan en runners hospedados por GitHub. Usa el runner Windows para el
repositorio privado de entrega y acepta cambios en `main` de integrantes
autorizados.

## Evidencias para presentar

1. Enlace al repositorio y a la ejecución que compila y prueba con PostgreSQL.
2. Etiqueta del commit y digest de la imagen en GHCR.
3. Job de Windows exitoso y versión del Deployment tras un cambio en `main`.
4. Demo de la directora publicando una partida, dos jugadores compitiendo
   por el último cupo y el cupo volviendo a estar disponible tras cancelar.
5. Cada integrante debe hospedar su copia en GitHub según la guía de la actividad.

Referencias operativas. [Publicación de imágenes con GitHub Actions](https://docs.github.com/en/actions/use-cases-and-examples/publishing-packages/publishing-docker-images) · [Runners propios](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/adding-self-hosted-runners) · [Keycloak en contenedores](https://www.keycloak.org/server/containers).
