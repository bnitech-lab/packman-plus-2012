# PackMan plus (2012)

A maze game in the style of Pac-Man, written in Java with Swing. You move the hero through a 21 x 27 maze with the arrow keys and eat every coin while robots roam the corridors. The robots pick random turns and never reverse, except when you are less than five columns or rows away in that direction. Eat all the coins to win; touching a robot costs a life, and being caught with no spare lives left ends the game. Four power-ups sit in the maze, each usable once per game:

| Power-up | Effect in the code |
|---|---|
| Apple | for 10 seconds you also eat coins up to two cells away |
| Shoe | for 8 seconds you move about three times as fast |
| Spinning top | for 10 seconds you are bigger and faster and can catch robots (200 points; the robot goes back to the centre) |
| Heart | one extra life and 10 seconds of being untouchable |

A coin is worth 10 points. Before a game you pick a difficulty with the arrows next to the START button: easy (4 robots, 4 spare lives), normal (5 faster robots, 3 spare lives) or hard (6 even faster robots, 2 spare lives). The right-hand panel shows the score, the active power-up with a short description, the play time and the remaining lives; after a win or a loss, REGAME returns to the title screen. The maze layout is a hand-made character array in the source, and the side tunnel wraps around. The in-game text is in Korean.

## Background

- Year: 2012.
- Made alone by Dawn at university, as a personal project that grew out of a Java course.
- The window title credits BNItech as the maker, and the title screen reads BNI Tech.

## Run it

Any JDK 8 or newer should work; this release was compiled and checked with JDK 21. Run the commands from the repository root, because the game loads its pictures from `./imgs/`.

```sh
javac -encoding UTF-8 -d out src/PackMan_plus.java
java -cp out PackMan_plus
```

Click START, then use the arrow keys. The code asks for some Windows fonts by name (Berlin Sans FB Demi, AR DESTINE, AR DELANEY, HY엽서L, HY태백B); where they are missing, Java uses its default font, so the panel text looks plainer but works. The Korean text needs a font with Korean glyphs, which Windows and macOS have by default.

## Changes for this public release

- `src/PackMan_plus.java` was converted from the Korean CP949 encoding to UTF-8, and the window title typo "Puls" was corrected to "Plus". No other code was changed.
- Every image in `imgs/` was replaced with a new drawing. The originals included third-party artwork: the PAC-MAN logo and the ghost characters, which belong to Bandai Namco, plus several pictures of unknown origin (an Android robot, the power-up icons, the maze background and the READY / GO / VICTORY / LOSE captions). The new images are drawn in code by [`tools/DrawArt.java`](tools/DrawArt.java), keep each file's name, format and pixel size so the game loads them unchanged, and can be regenerated with JDK 11 or newer:

  ```sh
  java tools/DrawArt.java
  ```

  The robots replace the ghosts, a spinning top replaces the Android robot as the "power-up toy", and the maze picture is generated from the map array in the source so the walls line up with the coins.
- Left out: the Eclipse project files (`.project`, `.classpath`), which are not needed to build, and the old Eclipse `.gitignore`, replaced by a shorter one.

## Licence

MIT, see [LICENSE](LICENSE). The images and `tools/DrawArt.java` were added for this release and are under the same licence.

## Status

Finished: the game can be played from the title screen to a win or a loss on all three difficulty levels.
