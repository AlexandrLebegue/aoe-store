# Déploiement systemd

L'unité `aoe-store.service` lance le serveur sous l'utilisateur `dev`, sur le port
`8099`, avec les données dans `/home/dev/projects/aoe-store/data`.

## Installation

```bash
# Arrêter une éventuelle instance lancée à la main sur le port 8099
pkill -f 'aoe-store/server/src/index.js' || true

sudo cp /home/dev/projects/aoe-store/deploy/aoe-store.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now aoe-store
systemctl status aoe-store
```

## Journal et redémarrage

```bash
journalctl -u aoe-store -f
sudo systemctl restart aoe-store
```

## Vérification

```bash
curl http://127.0.0.1:8099/api/health
```

Le jeton de publication se trouve dans `data/publish.token` (créé au premier
démarrage, permissions `600`) et n'est jamais exposé par l'API.
