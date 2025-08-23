# Parental Controls

Adds a server-side per-player time limit.

<img src="src/main/resources/assets/parentalcontrols/icon.png" alt="Icon featuring a clock and a shield icon" width="128">

## Features

- Configurable time limit, kick message, warning message, etc.
- Tick-based timer (lag spikes won't count toward elapsed time)
- Reset at midnight
- Possibility of stacking unused time (disabled by default)
- Warning system that alerts players when time is running low

## Usage

The default time limit is 8 hours (`minutes_allowed: 480`). Players receive a warning message when their remaining time drops to the configured threshold (`warning_threshold_seconds: 300` or 5 minutes). Those, along with some other settings, can be changed in the `config/parentalcontrols.json` file. Settings can be reloaded on the fly with an operator-only command ([see below](#commands)). 

### Time Stacking

Time stacking is disabled by default (`allow_time_stacking: false`). When enabled, any unused time from the daily allowance will be accumulated and carried over to the next day, up to a maximum of `max_stacked_hours` (default: 24 hours).
The time remaining is internally counted per-tick, but checks are performed every second for better performance (assuming that the server runs at 20 ticks per second). If your server runs at a different tick rate, please update the `check_interval_ticks` config value accordingly.

## Commands

- `/parental remaining`: As a player, query your time remaining
- `/parental reload`: (Operator-only) Reload the configuration file
- `/parental stacking`: (Operator-only) Show time stacking status and each player's accumulated time
