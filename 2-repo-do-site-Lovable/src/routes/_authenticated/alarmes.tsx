import { createFileRoute } from "@tanstack/react-router";
import { queryOptions, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { format } from "date-fns";
import { ptBR } from "date-fns/locale";
import { AlertTriangle, BellRing, Copy, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { q, useMut } from "@/lib/data";
import {
  ACOES, DIAS, SONS, abrirConfig, isNative, pad, pedirNotificacoes, proximaOcorrencia, sincronizarAlarmes, testarSom, verificarPermissoes,
  type Alarme, type Permissoes,
} from "@/lib/alarmes";
import { Empty, PageHeader } from "@/components/app/ui";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

export const qAlarmes = queryOptions({
  queryKey: ["alarmes"],
  queryFn: async () => {
    const r = await supabase.from("alarmes").select("*").order("hora").order("minuto");
    if (r.error) throw new Error(r.error.message);
    return r.data as Alarme[];
  },
});

export const Route = createFileRoute("/_authenticated/alarmes")({
  head: () => ({ meta: [{ title: "Alarmes e Rotina — Central Executiva" }, { name: "description", content: "Lembretes sonoros da sua rotina." }, { property: "og:title", content: "Alarmes e Rotina — Central Executiva" }, { property: "og:description", content: "Lembretes sonoros da sua rotina." }] }),
  component: Alarmes,
});

const vazio = { nome: "", titulo: "", mensagem: "", hora: 7, minuto: 0, dias: [1, 2, 3, 4, 5], ativo: false, som: "padrao", vibrar: true, acao: "hoje", projeto_id: null as string | null };
type Form = typeof vazio & { id?: string | undefined };

const sim = (v: boolean | undefined) => (v ? "Ligado" : "Desligado");

function Alarmes() {
  const { data: alarmes = [] } = useQuery(qAlarmes);
  const { data: projetos = [] } = useQuery(q.projetos);
  const mut = useMut();
  const qc = useQueryClient();
  const [form, setForm] = useState<Form | null>(null);
  const [perm, setPerm] = useState<Permissoes | null>(null);
  const atualizarPerm = () => verificarPermissoes().then(setPerm);
  useEffect(() => {
    const aoVoltar = () => { if (document.visibilityState === "visible") atualizarPerm(); };
    document.addEventListener("visibilitychange", aoVoltar);
    window.addEventListener("focus", aoVoltar);
    return () => { document.removeEventListener("visibilitychange", aoVoltar); window.removeEventListener("focus", aoVoltar); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { atualizarPerm(); supabase.rpc("seed_alarmes").then(({ error }) => { if (!error) mut(Promise.resolve({ error: null })); }); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function salvar() {
    if (!form || !form.nome.trim()) { toast.error("Dê um nome ao alarme."); return; }
    const { id } = form;
    const rest = Object.fromEntries(Object.keys(vazio).map((k) => [k, form[k as keyof typeof vazio]])) as typeof vazio;
    const dados = { ...rest, titulo: rest.titulo || rest.nome, projeto_id: rest.acao === "projeto" ? rest.projeto_id : null, updated_at: new Date().toISOString() };
    const ok = await mut(id ? supabase.from("alarmes").update(dados).eq("id", id) : supabase.from("alarmes").insert(dados), "Alarme salvo");
    if (ok) setForm(null);
  }
  async function ativar(a: Alarme, v: boolean) {
    if (v && isNative() && !perm?.status?.notificacoes) {
      try { await pedirNotificacoes(); } catch (e) { toast.error((e as Error).message); }
      atualizarPerm();
    }
    const ok = await mut(supabase.from("alarmes").update({ ativo: v, updated_at: new Date().toISOString() }).eq("id", a.id));
    if (ok && v && isNative()) {
      const r = await qc.fetchQuery({ ...qAlarmes, staleTime: 0 }).then((l) => sincronizarAlarmes(l)).catch((e: Error) => { toast.error(`Não foi possível agendar: ${e.message}`); return null; });
      if (r) toast.success(`Alarme agendado no Android (${r.agendados ?? 0} ativo${r.agendados === 1 ? "" : "s"}).`);
      atualizarPerm();
    }
  }
  async function testar(a: Alarme) {
    try {
      const seg = await testarSom(a);
      toast.success(`Bloqueie a tela do tablet agora: o alarme toca em ${seg} segundos.`);
    } catch (e) { toast.error((e as Error).message); }
    atualizarPerm();
  }

  return (
    <div>
      <PageHeader title="Alarmes e Rotina" subtitle="Alarmes que tocam e abrem o app mesmo com a tela bloqueada." action={<Button size="sm" onClick={() => setForm({ ...vazio })}><Plus /> Novo</Button>} />

      <div className="surface mb-6 space-y-2 p-4 text-sm">
        {!perm?.nativo ? (
          <p className="flex gap-2 text-warn"><AlertTriangle className="size-4 shrink-0" /> Você está no navegador. Os alarmes só tocam no aplicativo Android instalado no tablet. Aqui você pode configurá-los; eles serão agendados quando abrir o app.</p>
        ) : !perm.status ? (
          <p className="flex gap-2 text-warn"><AlertTriangle className="size-4 shrink-0" /> O app Android não respondeu. Feche e abra o Central de novo. Se continuar, instale a versão mais recente do APK.</p>
        ) : (
          <>
            <p>Notificações: <b>{sim(perm.status.notificacoes)}</b> {!perm.status.notificacoes && <Button size="sm" variant="outline" className="ml-2" onClick={async () => { try { await pedirNotificacoes(); } catch { await abrirConfig("notificacoes"); } atualizarPerm(); }}>Permitir</Button>}</p>
            <p>Abrir sobre a tela de bloqueio: <b>{sim(perm.status.telaCheia)}</b> {!perm.status.telaCheia && <Button size="sm" variant="outline" className="ml-2" onClick={async () => { await abrirConfig("telaCheia"); }}>Abrir configurações</Button>}</p>
            <p>Exibir sobre outros apps: <b>{sim(perm.status.sobrepor)}</b> {!perm.status.sobrepor && <Button size="sm" variant="outline" className="ml-2" onClick={async () => { await abrirConfig("sobrepor"); }}>Abrir configurações</Button>}</p>
            <p>Bateria sem restrição: <b>{sim(perm.status.bateria)}</b> {!perm.status.bateria && <Button size="sm" variant="outline" className="ml-2" onClick={async () => { await abrirConfig("bateria"); }}>Permitir</Button>}</p>
            <p className="text-muted-foreground">Esta lista se atualiza ao voltar das configurações. Use o sino para testar: bloqueie a tela e aguarde 8 segundos.</p>
          </>
        )}
      </div>

      {alarmes.length === 0 ? <Empty>Nenhum alarme ainda.</Empty> : (
        <div className="space-y-2">
          {alarmes.map((a) => {
            const prox = proximaOcorrencia(a);
            return (
              <div key={a.id} className={cn("surface flex items-center gap-3 p-3", !a.ativo && "opacity-70")}>
                <div className="w-16 font-display text-2xl font-semibold tabular-nums">{pad(a.hora)}:{pad(a.minuto)}</div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{a.nome}</p>
                  <p className="text-xs text-muted-foreground">
                    {a.dias.length ? a.dias.slice().sort().map((d) => DIAS[d]).join(" ") : "Sem dias"} · {SONS[a.som]?.split(" (")[0]}
                    {a.ativo && prox && <> · próxima: {format(prox, "EEE HH:mm", { locale: ptBR })}</>}
                  </p>
                </div>
                <Button size="icon" variant="ghost" aria-label="Testar som" title="Testar som" onClick={() => testar(a)}><BellRing /></Button>
                <Button size="icon" variant="ghost" aria-label="Editar" onClick={() => setForm({ ...a })}><Pencil /></Button>
                <Button size="icon" variant="ghost" aria-label="Duplicar" onClick={() => setForm({ ...a, id: undefined, nome: `${a.nome} (cópia)`, ativo: false })}><Copy /></Button>
                <Button size="icon" variant="ghost" aria-label="Excluir" onClick={() => confirm(`Excluir "${a.nome}"?`) && mut(supabase.from("alarmes").delete().eq("id", a.id), "Alarme excluído")}><Trash2 /></Button>
                <Switch checked={a.ativo} onCheckedChange={(v) => ativar(a, v)} aria-label="Ativado" />
              </div>
            );
          })}
        </div>
      )}

      <Dialog open={!!form} onOpenChange={(o) => !o && setForm(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader><DialogTitle>{form?.id ? "Editar alarme" : "Novo alarme"}</DialogTitle></DialogHeader>
          {form && (
            <div className="space-y-3 text-sm">
              <label className="block">Nome<Input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} /></label>
              <label className="block">Título da notificação<Input value={form.titulo} onChange={(e) => setForm({ ...form, titulo: e.target.value })} /></label>
              <label className="block">Mensagem<Input value={form.mensagem} onChange={(e) => setForm({ ...form, mensagem: e.target.value })} /></label>
              <label className="block">Horário<Input type="time" value={`${pad(form.hora)}:${pad(form.minuto)}`} onChange={(e) => { const [h, m] = e.target.value.split(":").map(Number); setForm({ ...form, hora: h || 0, minuto: m || 0 }); }} /></label>
              <div>Dias
                <div className="mt-1 flex flex-wrap gap-1">
                  {DIAS.map((d, i) => (
                    <button key={d} type="button" onClick={() => setForm({ ...form, dias: form.dias.includes(i) ? form.dias.filter((x) => x !== i) : [...form.dias, i] })}
                      className={cn("rounded-md border px-2.5 py-1 text-xs", form.dias.includes(i) && "border-primary bg-primary text-primary-foreground")}>{d}</button>
                  ))}
                </div>
              </div>
              <label className="block">Som
                <select className="mt-1 w-full rounded-md border bg-background p-2" value={form.som} onChange={(e) => setForm({ ...form, som: e.target.value })}>
                  {Object.entries(SONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </label>
              <label className="flex items-center justify-between">Vibrar (quando suportado)<Switch checked={form.vibrar} onCheckedChange={(v) => setForm({ ...form, vibrar: v })} /></label>
              <label className="block">Ao tocar na notificação
                <select className="mt-1 w-full rounded-md border bg-background p-2" value={form.acao} onChange={(e) => setForm({ ...form, acao: e.target.value })}>
                  {Object.entries(ACOES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </label>
              {form.acao === "projeto" && (
                <select className="w-full rounded-md border bg-background p-2" value={form.projeto_id ?? ""} onChange={(e) => setForm({ ...form, projeto_id: e.target.value || null })}>
                  <option value="">Escolha o projeto</option>
                  {projetos.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
                </select>
              )}
              <label className="flex items-center justify-between">Ativado<Switch checked={form.ativo} onCheckedChange={(v) => setForm({ ...form, ativo: v })} /></label>
              <Button className="w-full" onClick={salvar}>Salvar</Button>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
