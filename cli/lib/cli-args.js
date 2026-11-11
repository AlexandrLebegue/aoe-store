export class UsageError extends Error {
  constructor(message) {
    super(message);
    this.name = 'UsageError';
  }
}

const VALUE_OPTIONS = new Map([
  ['server', 'server'],
  ['token', 'token'],
  ['token-file', 'tokenFile'],
  ['slug', 'slug'],
  ['name', 'name'],
  ['tagline', 'tagline'],
  ['description', 'description'],
  ['category', 'category'],
  ['developer', 'developer'],
  ['accent-color', 'accentColor'],
  ['package', 'package'],
  ['apk', 'apk'],
  ['version', 'version'],
  ['version-code', 'versionCode'],
  ['changelog', 'changelog'],
  ['icon', 'icon'],
  ['screenshot', 'screenshot'],
]);

const FLAG_OPTIONS = new Map([
  ['dry-run', 'dryRun'],
  ['help', 'help'],
  ['no-cover-generation', 'noCoverGeneration'],
]);

export function parseArgs(argv) {
  const options = { screenshots: [] };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (!argument.startsWith('--')) {
      throw new UsageError(`Argument inattendu : ${argument}`);
    }
    const equals = argument.indexOf('=');
    const key = equals === -1 ? argument.slice(2) : argument.slice(2, equals);
    const inlineValue = equals === -1 ? undefined : argument.slice(equals + 1);

    if (FLAG_OPTIONS.has(key)) {
      if (inlineValue !== undefined) {
        throw new UsageError(`L’option --${key} ne prend pas de valeur`);
      }
      options[FLAG_OPTIONS.get(key)] = true;
      continue;
    }

    if (!VALUE_OPTIONS.has(key)) {
      throw new UsageError(`Option inconnue : --${key}`);
    }
    let value = inlineValue;
    if (value === undefined) {
      index += 1;
      value = argv[index];
    }
    if (value === undefined || value === '') {
      throw new UsageError(`Valeur manquante pour --${key}`);
    }
    const target = VALUE_OPTIONS.get(key);
    if (target === 'screenshot') options.screenshots.push(value);
    else options[target] = value;
  }
  return options;
}

export function usage() {
  return `AoE Store — store-publish

Publie un APK dans le catalogue AoE Store.

Utilisation :
  store-publish --slug <slug> --name "<nom>" --tagline "<accroche>" \\
    --category <catégorie> --developer <éditeur> --apk <fichier.apk> \\
    [--version <version>] [--version-code <code>] [--changelog "<texte>"] \\
    [--description "<description longue>"] [--icon <icône.png>] \\
    [--screenshot <capture.png>]... [--accent-color "#RRGGBB"] \\
    [--server <url>] [--token <jeton> | --token-file <fichier>] [--dry-run]

Options :
  --server <url>          URL du serveur (défaut : $AOE_STORE_URL ou http://127.0.0.1:8099)
  --token <jeton>         Jeton de publication (défaut : $AOE_STORE_TOKEN)
  --token-file <fichier>  Fichier contenant le jeton (défaut : data/publish.token du dépôt)
  --slug <slug>           Identifiant unique (minuscules, chiffres, tirets)
  --name <nom>            Nom affiché de l’application
  --tagline <texte>       Accroche courte
  --description <texte>   Description longue (défaut : accroche)
  --category <nom>        Catégorie (ex. Outils)
  --developer <nom>       Éditeur
  --accent-color <hex>    Couleur d’accent #RRGGBB (défaut : dérivée du slug)
  --package <nom>         Package Android attendu (vérifié par le serveur)
  --apk <fichier>         APK à publier (obligatoire)
  --version <version>     versionName (défaut : lu dans l’APK)
  --version-code <n>      versionCode (défaut : lu dans l’APK)
  --changelog <texte>     Nouveautés de cette version
  --icon <fichier>        Icône PNG/JPEG/WebP (défaut : extraite de l’APK)
  --screenshot <fichier>  Capture d’écran (répétable). Si aucune n’est fournie,
                          3 visuels 1080x1920 sont générés automatiquement.
  --dry-run               Valide et affiche la publication sans envoyer
  --no-cover-generation   Désactive la génération automatique des visuels
  -h, --help              Affiche cette aide

Variables d’environnement :
  AOE_STORE_URL, AOE_STORE_TOKEN, AOE_STORE_TOKEN_FILE, AOE_STORE_PYTHON

Exemple :
  store-publish --server http://192.168.1.63:8099 \\
    --token "$(cat /home/dev/projects/aoe-store/data/publish.token)" \\
    --slug aoe-companion --name "AoE Companion" \\
    --tagline "Piloter Agent of Empires depuis le téléphone" \\
    --category Outils --developer ProxBoss \\
    --apk /tmp/AoE-Companion-0.9.2-debug.apk \\
    --version 0.9.2 --version-code 11 --changelog "Première version"
`;
}
