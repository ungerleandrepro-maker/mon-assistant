# Bloqueur Shorts (Android)

Petite appli Android qui vous fait sortir automatiquement :

| Appli | Ce qui est bloqué | Action |
|---|---|---|
| Instagram | le lecteur de **Reels** (onglet Reels, Reels ouverts depuis le fil ou les messages) | bouton « retour » |
| TikTok | **toute l'appli** (tout y est du contenu court) | retour à l'écran d'accueil |
| YouTube | le lecteur de **Shorts** | bouton « retour » |

Le reste d'Instagram (fil, stories, messages) et de YouTube (vidéos normales) reste utilisable.
Chaque blocage peut être activé/désactivé dans l'appli. Aucune donnée n'est collectée ni envoyée, l'appli n'a même pas la permission Internet.

## Installer sur le téléphone

1. Sur GitHub, ouvrez l'onglet **Actions** du dépôt → dernier passage de « Construire Bloqueur Shorts (APK) » → section **Artifacts** → téléchargez `bloqueur-shorts-apk` (un zip contenant `app-debug.apk`).
2. Décompressez et ouvrez `app-debug.apk` sur le téléphone. Autorisez l'installation depuis cette source si Android le demande (Play Protect peut avertir : « Installer quand même »).
3. Ouvrez **Bloqueur Shorts** → « Activer dans les réglages d'accessibilité » → Applis installées / Services téléchargés → **Bloqueur Shorts** → activer.
4. **Android 13 et plus** : si l'interrupteur est grisé (« paramètre restreint »), allez dans Réglages → Applications → Bloqueur Shorts → menu **⋮** en haut à droite → **Autoriser les paramètres restreints**, puis recommencez l'étape 3.

Sur certains téléphones (Xiaomi, Huawei, Samsung…), désactivez l'optimisation de batterie pour l'appli, sinon Android peut couper le service.

## Si un blocage ne marche plus

Instagram et YouTube changent parfois les identifiants internes de leurs écrans. Ils sont listés en haut de
`app/src/main/java/com/monassistant/bloqueur/BlockerService.java` (`INSTAGRAM_REELS_IDS`, `YOUTUBE_SHORTS_IDS`) :
il suffit d'y ajouter le nouvel identifiant, de pousser, et GitHub reconstruit l'APK.

## Et l'iPhone ?

iOS n'autorise pas une appli à lire l'écran d'une autre appli, donc ce type de blocage ciblé est impossible.
Sur iPhone, la seule option est Temps d'écran (limiter TikTok entièrement) ou d'utiliser Instagram/YouTube depuis le navigateur avec une extension.
