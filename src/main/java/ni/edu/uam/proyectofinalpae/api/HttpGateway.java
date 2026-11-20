package ni.edu.uam.proyectofinalpae.api;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface HttpGateway {
    String get(URI uri) throws IOException, InterruptedException;
}
