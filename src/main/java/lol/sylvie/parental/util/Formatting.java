package lol.sylvie.parental.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.NotNull;

public class Formatting {
    public static @NotNull String ticksAsHours(int ticks) {
        return DurationFormatUtils.formatDuration((ticks / 20) * 1000L, "HH:mm:ss");
    }

    public static @NotNull String ticksAsWords(int ticks) {
        int seconds = ticks / 20;
        int minutesLeft = seconds / 60;
        int secondsLeft = seconds % 60;

        String timeMessage;
        if (minutesLeft > 0) {
            if (secondsLeft > 0) {
                timeMessage = minutesLeft + " minute" + (minutesLeft == 1 ? "" : "s") +
                        " and " + secondsLeft + " second" + (secondsLeft == 1 ? "" : "s");
            } else {
                timeMessage = minutesLeft + " minute" + (minutesLeft == 1 ? "" : "s");
            }
        } else {
            timeMessage = secondsLeft + " second" + (secondsLeft == 1 ? "" : "s");
        }
        return timeMessage;
    }
}
