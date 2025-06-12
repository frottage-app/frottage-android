#!/usr/bin/env bash

# Script to select and launch an Android Virtual Device (AVD) with fzf

export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export ANDROID_HOME="$HOME/Android/Sdk" # Rely on devbox environment
EMULATOR_FULL_CMD="$ANDROID_SDK_ROOT/emulator/emulator"

echo "Time to pick your virtual buddy, chief! Let the frottage commence!"

# List AVDs and let the user select one
SELECTED_AVD=$($EMULATOR_FULL_CMD -list-avds | fzf --height 40% --reverse --prompt="Select an AVD to frottage with: ")

# Check if an AVD was selected
if [ -z "$SELECTED_AVD" ]; then
    echo "No AVD selected. No frottage for you today! Peace out!"
    exit 1
fi

echo "Alright, launching '$SELECTED_AVD'! Get ready for some digital frottage!"

# Launch the emulator in the foreground
$EMULATOR_FULL_CMD -avd "$SELECTED_AVD" 