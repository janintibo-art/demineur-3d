# Notes de portage Web → Kotlin

## Correspondance des concepts

| Web (JS) | Android (Kotlin) |
|---|---|
| `mineMap[l][r][c]`, `revealed`, `flagged`, `questioned` (4 tableaux) | `GameEngine` : un tableau `mine` + un enum `CellState` par case |
| `placeMines(excludeL, excludeR, excludeC)` | `GameEngine.placeMines(exclude: Position)` |
| `revealCell` récursif | `floodReveal` **itératif** (pile) — évite le débordement de pile sur la grille Géant 24×24×3 |
| `handleChordClick` | `GameEngine.chord()` |
| `localStorage` | `SharedPreferences` via `StatsRepository` |
| `setInterval` (timer) | Coroutine dans le `GameViewModel` |
| Transform CSS (zoom/pan) | `detectTransformGestures` + `graphicsLayer` |
| DOM + classes CSS | Jetpack Compose (recomposition via `boardVersion`) |

## Choix d'architecture

- **Moteur pur** : `GameEngine` n'importe rien d'Android. Il est testé unitairement
  et pourrait être partagé avec iOS via Kotlin Multiplatform.
- **`ActionResult` scellé** : chaque action retourne un résultat typé
  (`Revealed`, `Exploded`, `Won`, `FlagChanged`), ce qui permet au ViewModel de
  déclencher sons/animations/stats au bon moment sans que le moteur connaisse l'UI.
- **`boardVersion`** : le moteur utilise des tableaux mutables pour la performance ;
  un compteur dans le `UiState` force la recomposition de la grille.

## Reste à porter

- Audio : la version web synthétise musique et SFX avec la WebAudio API
  (oscillateurs, réverbération à convolution, compresseur). Sur Android, deux options :
  1. **Simple** : pré-générer les sons en `.ogg` et les jouer avec `SoundPool`.
  2. **Fidèle** : synthèse temps réel avec `AudioTrack` ou la librairie Oboe (C++/NDK).
- Le personnage promeneur (walker) + joystick : un `Canvas` Compose avec une boucle
  `withFrameNanos` fera l'affaire.
- La vue 3D empilée (perspective CSS `rotateX`) : `graphicsLayer` supporte
  `rotationX` et `cameraDistance`, le rendu sera très proche.
