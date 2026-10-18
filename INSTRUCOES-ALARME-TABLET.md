# Alarmes no tablet — passo a passo

O código dos alarmes (src/lib/alarmes.ts) já está pronto. O que faltava era o **aplicativo Android** (APK) que o Capacitor precisa para agendar alarmes nativos. Este pacote gera o APK de graça, pelo GitHub, sem Lovable e sem Android Studio.

## 1. Colocar os arquivos no GitHub
No repositório do projeto (o mesmo conectado ao Lovable), adicione/substitua:
- `capacitor.config.ts` (substituir)
- `www/index.html` (novo)
- `scripts/android.mjs` (novo)
- `.github/workflows/build-android.yml` (novo)

Pelo site do GitHub: **Add file → Create new file**, digite o caminho completo no nome (ex.: `.github/workflows/build-android.yml`) e cole o conteúdo.

## 2. Gerar o APK
1. No GitHub: aba **Actions** → **Build APK Android (Central)** → **Run workflow**.
2. Aguarde ~5–8 min. Ao terminar, abra a execução e baixe **central-apk** (em *Artifacts*). Descompacte: dentro está `app-debug.apk`.

## 3. Instalar no tablet
1. Passe o `app-debug.apk` para o tablet (Drive, e-mail, cabo).
2. Abra o arquivo e permita "instalar apps desconhecidos" quando pedir.
3. Se já havia uma versão instalada com assinatura diferente, desinstale antes (nada se perde: os dados ficam na nuvem).

## 4. Configurar no tablet (essencial)
1. Abra o app **Central** e entre com **e-mail e senha** (o login com Google costuma ser bloqueado dentro de apps).
2. Menu **Alarmes** → toque em **Permitir** (notificações) e confira **Alarmes exatos: Permitido**.
3. Ative o alarme (os alarmes iniciais vêm desativados) e toque no sino para **testar o som**.
4. Ajustes do Android: Configurações → Apps → Central →
   - Bateria: **Sem restrições** (Samsung: tire o app de "apps em suspensão"; Xiaomi/Huawei: ative "Início automático").
   - Notificações: confirme que o canal "Alarmes" está com som ligado.
   - Volume de notificações do tablet alto e **Não Perturbe** com exceção para o app.

## 5. Observações
- O app abre o site publicado (`https://org-compass-pro.lovable.app`). Para outro endereço, rode o workflow informando `app_url`.
- Alterações no site só aparecem no tablet depois de **publicadas**. Mudanças nos alarmes nativos exigem gerar novo APK.
- Os alarmes agendados tocam mesmo com o app fechado e sem internet.
