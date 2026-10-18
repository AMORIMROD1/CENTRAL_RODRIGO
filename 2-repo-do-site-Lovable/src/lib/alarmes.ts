import { Capacitor, registerPlugin } from "@capacitor/core";
import type { Tables } from "@/integrations/supabase/types";

export type Alarme = Tables<"alarmes">;

export const DIAS = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];
export const ACOES: Record<string, string> = {
  hoje: "Abrir planejamento diário",
  revisao: "Abrir revisão noturna",
  tarefas: "Abrir lista de tarefas",
  habitos: "Abrir hábitos",
  projeto: "Abrir um projeto",
};
export const SONS: Record<string, string> = {
  padrao: "Toque de alarme do aparelho",
  alarme: "Toque de alarme do aparelho (volume alto)",
  silencioso: "Sem som (só vibra e abre a tela)",
};

export const isNative = () => Capacitor.isNativePlatform() && Capacitor.getPlatform() === "android";

export const pad = (n: number) => String(n).padStart(2, "0");

/** Stable device notification id: one per alarm per weekday. */
export const notifId = (a: Pick<Alarme, "notif_id">, dia: number) => a.notif_id * 10 + dia;

/** Next occurrence of an alarm after `from`, or null if no days selected. */
export function proximaOcorrencia(a: Pick<Alarme, "hora" | "minuto" | "dias">, from = new Date()): Date | null {
  if (!a.dias.length) return null;
  for (let i = 0; i < 8; i++) {
    const d = new Date(from);
    d.setDate(d.getDate() + i);
    d.setHours(a.hora, a.minuto, 0, 0);
    if (a.dias.includes(d.getDay()) && d > from) return d;
  }
  return null;
}

export function rotaDaAcao(acao: string, projetoId?: string | null) {
  if (acao === "projeto" && projetoId) return `/projetos/${projetoId}`;
  return ({ hoje: "/hoje", revisao: "/revisoes", tarefas: "/semana", habitos: "/habitos" } as Record<string, string>)[acao] ?? "/hoje";
}


export type StatusNativo = {
  sdk: number;
  notificacoes: boolean;
  exatos: boolean;
  telaCheia: boolean;
  sobrepor: boolean;
  bateria: boolean;
  agendados?: number;
};
export type Permissoes = { nativo: boolean; status: StatusNativo | null };

type AlarmeNativo = { id: number; titulo: string; mensagem: string; hora: number; minuto: number; dias: number[]; ativo: boolean; som: string; vibrar: boolean; rota: string };
interface AlarmeNativoPlugin {
  status(): Promise<StatusNativo>;
  sincronizar(o: { alarmes: AlarmeNativo[] }): Promise<StatusNativo>;
  pedirNotificacoes(): Promise<StatusNativo>;
  abrirConfig(o: { tipo: "notificacoes" | "exatos" | "telaCheia" | "sobrepor" | "bateria" | "app" }): Promise<void>;
  testar(o: { titulo: string; mensagem: string; rota: string; som: string; vibrar: boolean; segundos: number }): Promise<void>;
  rotaPendente(): Promise<{ rota?: string }>;
  parar(): Promise<void>;
}
const Nativo = registerPlugin<AlarmeNativoPlugin>("AlarmeNativo");

/** Nunca deixa a tela esperando para sempre por uma resposta do Android. */
function comTimeout<T>(p: Promise<T>, ms = 8000, msg = "O Android não respondeu a tempo."): Promise<T> {
  return new Promise((ok, erro) => {
    const t = setTimeout(() => erro(new Error(msg)), ms);
    p.then((v) => { clearTimeout(t); ok(v); }, (e) => { clearTimeout(t); erro(e); });
  });
}

export async function verificarPermissoes(): Promise<Permissoes> {
  if (!isNative()) return { nativo: false, status: null };
  try {
    return { nativo: true, status: await comTimeout(Nativo.status()) };
  } catch {
    return { nativo: true, status: null };
  }
}

export async function pedirNotificacoes() {
  if (!isNative()) return;
  await comTimeout(Nativo.pedirNotificacoes(), 60000, "O Android não mostrou o pedido de permissão. Libere em Configurações > Apps > Central > Notificações.");
}

export async function abrirConfig(tipo: Parameters<AlarmeNativoPlugin["abrirConfig"]>[0]["tipo"]) {
  if (!isNative()) return;
  await comTimeout(Nativo.abrirConfig({ tipo }));
}

let limpouAntigos = false;
/** Remove notificações agendadas pela versão anterior (plugin de notificações). */
async function limparAntigos() {
  if (limpouAntigos) return;
  limpouAntigos = true;
  try {
    const { LocalNotifications: L } = await import("@capacitor/local-notifications");
    const pend = await comTimeout(L.getPending(), 4000);
    if (pend.notifications.length) await L.cancel({ notifications: pend.notifications.map((n) => ({ id: n.id })) });
  } catch { /* ignora */ }
}

/** Faz o Android ficar com exatamente os alarmes ativos salvos. */
export async function sincronizarAlarmes(alarmes: Alarme[]): Promise<StatusNativo | null> {
  if (!isNative()) return null;
  await limparAntigos();
  const lista: AlarmeNativo[] = alarmes.map((a) => ({
    id: a.notif_id,
    titulo: a.titulo,
    mensagem: a.mensagem,
    hora: a.hora,
    minuto: a.minuto,
    dias: a.dias,
    ativo: a.ativo,
    som: a.som,
    vibrar: a.vibrar,
    rota: rotaDaAcao(a.acao, a.projeto_id),
  }));
  return comTimeout(Nativo.sincronizar({ alarmes: lista }));
}

/** Toca em poucos segundos para você bloquear a tela e conferir. */
export async function testarSom(a: Alarme, segundos = 8) {
  if (!isNative()) throw new Error("O teste só funciona no aplicativo Android instalado.");
  await comTimeout(Nativo.testar({ titulo: `Teste: ${a.titulo}`, mensagem: a.mensagem || "Teste de alarme", rota: rotaDaAcao(a.acao, a.projeto_id), som: a.som, vibrar: a.vibrar, segundos }));
  return segundos;
}

/** Quando o alarme abre o app, vai para a página escolhida; também ao voltar para o app. */
export async function iniciarListenersNativos(navegar: (rota: string) => void, aoRetomar: () => void) {
  if (!isNative()) return () => {};
  const consumir = async () => {
    try {
      const { rota } = await comTimeout(Nativo.rotaPendente(), 4000);
      if (rota) navegar(rota);
    } catch { /* ignora */ }
  };
  consumir();
  const { App } = await import("@capacitor/app");
  const h = await App.addListener("resume", () => { consumir(); aoRetomar(); });
  return () => { h.remove(); };
}
