# 💣 Démineur 3D — Ultimate Edition

Un démineur moderne avec un mode 3D à couches multiples, disponible en deux versions :

- **`web/`** — la version web originale (HTML/CSS/JS, un seul fichier, jouable dans n'importe quel navigateur)
- **`android/`** — le portage **Kotlin / Jetpack Compose**, en préparation d'une publication sur Google Play

## 🎮 Fonctionnalités

| Fonctionnalité | Web | Android |
|---|---|---|
| Mode classique (1 couche) | ✅ | ✅ |
| Mode 3D (3 couches, voisinage à 26 cases) | ✅ | ✅ |
| 4 difficultés : Facile 8×8, Moyen 12×12, Difficile 16×16, Géant 24×24 | ✅ | ✅ |
| Premier clic toujours sûr | ✅ | ✅ |
| Chord-click (révéler les voisins d'un chiffre satisfait) | ✅ | ✅ |
| Drapeau → ❓ → vide (appui long) | ✅ | ✅ |
| Timer + records par difficulté | ✅ | ✅ |
| Combos / streaks | ✅ | ✅ |
| Statistiques persistantes | ✅ localStorage | ✅ SharedPreferences |
| Zoom / déplacement (pinch & pan) | ✅ | ✅ |
| Musique synthétisée + effets sonores | ✅ | 🚧 à venir |
| Personnage promeneur + joystick | ✅ | 🚧 à venir |

## 🚀 Lancer la version web

Ouvrir simplement `web/index.html` dans un navigateur. Aucune dépendance.

## 🤖 Compiler la version Android

Prérequis : [Android Studio](https://developer.android.com/studio) (Ladybug ou plus récent), JDK 17.

```bash
cd android
./gradlew assembleDebug        # APK de debug
./gradlew test                 # tests unitaires du moteur de jeu
```

Ou ouvrir le dossier `android/` dans Android Studio et cliquer sur ▶.

> **Note** : le wrapper Gradle (`gradlew`) est généré automatiquement par Android Studio
> à la première ouverture du projet. Sinon : `gradle wrapper --gradle-version 8.9`.

## 🏗 Architecture Android

```
android/app/src/main/java/com/demineur3d/
├── MainActivity.kt              # Point d'entrée, thème sombre
├── game/
│   └── GameEngine.kt            # Logique pure (aucune dépendance Android → testable)
├── data/
│   └── StatsRepository.kt       # Persistance des stats (SharedPreferences)
└── ui/
    ├── GameViewModel.kt         # État, timer, combos (StateFlow)
    └── GameScreen.kt            # Interface Jetpack Compose
```

Le moteur (`GameEngine`) est volontairement séparé de l'interface : il est couvert par
des tests unitaires (`app/src/test/`) et pourrait être réutilisé tel quel pour une
version desktop (Compose Multiplatform) ou iOS (Kotlin Multiplatform).

## 📋 Feuille de route Google Play

- [ ] Icône adaptative + splash screen
- [ ] Sons et musique (SoundPool / Oboe)
- [ ] Personnage promeneur + joystick
- [ ] Animations (explosion, révélation en cascade)
- [ ] Vue 3D empilée des couches (perspective)
- [ ] Signature de release + Play App Signing
- [ ] Politique de confidentialité (requise par Google Play)
- [ ] Captures d'écran + fiche Play Store

## 📄 Licence

MIT — voir [LICENSE](LICENSE).
