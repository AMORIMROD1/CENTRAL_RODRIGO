# Alarme próprio (toca e abre o app) — passo a passo

O alarme agora é nativo: o Android acorda o app na hora, toca o toque de alarme em loop (volume de ALARME),
vibra e abre uma tela cheia por cima do bloqueio com "Parar e abrir o app" e "Soneca 10 min".

## Parte 1 — Repositório do APK (CENTRAL_RODRIGO)
Envie para o repositório (Add file → Upload files; a pasta `native-src` deve ficar na raiz):
- `scripts/android.mjs`  (substitui o atual)
- `native-src/` com os 7 arquivos .java (novos)
Depois: Actions → Build APK Android (Central) → Run workflow → baixe `central-apk` → instale no tablet
(desinstale a versão antiga antes).

## Parte 2 — Repositório do site (o que o Lovable usa)
Substitua estes 2 arquivos (lápis de edição no GitHub, apague tudo e cole o novo):
- `src/lib/alarmes.ts`
- `src/routes/_authenticated/alarmes.tsx`
Depois, no Lovable, clique em **Publish**. O app do tablet só enxerga a mudança depois de publicada.

## Parte 3 — No tablet (Android 15)
1. Abra o Central → Alarmes. Deve aparecer um quadro com: Notificações, Abrir sobre a tela de bloqueio,
   Exibir sobre outros apps, Bateria sem restrição. Toque em cada botão e libere.
2. Ative um alarme. Deve aparecer a mensagem "Alarme agendado no Android".
3. Toque no sino, bloqueie a tela e espere 8 segundos.
