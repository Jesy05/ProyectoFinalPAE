package ni.edu.uam.proyectofinalpae.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

final class JsonRespuesta {
    static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private JsonRespuesta() {}

    static JsonElement leer(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) throw new IllegalArgumentException("Cuerpo vacío");
        return GSON.fromJson(cuerpo, JsonElement.class);
    }

    static JsonElement campo(JsonObject objeto, String nombre, boolean admiteNulo) {
        JsonElement valor = objeto.get(nombre);
        if (valor == null || (!admiteNulo && valor.isJsonNull())) {
            throw new IllegalArgumentException("Campo faltante o nulo: " + nombre);
        }
        return valor;
    }

    static String texto(JsonElement valor) {
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()
                || valor.getAsString().isBlank()) {
            throw new IllegalArgumentException("Se esperaba texto");
        }
        return valor.getAsString();
    }

    static java.math.BigDecimal numero(JsonElement valor) {
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Se esperaba un número");
        }
        return valor.getAsBigDecimal();
    }

    static int porcentaje(JsonElement valor) {
        int porcentaje = numero(valor).intValueExact();
        if (porcentaje < 0 || porcentaje > 100) throw new IllegalArgumentException("Porcentaje fuera de rango");
        return porcentaje;
    }
}
