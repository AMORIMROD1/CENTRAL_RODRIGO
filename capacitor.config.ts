import type { CapacitorConfig } from "@capacitor/cli";

// O app Android é uma "casca" que abre o site publicado; os alarmes são
// agendados pelo plugin nativo (AlarmManager) e tocam mesmo com o app fechado.
// Para trocar o endereço sem editar o código, defina APP_URL ao compilar.
const APP_URL = process.env.APP_URL || "https://org-compass-pro.lovable.app";

const config: CapacitorConfig = {
  appId: "app.lovable.centralrodrigo",
  appName: "Central",
  // Página mínima usada apenas como "sem conexão" (ver www/index.html).
  webDir: "www",
  server: {
    url: APP_URL,
    cleartext: false,
    // Mantém login e navegação dentro do app (senão o Android abre o navegador).
    allowNavigation: ["*.lovable.app", "*.lovableproject.com", "*.lovable.dev", "*.supabase.co"],
  },
  plugins: {
    LocalNotifications: { iconColor: "#6b7f4e" },
  },
};

export default config;
