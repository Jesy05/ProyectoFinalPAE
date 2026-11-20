# Verificacion de la entrega de Jose

- Rama: `feature/ficha-cultivo`
- Base: `master` en `f903ac3`
- Funcionalidad: ficha de cultivo con Wikipedia REST.
- Pruebas: `mvnw.cmd -q clean test`, sin depender de Internet.
- Evidencia: `evidencias/03-plantas-sensores.png`.
- Fabiola debe reutilizar `HttpGateway` y `HttpClientGateway` para Open-Meteo.

Comandos de auditoria ejecutados antes del Pull Request:

```powershell
./mvnw.cmd -q clean test
./mvnw.cmd -q -DskipTests compile
git diff --check f903ac3..HEAD
git rev-list --count f903ac3..HEAD
```
