# Ficha de cultivos con Wikipedia

La pantalla Plantas consulta el resumen de un cultivo en la API REST de Wikipedia:

`https://es.wikipedia.org/api/rest_v1/page/summary/{nombre}`

`WikipediaApiRepository` construye la URL y parsea el JSON con Gson. `WikipediaService`
valida la consulta y `PlantaController` ejecuta la llamada dentro de un `Task` para no
bloquear el hilo JavaFX. `HttpClientGateway` aplica timeout, valida el estado HTTP y envia
un User-Agent propio.

Las pruebas usan un `HttpGateway` falso, por lo que `mvn test` no necesita Internet.
Open-Meteo, la cache HashMap y `quitarModulo` pertenecen a la segunda guia de Fabiola.

Para una demostracion usar `Albahaca` o el nombre cientifico registrado en el cultivo.
