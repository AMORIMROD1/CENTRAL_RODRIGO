// Uso:
//   node scripts/android.mjs prepare   -> cria .native/ (mini-projeto só com o Capacitor)
//   node scripts/android.mjs patch     -> ajusta o AndroidManifest em .native/android
// Roda no GitHub Actions (ver .github/workflows/build-android.yml).
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const nativeDir = join(root, ".native");
const mode = process.argv[2];

if (mode === "prepare") {
  const pkg = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
  const cap = Object.fromEntries(Object.entries(pkg.dependencies).filter(([k]) => k.startsWith("@capacitor/")));
  mkdirSync(nativeDir, { recursive: true });
  writeFileSync(
    join(nativeDir, "package.json"),
    JSON.stringify({ name: "central-native", private: true, dependencies: cap, devDependencies: { "@capacitor/cli": pkg.devDependencies["@capacitor/cli"], typescript: "^5.8.3" } }, null, 2),
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
    "android.permission.USE_EXACT_ALARM", // Android 13+: alarme exato concedido automaticamente
    "android.permission.SCHEDULE_EXACT_ALARM", // Android 12: alarme exato (usuário libera em "Alarmes e lembretes")
    "android.permission.RECEIVE_BOOT_COMPLETED", // reagendar após reiniciar o tablet
    "android.permission.WAKE_LOCK",
    "android.permission.VIBRATE",
  ];
  const add = perms.filter((p) => !xml.includes(`"${p}"`)).map((p) => `    <uses-permission android:name="${p}" />`);
  if (add.length) xml = xml.replace("</manifest>", `${add.join("\n")}\n</manifest>`);
  writeFileSync(file, xml);
  console.log("OK: manifest atualizado. Permissões adicionadas:", add.length ? add.length : "nenhuma (já existiam)");
} else {
  console.error("Uso: node scripts/android.mjs prepare|patch");
  process.exit(1);
}
