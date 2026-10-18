// Uso:
//   node scripts/android.mjs prepare   -> cria .native/ (mini-projeto só com o Capacitor)
//   node scripts/android.mjs patch     -> ajusta o AndroidManifest em .native/android
// Roda no GitHub Actions (ver .github/workflows/build-android.yml).
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const nativeDir = join(root, ".native");
const mode = process.argv[2];

if (mode === "prepare") {
  // Versões fixas (funciona mesmo neste repositório sem o package.json do app).
  const cap = {
    "@capacitor/android": "^8.5.3",
    "@capacitor/app": "^8.1.2",
    "@capacitor/core": "^8.5.3",
    "@capacitor/local-notifications": "^8.3.1",
  };
  mkdirSync(nativeDir, { recursive: true });
  writeFileSync(
    join(nativeDir, "package.json"),
    JSON.stringify({ name: "central-native", private: true, dependencies: cap, devDependencies: { "@capacitor/cli": "^8.5.3", typescript: "^5.8.3" } }, null, 2),
  );
  cpSync(join(root, "capacitor.config.ts"), join(nativeDir, "capacitor.config.ts"));
  cpSync(join(root, "www"), join(nativeDir, "www"), { recursive: true });
  console.log("OK: .native preparado com", Object.keys(cap).join(", "));
} else if (mode === "patch") {
  const file = join(nativeDir, "android/app/src/main/AndroidManifest.xml");
  if (!existsSync(file)) throw new Error("AndroidManifest.xml não encontrado: rode 'cap add android' antes.");
  let xml = readFileSync(file, "utf8");
  const perms = [
    "android.permission.INTERNET",
    "android.permission.POST_NOTIFICATIONS", // Android 13+: mostrar notificações
    "android.permission.USE_EXACT_ALARM", // alarme exato (despertador) concedido automaticamente
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_FULL_SCREEN_INTENT", // abrir a tela de alarme sobre o bloqueio
    "android.permission.SYSTEM_ALERT_WINDOW", // opcional: abrir a tela direto (Exibir sobre outros apps)
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED", // reagendar após reiniciar o tablet
    "android.permission.WAKE_LOCK",
    "android.permission.VIBRATE",
  ];
  const add = perms.filter((p) => !xml.includes(`"${p}"`)).map((p) => `    <uses-permission android:name="${p}" />`);
  if (add.length) xml = xml.replace("</manifest>", `${add.join("\n")}\n</manifest>`);

  if (!xml.includes("AlarmeService")) {
    const componentes = `
        <activity
            android:name=".AlarmeActivity"
            android:exported="false"
            android:launchMode="singleInstance"
            android:excludeFromRecents="true"
            android:taskAffinity=""
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:theme="@android:style/Theme.DeviceDefault.NoActionBar" />

        <service
            android:name=".AlarmeService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <receiver android:name=".AlarmeReceiver" android:exported="false" />

        <receiver android:name=".AlarmeBootReceiver" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
`;
    xml = xml.replace("</application>", `${componentes}    </application>`);
  }
  writeFileSync(file, xml);

  // Código nativo do alarme (Java) -> pacote do app.
  const destino = join(nativeDir, "android/app/src/main/java/app/lovable/centralrodrigo");
  mkdirSync(destino, { recursive: true });
  // Procura os .java em qualquer pasta do repositório (usa o primeiro de cada nome).
  const achados = new Map();
  const ignorar = new Set([".git", ".native", "node_modules", ".github"]);
  const varrer = (dir) => {
    for (const nome of readdirSync(dir).sort()) {
      if (ignorar.has(nome)) continue;
      const caminho = join(dir, nome);
      if (statSync(caminho).isDirectory()) varrer(caminho);
      else if (nome.endsWith(".java") && !achados.has(nome)) achados.set(nome, caminho);
    }
  };
  varrer(root);
  const esperados = ["AlarmeActivity", "AlarmeAgenda", "AlarmeBootReceiver", "AlarmePlugin", "AlarmeReceiver", "AlarmeService", "MainActivity"];
  const faltando = esperados.filter((n) => !achados.has(`${n}.java`));
  if (faltando.length) throw new Error(`Arquivos .java não encontrados no repositório: ${faltando.join(", ")}`);
  for (const [nome, caminho] of achados) cpSync(caminho, join(destino, nome));
  console.log("Arquivos .java copiados:", [...achados.keys()].join(", "));
  console.log("OK: manifest e código nativo atualizados. Permissões novas:", add.length);
} else {
  console.error("Uso: node scripts/android.mjs prepare|patch");
  process.exit(1);
}
