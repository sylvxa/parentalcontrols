package lol.sylvie.parental.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import lol.sylvie.parental.ParentalControls;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
            if (parsed == null) {
                return false;
            }

            INSTANCE = parsed;
            if (INSTANCE.playerAccumulatedTicks == null) {
                INSTANCE.playerAccumulatedTicks = new HashMap<>();
            }

            ParentalControls.updateTimeConstants();
            ParentalControls.loadAccumulatedTicksFromConfig();
            return true;
        } catch (FileNotFoundException exception) {
            ParentalControls.LOGGER.warn("Configuration file not found.");
            save();
            ParentalControls.updateTimeConstants();
        } catch (IOException | JsonSyntaxException exception) {
            ParentalControls.LOGGER.error("Couldn't load JSON configuration", exception);
        }
        return false;
    }

    @SerializedName("minutes_allowed")
    public float minutesAllowed = 60 * 8;

    @SerializedName("disconnect_message")
    public String disconnectMessage = "§cYou have reached your time limit for today.";

    @SerializedName("warning_message")
    public String warningMessage = "§6⚠ Warning: You have %time% remaining before you're disconnected!";

    @SerializedName("exclude_operators")
    public boolean excludeOperators = false;

    @SerializedName("allow_time_stacking")
    public boolean allowTimeStacking = false;

    @SerializedName("max_stacked_hours")
    public float maxStackedHours = 24.0f;

    @SerializedName("warning_threshold_seconds")
    public int warningThresholdSeconds = 300;

    @SerializedName("second_warning_enabled")
    public boolean secondWarningEnabled = false;

    @SerializedName("second_warning_message")
    public String secondWarningMessage = "§c⚠ Last warning: You have %time% remaining before you're disconnected!";

    @SerializedName("second_warning_threshold_seconds")
    public int secondWarningThresholdSeconds = 180;

    @SerializedName("check_interval_ticks")
    public int checkIntervalTicks = 20;

    @SerializedName("player_accumulated_ticks")
    public Map<UUID, Integer> playerAccumulatedTicks = new HashMap<>();
}
