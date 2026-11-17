// Le cahier — app PC.
// Une fenêtre dédiée au cahier, qui reste dans la zone de notification quand on la ferme :
// la transcription des audios envoyés par le téléphone continue en arrière-plan.
const { app, BrowserWindow, Tray, Menu, shell, nativeImage, session } = require('electron');
const path = require('path');

const CAHIER = 'https://claude.ai/artifact/LmxeKaao9abFDJfXDurLL5';
const INTERNES = ['claude.ai', 'anthropic.com', 'accounts.google.com', 'appleid.apple.com'];

let win = null, tray = null, quitter = false;

if (!app.requestSingleInstanceLock()) { app.quit(); }
app.on('second-instance', () => montrer());

function interne(url) {
  try {
    const h = new URL(url).hostname;
    return INTERNES.some(d => h === d || h.endsWith('.' + d)) || h.endsWith('.claudeusercontent.com');
  } catch { return false; }
}

function creer(cache) {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 420, minHeight: 500,
    title: 'Le cahier',
    backgroundColor: '#14151a',
    autoHideMenuBar: true,
    show: !cache,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      partition: 'persist:cahier',      // garde la connexion à Claude
      backgroundThrottling: false,      // la transcription continue fenêtre cachée
      spellcheck: false,
    },
  });
  win.removeMenu();
  win.loadURL(CAHIER);

  // Liens externes (GitHub, sites…) dans le navigateur normal.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (interne(url)) return { action: 'allow', overrideBrowserWindowOptions: { autoHideMenuBar: true, backgroundColor: '#14151a' } };
    shell.openExternal(url); return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (!interne(url)) { e.preventDefault(); shell.openExternal(url); }
  });
  win.webContents.on('page-title-updated', e => e.preventDefault());

  // Fermer = cacher dans la zone de notification.
  win.on('close', e => {
    if (!quitter) {
      e.preventDefault(); win.hide();
      if (!app.__dejaDit && tray) {
        tray.displayBalloon?.({ title: 'Le cahier', content: 'Je reste ici pour transcrire tes audios. Clic droit sur l’icône pour quitter.' });
        app.__dejaDit = true;
      }
    }
  });
}

function montrer() {
  if (!win) creer(false);
  if (win.isMinimized()) win.restore();
  win.show(); win.focus();
}

function menuTray() {
  const auDemarrage = app.getLoginItemSettings().openAtLogin;
  return Menu.buildFromTemplate([
    { label: 'Ouvrir le cahier', click: montrer },
    { label: 'Recharger', click: () => win && win.reload() },
    { type: 'separator' },
    {
      label: 'Lancer au démarrage de Windows', type: 'checkbox', checked: auDemarrage,
      click: (item) => {
        app.setLoginItemSettings({ openAtLogin: item.checked, args: ['--cache'] });
        tray.setContextMenu(menuTray());
      },
    },
    { type: 'separator' },
    { label: 'Quitter', click: () => { quitter = true; app.quit(); } },
  ]);
}

app.whenReady().then(() => {
  // User-agent de Chrome classique : certaines pages de connexion refusent « Electron ».
  const ua = session.fromPartition('persist:cahier').getUserAgent().replace(/\s?Electron\/\S+/, '').replace(/\s?le-cahier\/\S+/, '');
  session.fromPartition('persist:cahier').setUserAgent(ua);
  app.userAgentFallback = ua;

  // Micro autorisé pour le bouton « Capter » du cahier.
  session.fromPartition('persist:cahier').setPermissionRequestHandler((wc, perm, cb) => {
    cb(['media', 'notifications', 'clipboard-sanitized-write', 'fullscreen'].includes(perm));
  });

  tray = new Tray(nativeImage.createFromPath(path.join(__dirname, 'tray.png')));
  tray.setToolTip('Le cahier');
  tray.setContextMenu(menuTray());
  tray.on('click', montrer);

  creer(process.argv.includes('--cache'));
});

app.on('before-quit', () => { quitter = true; });
app.on('window-all-closed', e => e.preventDefault?.());
