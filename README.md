# Parental Controls

Adds a server-side per-player time limit.

<img src="src/main/resources/assets/parentalcontrols/icon.png" alt="Icon featuring a clock and a shield icon" width="128">

## Features

- Configurable time limit, kick message, warning messages, etc.
- Tick-based timer (lag spikes won't count toward elapsed time)
- Reset at midnight
- Possibility of stacking unused time (disabled by default)
- Up to two configurable warning stages when time is running low
- Warning messages are repeated briefly in the action bar and also sent once in chat so they are not lost when another mod overwrites the action bar

## Usage

The default time limit is 8 hours (`minutes_allowed: 480`). Players receive a first warning when their remaining time drops to the configured threshold (`warning_threshold_seconds: 300` or 5 minutes). Those, along with some other settings, can be changed in the `config/parentalcontrols.json` file. Settings can be reloaded on the fly with an operator-only command ([see below](#commands)).

### Second warning

A second warning is optional and disabled by default. It can be enabled with `second_warning_enabled`. The second threshold should normally be lower than the first one.

Example:

```json
{
  "warning_message": "§6⚠ Warning: You have %time% remaining before you're disconnected!",
  "warning_threshold_seconds": 600,
  "second_warning_enabled": true,
  "second_warning_message": "§c⚠ Last warning: You have %time% remaining before you're disconnected!",
  "second_warning_threshold_seconds": 180
}
```

Each warning stage triggers once per player/day. The action-bar copy is repeated for about 10 seconds. The same warning is also sent once as a normal system/chat message, which prevents the warning from being missed when another mod frequently updates the action bar.

### Time Stacking

Time stacking is disabled by default (`allow_time_stacking: false`). When enabled, any unused time from the daily allowance will be accumulated and carried over to the next day, up to a maximum of `max_stacked_hours` (default: 24 hours).
The time remaining is internally counted per-tick, but checks are performed every second for better performance (assuming that the server runs at 20 ticks per second). If your server runs at a different tick rate, please update the `check_interval_ticks` config value accordingly.

## Commands

- `/parental remaining`: As a player, query your time remaining
- `/parental reload`: (Operator-only) Reload the configuration file
- `/parental stacking`: (Operator-only) Show time stacking status and each player's accumulated time
