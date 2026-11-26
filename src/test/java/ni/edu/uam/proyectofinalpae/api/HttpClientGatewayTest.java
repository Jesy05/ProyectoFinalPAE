package ni.edu.uam.proyectofinalpae.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientGatewayTest {
    @Test
    void aceptaExitoYConservaCodigosDeError() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            int codigo = Integer.parseInt(intercambio.getRequestURI().getPath().substring(1));
            ApiServidor.responder(intercambio, codigo, "{}");
        });
        servidor.start();
        try {
            String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
            var gateway = new HttpClientGateway();
            assertEquals("{}", gateway.get(URI.create(base + "/200")));
            for (int codigo : List.of(404, 429, 500)) {
                var error = assertThrows(HttpStatusException.class, () -> gateway.get(URI.create(base + "/" + codigo)));
                assertEquals(codigo, error.statusCode());
            }
        } finally {
            servidor.stop(0);
        }
    }
}
