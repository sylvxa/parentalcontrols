package lol.sylvie.parental.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import lol.sylvie.parental.ParentalControls;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;

public class Configuration {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static final File FILE = FabricLoader
            .getInstance()
            .getConfigDir()
            .resolve(ParentalControls.MOD_ID + ".json")
            .toFile();

    public static Configuration INSTANCE = new Configuration();

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(INSTANCE, Configuration.class, writer);
        } catch (IOException | JsonSyntaxException exception) {
            ParentalControls.LOGGER.error("Couldn't create JSON configuration", exception);
        }
    }

    public static boolean load() {
        try (FileReader reader = new FileReader(FILE)) {
            Configuration parsed = GSON.fromJson(reader, Configuration.class);
            if (parsed == null) return false;
            INSTANCE = parsed;
            return true;
        } catch (FileNotFoundException exception) {
            ParentalControls.LOGGER.warn("Configuration file not found.");
            save();
        } catch (IOException | JsonSyntaxException exception) {
            ParentalControls.LOGGER.error("Couldn't load JSON configuration", exception);
        }
        return false;
    }

    // Actual settings
    @SerializedName("minutes_allowed")
    public float minutesAllowed = 60 * 8;

    @SerializedName("disconnect_message")
    public String disconnectMessage = "§cYou have reached your time limit for today.";

    @SerializedName("exclude_operators")
    public boolean excludeOperators = false;

}
