# AoE Store

Boutique d'applications privée pour les agents Agent of Empires : un serveur de
catalogue, un outil de publication en une commande et un client Android Compose
façon Play Store.

Le dépôt est un monorepo :

```
server/   API Node 22 (http natif, multipart maison), port 8099
cli/      commande store-publish et serveur MCP stdio
android/  client Compose (dev.proxboss.aoestore, minSdk 30)
tools/    génération des visuels et des icônes (Pillow)
data/     catalog.json, blobs et jeton de publication
deploy/   unité systemd aoe-store.service
shared/   code partagé (inspection APK, semver, accents)
```

Le serveur tourne actuellement sur `http://192.168.1.63:8099`. Le catalogue
est alimenté par les agents ; `GET /api/apps` renvoie la liste à jour (au moment
de la rédaction : 10 applications, dont **AoE Companion 0.9.7** et
**AoE Store 1.0.3**).

## Démarrage rapide

```bash
cd /home/dev/projects/aoe-store
/home/dev/node-v22.23.4-linux-x64/bin/node server/src/index.js
```

Au premier démarrage, le serveur crée `data/publish.token` (permissions `600`).
Le jeton n'est jamais renvoyé par l'API.

```bash
curl http://127.0.0.1:8099/api/health
# {"status":"ok","apps":2}
```

Pour un démarrage permanent, voir [deploy/README.md](deploy/README.md) :

```bash
sudo cp deploy/aoe-store.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now aoe-store
```

## API

Toutes les lectures sont publiques sur le LAN. Les erreurs sont du JSON stable
`{"error":{"code":"...","message":"..."}}`.

| Méthode | Chemin | Description |
| --- | --- | --- |
| GET | `/api/health` | `{status:"ok", apps:<n>}` |
| GET | `/api/apps` | tableau des applications du catalogue |
| GET | `/api/apps/{slug}` | détail : description, captures, versions triées |
| GET | `/api/apps/{slug}/versions/{version}/download` | APK (`application/vnd.android.package-archive`) |
| POST | `/api/publish` | publication multipart, `Authorization: Bearer <token>` |
| GET | `/files/**` | icônes, captures et APK en statique (cache immutable) |

Exemple de résumé d'application :

```json
{
  "slug": "aoe-companion",
  "name": "AoE Companion",
  "tagline": "Piloter Agent of Empires depuis le téléphone",
  "developer": "ProxBoss",
  "category": "Outils",
  "iconUrl": "/files/apps/aoe-companion/versions/v11/icon.png",
  "coverUrls": ["/files/apps/aoe-companion/versions/v11/cover-1.png"],
  "accentColor": "#3B82F6",
  "updatedAt": "2026-11-09T00:28:35.101Z",
  "downloads": 2,
  "latestVersion": {
    "version": "0.9.2",
    "versionCode": 11,
    "sizeBytes": 61820365,
    "publishedAt": "2026-11-09T00:28:35.101Z"
  }
}
```

Le détail ajoute `description`, `package`, `createdAt` et `versions[]` avec
`version`, `versionCode`, `changelog`, `sizeBytes`, `sha256`, `publishedAt` et
`downloadUrl`. Les URLs renvoyées sont relatives : le client les résout avec
l'URL de serveur configurée.

### Publication

`POST /api/publish` attend un multipart avec :

- `meta` : JSON `{slug,name,tagline,description,category,developer,accentColor,version,versionCode,changelog,package?}`
- `apk` : fichier APK (obligatoire)
- `icon` : PNG/JPEG/WebP (optionnel, extraite de l'APK sinon)
- `screenshot` : répétable (optionnel, 3 visuels générés sinon)

Le serveur lit le package avec `aapt2`/`aapt` (repli : parseur AXML intégré),
refuse un package incohérent (`422`), une version déjà publiée (`409`) et les
fichiers invalides. Il calcule le SHA-256 et la taille, puis écrit les blobs sous
`data/blobs/apps/{slug}/versions/v{versionCode}/`.

## Publier avec store-publish

Le shim est installé dans `/home/dev/.local/bin/store-publish` :

```bash
ls -l /home/dev/.local/bin/store-publish
store-publish --help
```

Il pointe sur le CLI du dépôt avec Node 22. Si `~/.local/bin` n'est pas dans le
`PATH`, ajoutez-le à votre profil ou appelez le chemin complet.

### Un agent publie en 3 lignes

```bash
TOKEN=$(cat /home/dev/projects/aoe-store/data/publish.token)
store-publish --server http://192.168.1.63:8099 --token "$TOKEN" \
  --slug mon-app --name "Mon App" --tagline "Accroche courte" --category Outils --developer ProxBoss \
  --apk ./app-debug.apk --changelog "Première version"
```

`version` et `versionCode` sont lus directement dans l'APK s'ils ne sont pas
fournis. L'icône est extraite de l'APK si `--icon` est absent. Sans
`--screenshot`, `tools/gen-covers.py` génère automatiquement trois visuels
1080x1920 avec l'icône, le nom, l'accroche et la version.

Options utiles :

```bash
store-publish ... --description "Description longue" --accent-color "#3B82F6"
store-publish ... --screenshot capture1.png --screenshot capture2.png
store-publish ... --dry-run          # valide et affiche sans envoyer
store-publish ... --no-cover-generation
```

Le CLI affiche l'URL publique du détail à la fin et sort avec un code non nul en
cas d'échec. Le jeton peut aussi venir de `AOE_STORE_TOKEN`, `--token-file` ou
par défaut de `data/publish.token` du dépôt.

### Recréer le shim

```bash
mkdir -p ~/.local/bin
cat > ~/.local/bin/store-publish <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exec /home/dev/node-v22.23.4-linux-x64/bin/node /home/dev/projects/aoe-store/cli/bin/store-publish.js "$@"
EOF
chmod +x ~/.local/bin/store-publish
```

## MCP pour les agents

Le dépôt fournit un petit serveur MCP stdio : `cli/mcp-server.js`. Il expose trois
outils aux IA :

| Outil | Rôle |
| --- | --- |
| `store_list_apps` | liste le catalogue et les dernières versions |
| `store_get_app` | renvoie la fiche complète, les versions, le SHA-256 et les URLs |
| `store_publish` | publie ou met à jour un APK via `store-publish` |

Le jeton n'est jamais passé en argument : le serveur MCP réutilise
`AOE_STORE_TOKEN`, `AOE_STORE_TOKEN_FILE` ou `data/publish.token`. L'URL par
défaut est `http://192.168.1.63:8099`, surchargeable avec `AOE_STORE_URL` ou
l'argument `server` de chaque outil.

Lancement manuel (transport stdio) :

```bash
AOE_STORE_URL=http://192.168.1.63:8099 \
  /home/dev/node-v22.23.4-linux-x64/bin/node cli/mcp-server.js
```

Configuration opencode ajoutée dans `~/.config/opencode/opencode.json` :

```json
{
  "mcp": {
    "aoe-store": {
      "type": "local",
      "command": [
        "/home/dev/node-v22.23.4-linux-x64/bin/node",
        "/home/dev/projects/aoe-store/cli/mcp-server.js"
      ],
      "enabled": true,
      "environment": {
        "AOE_STORE_URL": "http://192.168.1.63:8099"
      }
    }
  }
}
```

Un redémarrage d'opencode (ou de la session AoE) est nécessaire pour charger le
serveur. Les outils apparaissent ensuite sous le nom `aoe-store`. Exemple de
demande à une IA : « liste les apps du store, puis publie
`android/app/build/outputs/apk/debug/app-debug.apk` en 1.0.4 avec le changelog ».

## Client Android

Caractéristiques : Compose Material 3, OkHttp, DataStore, navigation Compose,
aucun Hilt/Room. Thème sombre par défaut, clair/système dans les réglages.

- Accueil : recherche en pilule, carrousel de banners avec indicateur de page,
  « Nouveautés et mises à jour », puces de catégories, grille d'applications,
  pull-to-refresh, squelettes et cache hors ligne.
- Fiche : image d'en-tête, icône, développeur, catégorie, bouton
  Installer/Mettre à jour/Ouvrir, galerie de captures, description, nouveautés,
  historique repliable avec téléchargement par version et SHA-256 affiché.
- Installation : téléchargement avec progression, vérification SHA-256,
  FileProvider + `ACTION_VIEW`, gestion de l'autorisation « sources inconnues »
  et de l'état installé au retour.
- Réglages : URL du serveur (défaut `http://192.168.1.63:8099`), test de
  connexion, thème système/sombre/clair, à propos.

### Construire et tester

```bash
cd /home/dev/projects/aoe-store/android
ANDROID_HOME=/home/dev/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew clean assembleDebug testDebugUnitTest --no-daemon
```

APK : `android/app/build/outputs/apk/debug/app-debug.apk`
(package `dev.proxboss.aoestore`, version `1.0.3`, versionCode `4`).

Installation sur un appareil connecté :

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Les tests unitaires couvrent le parsing du catalogue, la résolution de version,
la vérification SHA-256 et le mapping des URLs.

## Tests serveur

```bash
cd /home/dev/projects/aoe-store
/home/dev/node-v22.23.4-linux-x64/bin/node --test shared/test/*.test.js server/test/*.test.js
```

Le test d'intégration publie le vrai APK `/tmp/AoE-Companion-0.9.2-debug.apk`
(s'il est présent), vérifie le catalogue, le téléchargement, le SHA-256, les
erreurs 401/404/405/409/422 et la permission `600` du jeton.

## Données et sécurité

- `data/catalog.json` : catalogue courant, versionné sous forme d’instantanés.
- `data/blobs/` : APK, icônes et captures (ignorés par git ; un clone ne sert
  pas les fichiers déjà publiés).
- `data/publish.token` : jeton de publication, `chmod 600`, ignoré par git.
- `data/tmp/` : uploads temporaires nettoyés après chaque publication.
- Lectures publiques sur le LAN ; seule la publication exige le Bearer token.
- Le serveur refuse les traversées de chemin et les requêtes malformées sans
  planter.

## Ce qui reste manuel

- Activer le service systemd avec `sudo` (voir `deploy/README.md`).
- Autoriser le port `8099` dans un éventuel pare-feu.
- Installer l'APK sur le téléphone et vérifier l'URL du serveur dans les
  réglages si l'adresse LAN change.
- Pour une nouvelle version du client, relancer la construction puis
  `store-publish` avec `--version`/`--version-code` incrémentés.
