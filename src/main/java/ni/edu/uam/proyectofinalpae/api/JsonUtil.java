package ni.edu.uam.proyectofinalpae.api;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonUtil {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private JsonUtil() {
    }

    public static String aJson(Object objeto) {
        return GSON.toJson(objeto);
    }
}
