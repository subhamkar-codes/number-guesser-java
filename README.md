# Number Guesser (Java)

A number guessing game built with **Java Swing**. It has a dark theme, three difficulty levels, limited attempts, hot/cold hints and a saved score history.

The computer picks a secret number and you try to find it using the hints.

<img width="1920" height="1057" alt="Number Guesser 10_18_2026 9_37_25 AM" src="https://github.com/user-attachments/assets/815f9aa0-4e04-4589-9102-069e0f719cc7" />


## Features

- Three difficulty levels: Easy (1-50, 10 attempts), Medium (1-100, 8 attempts), Hard (1-500, 12 attempts)
- Higher / lower hint after every guess
- Hot / Warm / Cold hint that shows how close you are
- Attempts-left bar that changes from green to orange to red
- Input validation: letters and out-of-range numbers show a message and do not use up an attempt
- Guess list for the current game, colored by result
- Saved score history that survives closing the game
- Best score for each difficulty
- Custom dark theme with rounded buttons and cards, built with plain Swing (no external libraries)

## How to run

Requires JDK 8 or newer. The file name must stay `Number_guessing_game_optimized.java` because the public class has the same name.

```
javac Number_guessing_game_optimized.java
java Number_guessing_game_optimized
```

In IntelliJ IDEA, put the file in `src` and run it. If you see odd `NoClassDefFoundError` messages after renaming classes, use **Build > Rebuild Project**.

## How to play

1. Pick a difficulty with the Easy / Medium / Hard buttons.
2. Type a number and press Enter (or click **Guess**).
3. Use the hints:
   - **Higher / Lower** tells you which direction to go.
   - **Hot / Warm / Cold** tells you how close you are. Within 5% of the range is Hot, within 15% is Warm, anything further is Cold.
4. The bar under the input shows how many attempts you have left. Run out and you lose.
5. Click **Scores** to see your total games, win rate, best score per difficulty and your last 50 games.
6. Click **New Game** to start again.

## Score history

Every finished game is saved as one line in `number_guesser_scores.csv` in your user folder (for example `C:\Users\<name>\` on Windows). It's plain text and looks like this:

```
2026-10-18 14:32,MEDIUM,WIN,5
```

The **Clear history** button in the Scores window deletes the file after a second confirming click.

## Code structure

| Class | Responsibility |
|---|---|
| `Difficulty` | The three levels: label, max number, allowed attempts |
| `GameLogic` | Pure game rules: secret number, guess checking, hot/cold, attempt tracking. No printing, no UI |
| `ScoreRecord` | One finished game, and how it is written to / read from the save file |
| `ScoreBoard` | Loads and saves the score file, calculates best scores and win rate |
| `RoundedButton`, `RoundedPanel`, `AttemptsBar` | Custom-painted UI pieces for the dark theme |
| `GameWindow` | The window: layout, events, showing hints, input validation, Scores pop-up |
| `Number_guessing_game_optimized` | Contains `main()` and launches the window |

Keeping the rules (`GameLogic`) separate from the window (`GameWindow`) means the display can change later without touching the logic.

## Known limitations

- Scores have no player names, and they are stored per computer user account.
- Only the last 50 games are shown in the Scores window (all games stay in the file).

## Ideas for later

- Player names and a top-5 leaderboard
- Timer, and time shown in the score history
- Reverse mode: you think of a number and the computer guesses it using binary search
- Light/dark theme toggle

## What I learned

- Object-oriented structure in Java: classes, enums, encapsulation
- Building a GUI with Swing, handling events, and custom painting
- Validating user input instead of trusting it
- Reading and writing files, and handling damaged data safely
- Separating game logic from the user interface

## Author

Subham
