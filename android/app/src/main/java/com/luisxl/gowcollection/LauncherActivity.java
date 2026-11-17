package com.luisxl.gowcollection;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// A tela de entrada no padrão de tela inicial de console: a arte do jogo em foco ocupa a tela, os jogos numa fileira de
// cartões no alto, o logotipo, uma linha de informação e o botão Jogar embaixo à esquerda, dicas de botão à direita.
// A arte vem da pasta do jogo (data/ui-arte e o LiveArea do game/PCSA00126); nada do jogo vai no APK.
public final class LauncherActivity extends Activity {
    private static final int PEDIR_ZIP = 7, PEDIR_PASTA = 8, PEDIR_PKG = 9, PEDIR_RIF = 10;
    static final String URL_LIVEPIX = "https://livepix.gg/luisxl15",
            URL_PAYPAL = "https://www.paypal.com/donate/?business=7MWMRNN92Z464&no_recurring=0&item_name=God+of+War+HD+Collection",
            URL_GITHUB = "https://github.com/luisxl15";
    private static boolean aberturaMostrada; // a abertura sai uma vez por vida do processo (voltar do jogo não repete)
    private Tela tela;
    // a tela da LauncherActivity que está aberta agora: a instalação (vários minutos) pode terminar depois de o
    // Android recriar a activity, e o aviso de pronto tem de chegar à tela nova, não à antiga
    static volatile Tela telaAtual;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Pastas.carregar(this);
        tela = new Tela(this);
        telaAtual = tela;
        if (Build.VERSION.SDK_INT >= 29) tela.setForceDarkAllowed(false); // o modo escuro da One UI clareava o preto
        setContentView(tela);
        telaCheia();
        if (!aberturaMostrada) {
            aberturaMostrada = true;
            tela.comecarAbertura();
        }
        Atualizador.tratar(this, getIntent());
        // Android 13+: o gesto e a tecla de voltar vêm por aqui (o onBackPressed nem é chamado com o
        // enableOnBackInvokedCallback do manifesto): fecha a aba aberta; só na tela principal o app vai para o fundo
        if (Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if (!tela.voltar()) moveTaskToBack(true);
            });
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        Atualizador.tratar(this, i); // o resultado da instalação da atualização
    }

    // o PKG ou o work.bin pelo seletor de arquivos (quando a busca não os acha)
    void escolherArquivo(boolean pkg) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, pkg ? PEDIR_PKG : PEDIR_RIF);
        } catch (Exception e) {
            Toast.makeText(this, Tr.t("Sem seletor de arquivos neste celular"), Toast.LENGTH_LONG).show();
        }
    }

    // caminho do arquivo escolhido (com o acesso a todos os arquivos o lançador lê pelo caminho, sem copiar 3,3 GB)
    static String caminhoDoc(Uri u) {
        try {
            String id = android.provider.DocumentsContract.getDocumentId(u);
            if (id.startsWith("raw:")) return id.substring(4);
            if (id.contains(":") && !id.startsWith("msf:")) return Pastas.caminhoDaArvore(id);
        } catch (Exception ignorada) {
            // outro provedor
        }
        return null;
    }

    void abrirLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, Tr.t("Não foi possível abrir o link") + ": " + url, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tela.conferir();
    }

    @Override
    public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        if (foco) telaCheia();
    }

    @Override
    public void onBackPressed() {
        if (!tela.voltar()) super.onBackPressed();
    }

    private void telaCheia() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    static boolean temAcesso() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    void pedirAcesso() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    void jogar(String principal) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(GameActivity.EXTRA_PRINCIPAL, principal);
        // tarefa nova: a GameActivity é singleTask e, sem isso, o Android reabria a antiga com o pedido antigo
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // o seletor de pastas do Android, para apontar a pasta do jogo quando a busca não a acha
    void escolherPasta() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PEDIR_PASTA);
        } catch (Exception e) {
            Toast.makeText(this, Tr.t("Sem seletor de pastas neste celular"), Toast.LENGTH_LONG).show();
        }
    }

    void escolherZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            startActivityForResult(i, PEDIR_ZIP);
        } catch (Exception e) {
            Toast.makeText(this, Tr.t("Sem seletor de arquivos: copie o .zip para a pasta drivers do jogo"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (resultado != RESULT_OK || dados == null || dados.getData() == null) return;
        if (pedido == PEDIR_PKG || pedido == PEDIR_RIF) {
            String cam = caminhoDoc(dados.getData());
            File f = cam != null ? new File(cam) : null;
            if (f == null || !f.isFile()) {
                Toast.makeText(this, Tr.t("Abra o arquivo pelo armazenamento do celular (não por Recentes ou Downloads)"), Toast.LENGTH_LONG).show();
                return;
            }
            if (pedido == PEDIR_PKG) tela.pkgEscolhido = f;
            else tela.rifEscolhido = f;
            tela.invalidate();
            return;
        }
        if (pedido == PEDIR_PASTA) { // a pasta do jogo escolhida à mão (um celular que não a achou sozinho)
            try {
                String cam = Pastas.caminhoDaArvore(android.provider.DocumentsContract.getTreeDocumentId(dados.getData()));
                Pastas.guardar(this, cam);
                String f = Pastas.faltando(new File(cam));
                Toast.makeText(this, f == null ? Tr.t("Pasta do jogo: ") + cam : Tr.t("Essa pasta não tem: ") + f, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, Tr.t("Não deu para usar essa pasta"), Toast.LENGTH_LONG).show();
            }
            tela.conferir();
            return;
        }
        if (pedido != PEDIR_ZIP) return;
        final Uri u = dados.getData();
        new Thread(() -> {
            String msg;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                String id = Drivers.instalarZip(this, in, String.valueOf(u.getLastPathSegment()));
                Drivers.escolherManual(this, id);
                msg = Tr.t("Instalado e em uso no próximo jogo: ") + Drivers.rotulo(this, id);
            } catch (Exception e) {
                msg = Tr.t("Não instalou: ") + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> {
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                tela.conferir();
            });
        }).start();
    }

    // ------------------------------------------------------------------------------------------------
    private static final class Tela extends View {
        private static final String[] NOMES = {"God of War Collection", "God of War", "God of War II"};
        private static final String[] CURTOS = {"Collection", "God of War", "God of War II"};
        private final RectF dicaX = new RectF(), dicaO = new RectF(); // as dicas também são botões
        private static final String[] META = {Tr.t("Os dois jogos remasterizados  ·  PS Vita"), Tr.t("God of War  ·  Capítulo I  ·  2005"),
                Tr.t("God of War II  ·  Capítulo II  ·  2007")};
        private static final String[] LIBS = {"libvt_eboot.so", "libvt_gow1.so", "libvt_gow2.so"};
        // onde cada arte enquadra (0..1) ao cobrir a tela e no cartão
        private static final float[] HEROI_AX = {0.70f, 0.50f, 0.50f}, HEROI_AY = {0.40f, 0.15f, 0.20f};
        private static final float[] CARTAO_AX = {0.5f, 0.78f, 0.22f};
        private static final int CREME = 0xFFF5F0E6, TINTA = 0xFF14100E, TEXTO = 0xFFE8E2D6, AVISO = 0xFFF0C674;
        // focos: cartões (0..2), atalhos no alto (11 GitHub, 3 troféus, 4 idioma, 5 tela, 6 controles, 7 driver), botões
        // (8 Jogar, 9 Controles, 10 Apoie o projeto)
        private static final int F_TROFEUS = 3, F_IDIOMA = 4, F_TELA = 5, F_CONTROLES = 6, F_DRIVER = 7, F_JOGAR = 8, F_PREVIA = 9,
                F_APOIO = 10, F_GITHUB = 11;
        private static final int[] ALTO = {0, 1, 2, F_GITHUB, F_TROFEUS, F_IDIOMA, F_TELA, F_CONTROLES, F_DRIVER},
                BAIXO = {F_JOGAR, F_PREVIA, F_APOIO};
        private static final int[] COR_NIVEL = {0xFFD08B4C, 0xFFC6CBD2, 0xFFE8C45C, 0xFF9FC6F0}; // bronze, prata, ouro, platina

        private final LauncherActivity act;
        private final float dp;
        private final Typeface medio = Typeface.create("sans-serif-medium", Typeface.NORMAL), normal = Typeface.create("sans-serif", Typeface.NORMAL);
        private int foco = 0, jogo = 0;
        private boolean drivers;
        private String falta; // null: pronto para jogar
        private boolean acesso;
        private File raiz;
        private final Bitmap[] heroi = new Bitmap[3], cartao = new Bitmap[3];
        private Bitmap logo, sombraLogo;
        private boolean artesLidas, artesDaPasta; // artesDaPasta: lidas com a pasta do jogo completa
        // fundo: as artes trocam a cada CICLO s com fade cruzado de FADE s; escolher um jogo põe a dele na hora
        private static final float CICLO = 5f, FADE = 1.2f;
        private int fundo = 0, fundoAntes = -1;
        private float troca = 1f;            // 0..1 do fade da arte nova
        private float noFundo;               // segundos na arte atual (o zoom lento recomeça com ela)
        private float noAntes;
        private float t;
        private long ultimoQuadro;           // relógio real: a tela de 120 Hz não pode andar no dobro
        // o relógio da tela é o Choreographer, não um ValueAnimator: com a "escala de duração do Animator" desligada
        // (opções do desenvolvedor, economia de bateria ou "remover animações") o ValueAnimator não anda, e a
        // abertura ficava parada no primeiro quadro (tela preta no lançador)
        private boolean rodando;
        private final android.view.Choreographer.FrameCallback relogio = new android.view.Choreographer.FrameCallback() {
            @Override
            public void doFrame(long nanos) {
                if (!rodando) return;
                quadro();
                android.view.Choreographer.getInstance().postFrameCallback(this);
            }
        };
        private final RectF[] alvo = new RectF[12];
        // apoie o projeto (LivePix e PayPal, com o QR do LivePix) e a abertura (logo, depois o aviso)
        private boolean apoio;
        private final RectF[] cartaoApoio = {new RectF(), new RectF()};
        private Bitmap qr;
        private float aberturaT = -1; // segundos desde o começo da abertura; -1 = sem abertura
        private boolean telaOp; // proporção da imagem
        private final List<RectF> linhasA = new ArrayList<>();
        // troféus e idioma (telas sobre a arte, como a de drivers)
        private boolean trofeus, idiomas;
        private List<Trofeus.Conjunto> conjuntos = new ArrayList<>();
        private int aba, linhaT, topoT;
        private boolean confirmarZerar;
        private final List<RectF> abas = new ArrayList<>(), linhasT = new ArrayList<>();

        // tela de drivers: {texto, ação, detalhe}
        private final List<String[]> itens = new ArrayList<>();
        private final List<RectF> linhas = new ArrayList<>();
        private int escolhido, topo;
        private String statusAuto = "", ativo = "", gpuLinha = "";
        private volatile boolean autoRodando;

        Tela(LauncherActivity a) {
            super(a);
            act = a;
            dp = a.getResources().getDisplayMetrics().density;
            setFocusable(true);
            setFocusableInTouchMode(true);
            for (int i = 0; i < alvo.length; i++) alvo[i] = new RectF();
            try (InputStream in = a.getAssets().open("ui/livepix_qr.png")) {
                qr = BitmapFactory.decodeStream(in);
            } catch (Exception ignorada) {
                // sem o QR: só os botões
            }
        }

        private void quadro() {
            {
                long agora = System.nanoTime();
                float dt = ultimoQuadro == 0 ? 0f : Math.min(0.1f, (agora - ultimoQuadro) / 1e9f);
                ultimoQuadro = agora;
                t += dt;
                if (aberturaT >= 0) avancarAbertura(dt);
                noFundo += dt;
                noAntes += dt;
                if (troca < 1f) troca = Math.min(1f, troca + dt / FADE);
                if (noFundo >= CICLO && !painel()) trocarFundo(proximoFundo(), true);
                invalidate();
            }
        }

        // ---------------------------------------------------------------- estado e arte
        void conferir() {
            acesso = temAcesso();
            raiz = Pastas.raiz();
            falta = acesso ? Pastas.faltando(raiz) : "acesso";
            conferirAtualizacao();
            if (acesso) {
                if (Drivers.importarDaPasta(act, raiz) > 0)
                    Toast.makeText(act, Tr.t("Drivers novos da pasta drivers instalados"), Toast.LENGTH_SHORT).show();
                if (!artesLidas || (falta == null && !artesDaPasta)) lerArtes();
            }
            if (Drivers.MODO_AUTO.equals(Drivers.modo(act))) rodarAuto(false); // como o checkOnOpen do DroidDeck
            if (drivers) montarDrivers();
            invalidate();
        }

        private void lerArtes() {
            artesLidas = true;
            artesDaPasta = falta == null;
            File ui = new File(raiz, "data/ui-arte"), sys = new File(Pastas.jogo(raiz), "sce_sys");
            int larg = Math.max(1280, getResources().getDisplayMetrics().widthPixels);
            Bitmap chave = abrir(new File(ui, "chave.jpg"), larg);
            heroi[0] = chave;
            heroi[1] = abrir(new File(ui, "heroi_gow1.jpg"), larg);
            heroi[2] = abrir(new File(ui, "heroi_gow2.jpg"), larg);
            Bitmap icone = abrir(new File(sys, "icon0.png"), 512);
            cartao[0] = icone != null ? icone : quadrado(chave, 0.5f);
            cartao[1] = heroi[1] != null ? quadrado(heroi[1], CARTAO_AX[1]) : quadrado(chave, 0.30f);
            cartao[2] = heroi[2] != null ? quadrado(heroi[2], CARTAO_AX[2]) : quadrado(chave, 0.80f);
            for (int i = 1; i < 3; i++) if (heroi[i] == null) heroi[i] = chave;
            logo = abrir(new File(ui, "logo_collection.png"), 2048);
            if (logo == null) logo = abrir(new File(sys, "livearea/contents/gates/startup_english.png"), 1024);
            if (logo != null) {
                Paint p = new Paint();
                p.setMaskFilter(new BlurMaskFilter(Math.max(1, logo.getWidth() / 120f), BlurMaskFilter.Blur.NORMAL));
                sombraLogo = logo.extractAlpha(p, new int[2]);
            }
        }

        private static Bitmap abrir(File f, int maxLado) {
            if (!f.isFile()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxLado) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getPath(), o);
        }

        private static Bitmap quadrado(Bitmap b, float ax) {
            if (b == null) return null;
            int lado = Math.min(b.getWidth(), b.getHeight());
            int x = Math.round((b.getWidth() - lado) * ax), y = Math.round((b.getHeight() - lado) * 0.3f);
            return Bitmap.createScaledBitmap(Bitmap.createBitmap(b, x, y, lado, lado), 384, 384, true);
        }

        private void focar(int f) {
            if (f == foco) return;
            foco = f;
            if (f <= 2 && f != jogo) {
                jogo = f;
                trocarFundo(f, false);
            }
            invalidate();
        }

        // a próxima arte diferente da atual (sem as que faltam e repetiriam a arte-chave)
        private int proximoFundo() {
            for (int k = 1; k <= 3; k++) {
                int i = (fundo + k) % 3;
                if (heroi[i] != null && heroi[i] != heroi[fundo]) return i;
            }
            return fundo;
        }

        private void trocarFundo(int novo, boolean ciclo) {
            float vidaAntes = noFundo; // o zoom da que sai continua de onde estava
            noFundo = 0;
            if (novo == fundo || heroi[novo] == null) return;
            if (heroi[fundo] == heroi[novo]) { fundo = novo; return; }
            fundoAntes = fundo;
            noAntes = vidaAntes;
            fundo = novo;
            troca = 0f;
        }

        private void acionar(int f) {
            switch (f) {
                case 0: case 1: case 2: case F_JOGAR:
                    if (!acesso) { act.pedirAcesso(); return; }
                    if (falta != null) { // sem a pasta do jogo: a primeira instalação (PKG + work.bin; ou a pasta do PC)
                        abrirInstalacao();
                        return;
                    }
                    act.jogar(LIBS[jogo]);
                    return;
                case F_CONTROLES: // ícone do controle no alto: mapear os botões do controle físico
                    act.startActivity(new Intent(act, MapearControleActivity.class));
                    return;
                case F_PREVIA:
                    act.jogar(GameActivity.PREVIA);
                    return;
                case F_DRIVER:
                    abrirDrivers();
                    return;
                case F_TROFEUS:
                    abrirTrofeus();
                    return;
                case F_APOIO:
                    apoio = true;
                    escolhido = 0;
                    invalidate();
                    return;
                case F_GITHUB:
                    act.abrirLink(URL_GITHUB);
                    return;
                case F_TELA:
                    if (!acesso || raiz == null) { act.pedirAcesso(); return; }
                    telaOp = true;
                    escolhido = 0;
                    String asp = Opcoes.ler(raiz, "aspect", "auto");
                    for (int i = 0; i < Opcoes.PROPORCOES.length; i++) if (Opcoes.PROPORCOES[i][0].equals(asp)) escolhido = i;
                    invalidate();
                    return;
                case F_IDIOMA:
                    idiomas = true;
                    escolhido = 0;
                    for (int i = 0; i < Idioma.OPCOES.length; i++) if ((int) Idioma.OPCOES[i][0] == Idioma.atual(act)) escolhido = i;
                    invalidate();
                    return;
                default:
            }
        }

        // ---------------------------------------------------------------- desenho
        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (aberturaT >= 0) { abertura(c, w, h); return; }
            c.drawColor(0xFF0B0908);
            // fundo: a arte de antes saindo e a nova entrando (fade cruzado); cada uma com um zoom lento que começa nela
            float a = suave(troca);
            if (troca < 1f && fundoAntes >= 0 && heroi[fundoAntes] != null)
                desenharHeroi(c, p, heroi[fundoAntes], fundoAntes, w, h, zoom(noAntes), (int) (255 * (1 - a)));
            if (heroi[fundo] != null) desenharHeroi(c, p, heroi[fundo], fundo, w, h, zoom(noFundo), (int) (255 * a));
            veus(c, p, w, h);
            ondasVita(c, w, h, t, 0.75f); // as faixas de luz da abertura, por cima da arte
            if (drivers) { telaDrivers(c, w, h); return; }
            if (trofeus) { telaTrofeus(c, w, h); return; }
            if (idiomas) { telaIdiomas(c, w, h); return; }
            if (telaOp) { telaProporcao(c, w, h); return; }
            if (apoio) { telaApoio(c, w, h); return; }
            if (instalar) { telaInstalar(c, w, h); return; }
            cartoes(c, p);
            atalhos(c, w);
            destaque(c, p, w, h);
            dicas(c, w, h, Tr.t("Jogar"));
            barraAtualizacao(c, w);
        }

        private static float suave(float x) { return x * x * (3 - 2 * x); }

        // a arte um pouco mais viva: saturação +20% e contraste +8%
        private static final android.graphics.ColorMatrixColorFilter VIVO = vivo();

        private static android.graphics.ColorMatrixColorFilter vivo() {
            android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
            m.setSaturation(1.2f);
            float k = 1.08f, o = 128 * (1 - k);
            m.postConcat(new android.graphics.ColorMatrix(new float[]{k, 0, 0, 0, o, 0, k, 0, 0, o, 0, 0, k, 0, o, 0, 0, 0, 1, 0}));
            return new android.graphics.ColorMatrixColorFilter(m);
        }

        // 1.0 -> 1.05 ao longo da vida da arte na tela (CICLO + FADE)
        private static float zoom(float seg) { return 1f + 0.05f * Math.min(1f, seg / (CICLO + 2 * FADE)); }

        private void desenharHeroi(Canvas c, Paint p, Bitmap b, int i, int w, int h, float zoom, int alfa) {
            float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * zoom;
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            float x = (w - bw) * HEROI_AX[i], y = (h - bh) * HEROI_AY[i];
            p.setAlpha(alfa);
            p.setColorFilter(VIVO);
            c.drawBitmap(b, null, new RectF(x, y, x + bw, y + bh), p);
            p.setColorFilter(null);
            p.setAlpha(255);
        }

        // véus: embaixo (onde fica o texto), à esquerda e um leve no alto para os cartões
        private void veus(Canvas c, Paint p, int w, int h) {
            boolean claro = fundo != 0 && heroi[fundo] != heroi[0]; // as artes do GoW1/GoW2 são claras: véu da esquerda mais forte
            p.setShader(new LinearGradient(0, h * 0.45f, 0, h, new int[]{0x00060505, 0x66060505, 0xD0060505},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            int forte = claro ? 0xC8060505 : 0x99060505, meio = claro ? 0x5C060505 : 0x33060505;
            p.setShader(new LinearGradient(0, 0, w * 0.58f, 0, new int[]{forte, meio, 0x00060505}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new LinearGradient(0, 0, 0, h * 0.30f, 0x66060505, 0x00060505, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h * 0.30f, p);
            p.setShader(null);
        }

        private float margem() { return 64 * dp * Math.min(1f, getWidth() / (dp * 760)); }

        private void cartoes(Canvas c, Paint p) {
            float x = margem(), y0 = 24 * dp, grande = 80 * dp, normal = 66 * dp;
            for (int i = 0; i < 3; i++) {
                boolean f = i == jogo;
                float lado = f ? grande : normal, y = y0 + (grande - lado) / 2;
                RectF r = new RectF(x, y, x + lado, y + lado);
                Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
                s.setColor(0xFF000000);
                s.setShadowLayer(10 * dp, 0, 4 * dp, 0xAA000000);
                c.drawRoundRect(r, 12 * dp, 12 * dp, s);
                c.save();
                Path clip = new Path();
                clip.addRoundRect(r, 12 * dp, 12 * dp, Path.Direction.CW);
                c.clipPath(clip);
                if (cartao[i] != null) c.drawBitmap(cartao[i], null, r, p);
                else { Paint g = new Paint(); g.setColor(0xFF2A2522); c.drawRect(r, g); }
                if (!f) { Paint v = new Paint(); v.setColor(0x40000000); c.drawRect(r, v); }
                c.restore();
                if (f) {
                    Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                    b.setStyle(Paint.Style.STROKE);
                    b.setStrokeWidth(2.5f * dp);
                    b.setColor(foco == i ? 0xFFFFFFFF : 0xB3FFFFFF);
                    c.drawRoundRect(new RectF(r.left - 4 * dp, r.top - 4 * dp, r.right + 4 * dp, r.bottom + 4 * dp), 15 * dp, 15 * dp, b);
                }
                // o nome embaixo: dá para saber qual é qual sem focar
                Paint t = texto(f ? 13 : 12, f, f ? 0xFFFFFFFF : 0xCCFFFFFF);
                t.setShadowLayer(4 * dp, 0, 1 * dp, 0xCC000000);
                t.setTextAlign(Paint.Align.CENTER);
                float cxn = r.centerX(), larg = Math.max(lado + 20 * dp, t.measureText(CURTOS[i]));
                c.drawText(CURTOS[i], cxn, y0 + grande + 22 * dp, t);
                alvo[i].set(cxn - larg / 2, y0 - 6 * dp, cxn + larg / 2, y0 + grande + 30 * dp);
                x = Math.max(r.right, cxn + t.measureText(CURTOS[i]) / 2) + 22 * dp;
            }
        }

        private void atalhos(Canvas c, int w) {
            float cy = 24 * dp + 40 * dp, cx = w - margem() - 22 * dp, passo = 56 * dp;
            atalho(c, cx - 5 * passo, cy, F_GITHUB);
            atalho(c, cx - 4 * passo, cy, F_TROFEUS);
            atalho(c, cx - 3 * passo, cy, F_IDIOMA);
            atalho(c, cx - 2 * passo, cy, F_TELA);
            atalho(c, cx - passo, cy, F_CONTROLES);
            atalho(c, cx, cy, F_DRIVER);
            if ((foco >= F_TROFEUS && foco <= F_DRIVER) || foco == F_GITHUB) {
                String r = foco == F_GITHUB ? "GitHub  ·  luisxl15" : foco == F_TROFEUS ? Tr.t("Troféus") : foco == F_IDIOMA ? Tr.t("Idioma: ") + Idioma.nome(Idioma.atual(act))
                        : foco == F_TELA ? Tr.t("Tela: proporção e texturas HD")
                        : foco == F_CONTROLES ? Tr.t("Mapear botões do controle") : Tr.t("Driver de vídeo");
                Paint t = texto(13, true, 0xFFFFFFFF);
                t.setTextAlign(Paint.Align.RIGHT);
                t.setShadowLayer(4 * dp, 0, 1 * dp, 0xAA000000);
                c.drawText(r, w - margem(), cy + 46 * dp, t);
            }
        }

        private void atalho(Canvas c, float cx, float cy, int qual) {
            float r = 22 * dp;
            alvo[qual].set(cx - r, cy - r, cx + r, cy + r);
            boolean f = foco == qual;
            vidro(c, new RectF(cx - r, cy - r, cx + r, cy + r), r, f);
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeWidth(1.8f * dp);
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setStrokeJoin(Paint.Join.ROUND);
            s.setColor(0xFFFFFFFF);
            s.setShadowLayer(2 * dp, 0, 1 * dp, 0x66000000);
            float k = dp;
            if (qual == F_CONTROLES) { // DualSense: corpo com as pegadas longas, touchpad no meio, os dois analógicos
                Path g = new Path();
                g.moveTo(cx - 5 * k, cy - 7.5f * k);
                g.lineTo(cx + 5 * k, cy - 7.5f * k);
                g.cubicTo(cx + 9 * k, cy - 7.5f * k, cx + 12 * k, cy - 6 * k, cx + 13.5f * k, cy - 2 * k);
                g.cubicTo(cx + 15 * k, cy + 2.5f * k, cx + 16.5f * k, cy + 7 * k, cx + 15.5f * k, cy + 9.5f * k);
                g.cubicTo(cx + 14.5f * k, cy + 11.5f * k, cx + 11.5f * k, cy + 11 * k, cx + 10 * k, cy + 8.5f * k);
                g.cubicTo(cx + 8.5f * k, cy + 6 * k, cx + 6.5f * k, cy + 4.5f * k, cx + 4 * k, cy + 4.5f * k);
                g.lineTo(cx - 4 * k, cy + 4.5f * k);
                g.cubicTo(cx - 6.5f * k, cy + 4.5f * k, cx - 8.5f * k, cy + 6 * k, cx - 10 * k, cy + 8.5f * k);
                g.cubicTo(cx - 11.5f * k, cy + 11 * k, cx - 14.5f * k, cy + 11.5f * k, cx - 15.5f * k, cy + 9.5f * k);
                g.cubicTo(cx - 16.5f * k, cy + 7 * k, cx - 15 * k, cy + 2.5f * k, cx - 13.5f * k, cy - 2 * k);
                g.cubicTo(cx - 12 * k, cy - 6 * k, cx - 9 * k, cy - 7.5f * k, cx - 5 * k, cy - 7.5f * k);
                g.close();
                c.drawPath(g, s);
                Paint fino = new Paint(s);
                fino.setStrokeWidth(1.2f * dp);
                c.drawRoundRect(new RectF(cx - 4.5f * k, cy - 7.5f * k, cx + 4.5f * k, cy - 1.5f * k), 1.5f * k, 1.5f * k, fino); // touchpad
                c.drawCircle(cx - 4 * k, cy + 1.8f * k, 1.6f * k, fino); // analógicos
                c.drawCircle(cx + 4 * k, cy + 1.8f * k, 1.6f * k, fino);
                c.drawLine(cx - 11 * k, cy - 2 * k, cx - 7 * k, cy - 2 * k, fino); // cruz
                c.drawLine(cx - 9 * k, cy - 4 * k, cx - 9 * k, cy, fino);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
                d.setColor(0xFFFFFFFF);
                c.drawCircle(cx + 9 * k, cy - 4 * k, 1.1f * k, d); // △ ○ ✕ □ em pontos
                c.drawCircle(cx + 11 * k, cy - 2 * k, 1.1f * k, d);
                c.drawCircle(cx + 9 * k, cy, 1.1f * k, d);
                c.drawCircle(cx + 7 * k, cy - 2 * k, 1.1f * k, d);
            } else if (qual == F_TROFEUS) { // taça
                Path t = new Path();
                t.moveTo(cx - 7 * k, cy - 9 * k);
                t.lineTo(cx + 7 * k, cy - 9 * k);
                t.lineTo(cx + 7 * k, cy - 3 * k);
                t.cubicTo(cx + 7 * k, cy + 2 * k, cx + 3.5f * k, cy + 4.5f * k, cx, cy + 4.5f * k);
                t.cubicTo(cx - 3.5f * k, cy + 4.5f * k, cx - 7 * k, cy + 2 * k, cx - 7 * k, cy - 3 * k);
                t.close();
                c.drawPath(t, s);
                c.drawArc(new RectF(cx - 11.5f * k, cy - 8 * k, cx - 4.5f * k, cy - 1 * k), 90, 180, false, s);
                c.drawArc(new RectF(cx + 4.5f * k, cy - 8 * k, cx + 11.5f * k, cy - 1 * k), 270, 180, false, s);
                c.drawLine(cx, cy + 4.5f * k, cx, cy + 8 * k, s);
                c.drawLine(cx - 5 * k, cy + 9.5f * k, cx + 5 * k, cy + 9.5f * k, s);
            } else if (qual == F_TELA) { // tela larga com as setas de esticar
                c.drawRoundRect(new RectF(cx - 12 * k, cy - 7.5f * k, cx + 12 * k, cy + 7.5f * k), 2.5f * k, 2.5f * k, s);
                c.drawLine(cx - 7 * k, cy, cx + 7 * k, cy, s);
                c.drawLine(cx - 7 * k, cy, cx - 4 * k, cy - 3 * k, s);
                c.drawLine(cx - 7 * k, cy, cx - 4 * k, cy + 3 * k, s);
                c.drawLine(cx + 7 * k, cy, cx + 4 * k, cy - 3 * k, s);
                c.drawLine(cx + 7 * k, cy, cx + 4 * k, cy + 3 * k, s);
            } else if (qual == F_IDIOMA) { // globo
                c.drawCircle(cx, cy, 10 * k, s);
                c.drawOval(new RectF(cx - 4.5f * k, cy - 10 * k, cx + 4.5f * k, cy + 10 * k), s);
                c.drawLine(cx - 10 * k, cy, cx + 10 * k, cy, s);
                c.drawLine(cx - 8.5f * k, cy - 5 * k, cx + 8.5f * k, cy - 5 * k, s);
                c.drawLine(cx - 8.5f * k, cy + 5 * k, cx + 8.5f * k, cy + 5 * k, s);
            } else if (qual == F_GITHUB) { // a marca do GitHub: o gato recortado num disco
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
                d.setColor(0xFFFFFFFF);
                d.setShadowLayer(2 * dp, 0, 1 * dp, 0x66000000);
                c.drawCircle(cx, cy, 11 * k, d);
                d.clearShadowLayer();
                d.setColor(0xFF17191D);
                c.save();
                Path disco = new Path();
                disco.addCircle(cx, cy, 11 * k, Path.Direction.CW);
                c.clipPath(disco);
                c.drawOval(new RectF(cx - 5.6f * k, cy - 5.4f * k, cx + 5.6f * k, cy + 3.6f * k), d); // cabeça
                Path orelhas = new Path();
                orelhas.moveTo(cx - 5.4f * k, cy - 2.2f * k);
                orelhas.lineTo(cx - 5.0f * k, cy - 7.6f * k);
                orelhas.lineTo(cx - 1.6f * k, cy - 4.9f * k);
                orelhas.close();
                orelhas.moveTo(cx + 5.4f * k, cy - 2.2f * k);
                orelhas.lineTo(cx + 5.0f * k, cy - 7.6f * k);
                orelhas.lineTo(cx + 1.6f * k, cy - 4.9f * k);
                orelhas.close();
                c.drawPath(orelhas, d);
                c.drawRect(cx - 2.5f * k, cy + 1.5f * k, cx + 2.5f * k, cy + 12 * k, d); // pescoço até a borda
                Paint rabo = new Paint(Paint.ANTI_ALIAS_FLAG);
                rabo.setStyle(Paint.Style.STROKE);
                rabo.setStrokeWidth(1.5f * k);
                rabo.setStrokeCap(Paint.Cap.ROUND);
                rabo.setColor(0xFF17191D);
                Path br = new Path();
                br.moveTo(cx - 2.5f * k, cy + 6.2f * k);
                br.cubicTo(cx - 4.5f * k, cy + 6.4f * k, cx - 5.4f * k, cy + 4.2f * k, cx - 7.2f * k, cy + 3.8f * k);
                c.drawPath(br, rabo);
                c.restore();
            } else { // chip da GPU
                RectF q = new RectF(cx - 8 * k, cy - 8 * k, cx + 8 * k, cy + 8 * k);
                c.drawRoundRect(q, 3 * k, 3 * k, s);
                Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
                m.setColor(0xFFFFFFFF);
                c.drawRoundRect(new RectF(cx - 3.5f * k, cy - 3.5f * k, cx + 3.5f * k, cy + 3.5f * k), 1.5f * k, 1.5f * k, m);
                for (int d = -4; d <= 4; d += 4) {
                    c.drawLine(cx + d * k, cy - 8 * k, cx + d * k, cy - 11.5f * k, s);
                    c.drawLine(cx + d * k, cy + 8 * k, cx + d * k, cy + 11.5f * k, s);
                    c.drawLine(cx - 8 * k, cy + d * k, cx - 11.5f * k, cy + d * k, s);
                    c.drawLine(cx + 8 * k, cy + d * k, cx + 11.5f * k, cy + d * k, s);
                }
            }
        }

        // vidro: fundo translúcido com reflexo em cima e borda clara que some para baixo; em foco, mais claro e com aro
        private void vidro(Canvas c, RectF r, float raio, boolean foco) {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(0, r.top, 0, r.bottom, foco ? 0x52FFFFFF : 0x30FFFFFF, foco ? 0x24FFFFFF : 0x0FFFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, raio, raio, f);
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setStyle(Paint.Style.STROKE);
            b.setStrokeWidth(1.2f * dp);
            b.setShader(new LinearGradient(0, r.top, 0, r.bottom, foco ? 0xF0FFFFFF : 0x9EFFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP));
            RectF i = new RectF(r.left + 0.6f * dp, r.top + 0.6f * dp, r.right - 0.6f * dp, r.bottom - 0.6f * dp);
            c.drawRoundRect(i, raio, raio, b);
            if (foco) {
                Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
                a.setStyle(Paint.Style.STROKE);
                a.setStrokeWidth(2 * dp);
                a.setColor(0xFFFFFFFF);
                RectF o = new RectF(r.left - 4 * dp, r.top - 4 * dp, r.right + 4 * dp, r.bottom + 4 * dp);
                c.drawRoundRect(o, raio + 4 * dp, raio + 4 * dp, a);
            }
        }

        // Jogar: branco nítido com uma sombra macia; em foco, um halo branco e o aro (o botão principal do PS5)
        private void botaoBranco(Canvas c, RectF r, boolean ativo, boolean foco) {
            float raio = r.height() / 2;
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setColor(0x80000000);
            s.setMaskFilter(new BlurMaskFilter(10 * dp, BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(new RectF(r.left + 6 * dp, r.top + 10 * dp, r.right - 6 * dp, r.bottom + 6 * dp), raio, raio, s);
            if (foco) {
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setColor(0x99FFFFFF);
                g.setMaskFilter(new BlurMaskFilter(16 * dp, BlurMaskFilter.Blur.OUTER));
                c.drawRoundRect(r, raio, raio, g);
            }
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFFFFF, 0xFFE9E9EC, Shader.TileMode.CLAMP));
            if (!ativo) f.setAlpha(120);
            c.drawRoundRect(r, raio, raio, f);
            if (foco) {
                Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
                a.setStyle(Paint.Style.STROKE);
                a.setStrokeWidth(2 * dp);
                a.setColor(0xFFFFFFFF);
                RectF o = new RectF(r.left - 5 * dp, r.top - 5 * dp, r.right + 5 * dp, r.bottom + 5 * dp);
                c.drawRoundRect(o, raio + 5 * dp, raio + 5 * dp, a);
            }
        }

        // (o de ouro, guardado: ouro do logotipo em degradê, reflexo no alto; em foco, brasa em volta e aro)
        private void botaoOuro(Canvas c, RectF r, boolean ativo, boolean foco) {
            float raio = r.height() / 2;
            if (foco) {
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setColor(0xFFFF9A2E);
                g.setMaskFilter(new BlurMaskFilter(14 * dp, BlurMaskFilter.Blur.OUTER));
                c.drawRoundRect(r, raio, raio, g);
            }
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); // sombra: a pílula desfocada um pouco abaixo, sem borda escura em cima
            s.setColor(0x8C000000);
            s.setMaskFilter(new BlurMaskFilter(8 * dp, BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(new RectF(r.left + 6 * dp, r.top + 12 * dp, r.right - 6 * dp, r.bottom + 5 * dp), raio, raio, s);
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(0, r.top, 0, r.bottom, new int[]{0xFFFFEBB0, 0xFFF2C266, 0xFFD18E35, 0xFFA8661F},
                    new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            if (!ativo) f.setAlpha(110);
            c.drawRoundRect(r, raio, raio, f);
            Paint luz = new Paint(Paint.ANTI_ALIAS_FLAG); // reflexo: meia-pílula clara no alto
            luz.setShader(new LinearGradient(0, r.top, 0, r.centerY(), 0x80FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.save(); // o reflexo fica dentro da pílula (nas pontas ele passava da curva)
            Path forma = new Path();
            forma.addRoundRect(r, raio, raio, Path.Direction.CW);
            c.clipPath(forma);
            c.drawRect(r.left, r.top, r.right, r.centerY(), luz);
            c.restore();
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setStyle(Paint.Style.STROKE);
            b.setStrokeWidth(1 * dp);
            b.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFF6D8, 0xFF7A4510, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(r.left + 0.5f * dp, r.top + 0.5f * dp, r.right - 0.5f * dp, r.bottom - 0.5f * dp), raio, raio, b);
            if (foco) {
                Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
                a.setStyle(Paint.Style.STROKE);
                a.setStrokeWidth(2 * dp);
                a.setColor(0xFFFFFFFF);
                RectF o = new RectF(r.left - 4 * dp, r.top - 4 * dp, r.right + 4 * dp, r.bottom + 4 * dp);
                c.drawRoundRect(o, raio + 4 * dp, raio + 4 * dp, a);
            }
        }

        private void destaque(Canvas c, Paint p, int w, int h) {
            float gx = margem(), by = h - 64 * dp - 52 * dp;
            // linha de informação (ou o que falta para jogar) acima dos botões
            String linha = META[jogo];
            int cor = TEXTO;
            if (!acesso) { linha = Tr.t("O jogo lê a pasta do PC no armazenamento do celular: permita o acesso aos arquivos"); cor = AVISO; }
            else if (falta != null) { // o que falta e onde ele procurou
                linha = Tr.t("Copie a pasta \"") + Pastas.NOME + Tr.t("\" do PC para o celular  ·  falta: ") + falta + "  ·  " + raiz.getPath();
                cor = AVISO;
            }
            Paint m = texto(14, false, cor);
            m.setShadowLayer(3 * dp, 0, 1 * dp, 0xAA000000);
            float my = by - 24 * dp;
            c.drawText(cortar(linha, m, w - 2 * gx), gx, my, m);
            // logotipo real acima da linha
            if (logo != null) {
                float lw = w * 0.30f, lh = lw * logo.getHeight() / logo.getWidth();
                float ly = my - m.getTextSize() - 16 * dp - lh;
                if (sombraLogo != null) {
                    Paint s = new Paint(Paint.FILTER_BITMAP_FLAG);
                    s.setColor(0xCC000000);
                    c.drawBitmap(sombraLogo, null, new RectF(gx + 2 * dp, ly + 3 * dp, gx + 2 * dp + lw, ly + 3 * dp + lh), s);
                }
                c.drawBitmap(logo, null, new RectF(gx, ly, gx + lw, ly + lh), p);
            } else {
                Paint tl = texto(34, true, 0xFFFFFFFF);
                c.drawText("God of War Collection", gx, my - m.getTextSize() - 20 * dp, tl);
            }
            // Jogar: pílula de ouro, a ação principal (52 dp)
            boolean pronto = acesso && falta == null;
            float bw = (pronto ? 176 : 230) * dp, bh = 54 * dp;
            RectF j = new RectF(gx, by, gx + bw, by + bh);
            alvo[F_JOGAR].set(j);
            boolean ok = acesso && falta == null;
            botaoBranco(c, j, ok || !acesso, foco == F_JOGAR);
            String rot = !acesso ? Tr.t("Permitir acesso") : falta != null ? Tr.t("Instalar o jogo") : Tr.t("Jogar");
            Paint tj = texto(18, true, 0xFF0B0B0C);
            tj.setLetterSpacing(0.02f);
            float conteudo = (pronto ? 26 * dp : 0) + tj.measureText(rot), x0 = j.centerX() - conteudo / 2;
            if (pronto) {
                Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
                tri.setColor(0xFF0B0B0C);
                tri.setStrokeJoin(Paint.Join.ROUND);
                tri.setStyle(Paint.Style.FILL_AND_STROKE);
                tri.setStrokeWidth(2.5f * dp);
                Path pt = new Path();
                float cy = j.centerY();
                pt.moveTo(x0 + 1.5f * dp, cy - 8 * dp);
                pt.lineTo(x0 + 1.5f * dp, cy + 8 * dp);
                pt.lineTo(x0 + 14.5f * dp, cy);
                pt.close();
                c.drawPath(pt, tri);
            }
            c.drawText(rot, x0 + (pronto ? 26 * dp : 0), j.centerY() + tj.getTextSize() * 0.36f, tj);
            // secundário: vidro
            String sec = Tr.t("Controles na tela");
            Paint ts = texto(15, true, 0xFFFFFFFF);
            ts.setShadowLayer(2 * dp, 0, 1 * dp, 0x66000000);
            float sx = j.right + 18 * dp, sw = ts.measureText(sec) + 52 * dp;
            RectF s = new RectF(sx, by, sx + sw, by + bh);
            alvo[F_PREVIA].set(s);
            vidro(c, s, bh / 2, foco == F_PREVIA);
            c.drawText(sec, s.centerX() - ts.measureText(sec) / 2, s.centerY() + ts.getTextSize() * 0.35f, ts);
            // apoie o projeto: vidro com o coração
            String ap = Tr.t("Apoie o projeto");
            float ax = s.right + 14 * dp, aw = ts.measureText(ap) + 52 * dp + 20 * dp;
            RectF ar = new RectF(ax, by, ax + aw, by + bh);
            alvo[F_APOIO].set(ar);
            vidro(c, ar, bh / 2, foco == F_APOIO);
            float hx = ar.left + 26 * dp, hy = ar.centerY();
            coracao(c, hx, hy, 0.95f * dp);
            c.drawText(ap, hx + 14 * dp, ar.centerY() + ts.getTextSize() * 0.35f, ts);
        }

        private void coracao(Canvas c, float cx, float cy, float k) {
            Path co = new Path();
            co.moveTo(cx, cy + 7 * k);
            co.cubicTo(cx - 12 * k, cy - 1 * k, cx - 6.5f * k, cy - 10.5f * k, cx, cy - 4.2f * k);
            co.cubicTo(cx + 6.5f * k, cy - 10.5f * k, cx + 12 * k, cy - 1 * k, cx, cy + 7 * k);
            co.close();
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(0, cy - 8 * k, 0, cy + 7 * k, 0xFFFF7A8C, 0xFFE0304B, Shader.TileMode.CLAMP));
            f.setShadowLayer(3 * dp, 0, 1 * dp, 0x66000000);
            c.drawPath(co, f);
        }

        // foco do controle: aro branco por fora do botão
        private void anel(Canvas c, RectF r, boolean on) {
            if (!on) return;
            Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
            a.setStyle(Paint.Style.STROKE);
            a.setStrokeWidth(2 * dp);
            a.setColor(0xFFFFFFFF);
            RectF o = new RectF(r.left - 4 * dp, r.top - 4 * dp, r.right + 4 * dp, r.bottom + 4 * dp);
            c.drawRoundRect(o, o.height() / 2, o.height() / 2, a);
        }

        private Paint texto(float sp, boolean forte, int cor) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setTypeface(forte ? medio : normal);
            t.setTextSize(sp * dp);
            t.setColor(cor);
            return t;
        }

        // "✕ Jogar   ○ Voltar" embaixo à direita
        private void dicas(Canvas c, int w, int h, String acao) {
            float y = h - 40 * dp, x = w - margem();
            float xo = dica(c, x, y, false, Tr.t("Voltar"));
            dicaO.set(xo - 8 * dp, y - 24 * dp, x + 8 * dp, y + 24 * dp);
            float xx = dica(c, xo - 24 * dp, y, true, acao);
            dicaX.set(xx - 8 * dp, y - 24 * dp, xo - 16 * dp, y + 24 * dp);
        }

        // tocar na dica: ✕ faz o que o botão do controle faria, ○ volta
        private boolean tocarDica(float x, float y) {
            if (dicaO.contains(x, y)) {
                if (!voltar()) act.moveTaskToBack(true);
                return true;
            }
            if (!dicaX.contains(x, y)) return false;
            onKeyDown(KeyEvent.KEYCODE_BUTTON_A, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A));
            return true;
        }

        private float dica(Canvas c, float xDireita, float y, boolean cruz, String rot) {
            Paint t = texto(13, false, TEXTO);
            float tw = t.measureText(rot), x = xDireita - tw;
            c.drawText(rot, x, y + t.getTextSize() * 0.35f, t);
            float cx = x - 16 * dp, r = 10 * dp, k = 4 * dp;
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeWidth(1.5f * dp);
            s.setColor(TEXTO);
            c.drawCircle(cx, y, r, s);
            if (cruz) {
                c.drawLine(cx - k, y - k, cx + k, y + k, s);
                c.drawLine(cx + k, y - k, cx - k, y + k, s);
            } else {
                c.drawCircle(cx, y, k, s);
            }
            return cx - r;
        }

        // ---------------------------------------------------------------- drivers (critérios do DroidDeck: Drivers/DriverCatalog)
        private void abrirDrivers() {
            drivers = true;
            escolhido = itens.size(); // monta a partir do item em uso
            montarDrivers();
            invalidate();
        }

        private boolean painel() { return drivers || trofeus || idiomas || telaOp || apoio || instalar || aberturaT >= 0; }

        boolean voltar() {
            if (aberturaT >= 0) { pularAbertura(); return true; }
            if (instalar) {
                if (rodandoInst) return true; // não sai no meio: o Cancelar está no botão
                instalar = false;
                foco = F_JOGAR;
                invalidate();
                return true;
            }
            if (!painel()) return false;
            foco = drivers ? F_DRIVER : trofeus ? F_TROFEUS : telaOp ? F_TELA : apoio ? F_APOIO : F_IDIOMA;
            drivers = trofeus = idiomas = telaOp = apoio = false;
            invalidate();
            return true;
        }

        // ---------------------------------------------------------------- troféus
        private void abrirTrofeus() {
            if (!acesso || raiz == null) { act.pedirAcesso(); return; }
            conjuntos = Trofeus.ler(raiz, Idioma.atual(act));
            trofeus = true;
            aba = Math.min(aba, Math.max(0, conjuntos.size() - 1));
            linhaT = 0;
            topoT = 0;
            rolagemT = 0;
            seguirT = true;
            confirmarZerar = false;
            invalidate();
        }

        private void zerarAba() {
            if (aba >= conjuntos.size()) return;
            if (!confirmarZerar) { confirmarZerar = true; invalidate(); return; }
            Trofeus.Conjunto cj = conjuntos.get(aba);
            boolean ok = Trofeus.zerar(raiz, cj.npwr);
            Toast.makeText(act, ok ? Tr.t("Troféus de ") + cj.titulo + Tr.t(" zerados neste celular (o antigo ficou em unlocked.txt.bak)")
                    : Tr.t("Não deu para zerar"), Toast.LENGTH_LONG).show();
            confirmarZerar = false;
            int a = aba;
            abrirTrofeus();
            aba = a;
        }

        // grade de troféus: duas colunas no celular deitado, rola arrastando o dedo; o zerar fica no alto, à direita
        private float rolagemT, maxRolagemT, toqueX0, toqueY0, rolagemT0;
        private boolean seguirT = true, arrastandoT;
        private int colsT = 1;
        private final RectF zerarR = new RectF(), areaT = new RectF();

        private void telaTrofeus(Canvas c, int w, int h) {
            Paint v = new Paint();
            v.setColor(0xD0060505);
            c.drawRect(0, 0, w, h, v);
            float gx = margem(), topo = 14 * dp;
            Paint t = texto(22, true, 0xFFFFFFFF);
            c.drawText(Tr.t("Troféus"), gx, topo + 24 * dp + t.getTextSize() * 0.35f, t);
            abas.clear();
            zerarR.setEmpty();
            areaT.setEmpty();
            linhasT.clear();
            if (conjuntos.isEmpty()) {
                Paint m = texto(14, false, TEXTO);
                c.drawText(Tr.t("Nenhum troféu encontrado em game/PCSA00126/sce_sys/trophy"), gx, topo + 80 * dp, m);
                dicas(c, w, h, Tr.t("Selecionar"));
                return;
            }
            // abas (um jogo por aba, com o ícone e o progresso) na mesma linha do título
            float ax = gx + t.measureText(Tr.t("Troféus")) + 24 * dp, ay = topo, ah = 48 * dp;
            for (int i = 0; i < conjuntos.size(); i++) {
                Trofeus.Conjunto cj = conjuntos.get(i);
                Paint tn = texto(14, true, i == aba ? 0xFFFFFFFF : 0xB3FFFFFF);
                Paint tp = texto(11, false, i == aba ? 0xCCFFFFFF : 0x80FFFFFF);
                String pr = cj.ganhos() + Tr.t(" de ") + cj.lista.size() + "  ·  " + cj.porcento() + "%";
                float aw = 10 * dp + 32 * dp + 10 * dp + Math.max(tn.measureText(cj.titulo), tp.measureText(pr)) + 14 * dp;
                RectF r = new RectF(ax, ay, ax + aw, ay + ah);
                abas.add(r);
                Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                f.setColor(i == aba ? 0x26FFFFFF : 0x0FFFFFFF);
                c.drawRoundRect(r, 12 * dp, 12 * dp, f);
                if (cj.icone != null)
                    c.drawBitmap(cj.icone, null, new RectF(r.left + 10 * dp, r.top + 8 * dp, r.left + 42 * dp, r.top + 40 * dp),
                            new Paint(Paint.FILTER_BITMAP_FLAG));
                c.drawText(cj.titulo, r.left + 52 * dp, r.top + 21 * dp, tn);
                c.drawText(pr, r.left + 52 * dp, r.top + 37 * dp, tp);
                if (i == aba) { // barra de progresso embaixo da aba
                    Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                    b.setColor(0x33FFFFFF);
                    c.drawRoundRect(new RectF(r.left + 12 * dp, r.bottom - 5 * dp, r.right - 12 * dp, r.bottom - 2 * dp), 2 * dp, 2 * dp, b);
                    b.setColor(0xFFE8C45C);
                    float fim = r.left + 12 * dp + (r.width() - 24 * dp) * cj.porcento() / 100f;
                    c.drawRoundRect(new RectF(r.left + 12 * dp, r.bottom - 5 * dp, fim, r.bottom - 2 * dp), 2 * dp, 2 * dp, b);
                }
                ax = r.right + 10 * dp;
            }
            Trofeus.Conjunto cj = conjuntos.get(aba);
            final int n = cj.lista.size();
            // zerar: botão no alto à direita (o teclado chega nele com linhaT == n)
            {
                String zt = confirmarZerar ? Tr.t("Toque de novo para zerar") : Tr.t("Zerar troféus");
                Paint zp = texto(13, true, confirmarZerar ? TINTA : AVISO);
                float zw = zp.measureText(zt) + 32 * dp, zx = Math.max(ax + 8 * dp, w - gx - zw);
                zerarR.set(zx, ay + 8 * dp, zx + zw, ay + ah - 8 * dp);
                Paint zf = new Paint(Paint.ANTI_ALIAS_FLAG);
                zf.setColor(confirmarZerar ? AVISO : 0x14FFFFFF);
                c.drawRoundRect(zerarR, zerarR.height() / 2, zerarR.height() / 2, zf);
                if (linhaT == n) {
                    zf.setStyle(Paint.Style.STROKE);
                    zf.setStrokeWidth(2 * dp);
                    zf.setColor(0xFFFFFFFF);
                    c.drawRoundRect(zerarR, zerarR.height() / 2, zerarR.height() / 2, zf);
                }
                c.drawText(zt, zerarR.left + 16 * dp, zerarR.centerY() + zp.getTextSize() * 0.36f, zp);
            }
            // a grade
            float y0 = ay + ah + 12 * dp, baixo = h - 58 * dp;
            areaT.set(0, y0, w, baixo);
            colsT = (w - 2 * gx) >= 600 * dp ? 2 : 1;
            float gap = 10 * dp, x0 = gx - 12 * dp, largura = w - 2 * gx + 24 * dp;
            float cw = (largura - gap * (colsT - 1)) / colsT, alt = 52 * dp;
            int linhas = (n + colsT - 1) / colsT;
            maxRolagemT = Math.max(0, linhas * alt - (baixo - y0));
            if (seguirT && linhaT < n) { // o teclado puxa a seleção para a vista
                float ly = (linhaT / colsT) * alt;
                if (ly < rolagemT) rolagemT = ly;
                if (ly + alt > rolagemT + (baixo - y0)) rolagemT = ly + alt - (baixo - y0);
            }
            seguirT = false;
            rolagemT = Math.max(0, Math.min(rolagemT, maxRolagemT));
            c.save();
            c.clipRect(0, y0, w, baixo);
            for (int i = 0; i < n; i++) {
                int col = i % colsT, row = i / colsT;
                float ly = y0 + row * alt - rolagemT, lx = x0 + col * (cw + gap);
                RectF r = new RectF(lx, ly, lx + cw, ly + alt - 6 * dp);
                if (r.bottom < y0 || r.top > baixo) { linhasT.add(new RectF()); continue; }
                linhasT.add(r);
                Trofeus.Trofeu tr = cj.lista.get(i);
                Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                f.setColor(i == linhaT ? 0x29FFFFFF : 0x0DFFFFFF);
                c.drawRoundRect(r, 10 * dp, 10 * dp, f);
                if (i == linhaT) {
                    f.setColor(0xFFFFFFFF);
                    c.drawRoundRect(new RectF(r.left, r.top + 10 * dp, r.left + 3 * dp, r.bottom - 10 * dp), 2 * dp, 2 * dp, f);
                }
                float is = 34 * dp;
                RectF ir = new RectF(r.left + 10 * dp, r.top + (r.height() - is) / 2, r.left + 10 * dp + is, r.top + (r.height() + is) / 2);
                Paint ip = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                if (!tr.ganho) ip.setAlpha(70);
                if (tr.icone != null && (tr.ganho || !tr.oculto)) c.drawBitmap(tr.icone, null, ir, ip);
                else { Paint q = new Paint(Paint.ANTI_ALIAS_FLAG); q.setColor(0x22FFFFFF); c.drawRoundRect(ir, 6 * dp, 6 * dp, q); }
                int nivel = tr.tipo == 'P' ? 3 : tr.tipo == 'G' ? 2 : tr.tipo == 'S' ? 1 : 0;
                boolean mostra = tr.ganho || !tr.oculto;
                Paint nm = texto(14, true, tr.ganho ? 0xFFFFFFFF : 0xB3FFFFFF);
                Paint d = texto(11, false, tr.ganho ? 0xCCFFFFFF : 0x80FFFFFF);
                String rot = tr.ganho ? Tr.t("Ganho") : new String[]{Tr.t("Bronze"), Tr.t("Prata"), Tr.t("Ouro"), Tr.t("Platina")}[nivel];
                Paint u = texto(11, true, tr.ganho ? TINTA : COR_NIVEL[nivel]);
                float uw = u.measureText(rot) + 18 * dp, ux = r.right - 10 * dp - uw, uy = r.centerY();
                float tx = ir.right + 12 * dp, tw = ux - tx - 10 * dp;
                c.drawText(cortar(mostra ? tr.nome : Tr.t("Troféu oculto"), nm, tw), tx, r.top + 20 * dp, nm);
                c.drawText(cortar(mostra ? tr.detalhe : Tr.t("Continue jogando para descobrir"), d, tw), tx, r.top + 36 * dp, d);
                Paint ponto = new Paint(Paint.ANTI_ALIAS_FLAG);
                ponto.setColor(COR_NIVEL[nivel]);
                if (tr.ganho) c.drawRoundRect(new RectF(ux, uy - 10 * dp, ux + uw, uy + 10 * dp), 10 * dp, 10 * dp, ponto);
                else c.drawCircle(ux, uy, 3.5f * dp, ponto);
                c.drawText(rot, ux + 9 * dp, uy + u.getTextSize() * 0.35f, u);
            }
            c.restore();
            if (maxRolagemT > 0) { // barrinha de rolagem
                float trilho = baixo - y0, barra = Math.max(24 * dp, trilho * trilho / (trilho + maxRolagemT));
                float by = y0 + (trilho - barra) * rolagemT / maxRolagemT;
                Paint sb = new Paint(Paint.ANTI_ALIAS_FLAG);
                sb.setColor(0x55FFFFFF);
                c.drawRoundRect(new RectF(w - gx / 2 - 2 * dp, by, w - gx / 2 + 2 * dp, by + barra), 2 * dp, 2 * dp, sb);
            }
            if (n > 0 && cj.ganhos() >= n) { // tudo ganho (a pasta do PC): por que o aviso não aparece no jogo
                Paint m = texto(12, false, 0xB3FFFFFF);
                c.drawText(cortar(Tr.t("Todos já ganhos (vieram do PC): zere para ganhá-los de novo e ver o aviso no jogo"), m, w * 0.55f),
                        gx, h - 24 * dp, m);
            }
            dicas(c, w, h, Tr.t("Selecionar"));
        }

        // ---------------------------------------------------------------- idioma
        private final List<RectF> linhasI = new ArrayList<>();

        private void telaIdiomas(Canvas c, int w, int h) {
            Paint v = new Paint();
            v.setColor(0xC4060505);
            c.drawRect(0, 0, w, h, v);
            float gx = margem(), y = 40 * dp;
            Paint t = texto(22, true, 0xFFFFFFFF);
            c.drawText(Tr.t("Idioma"), gx, y + t.getTextSize() * 0.8f, t);
            Paint g = texto(14, false, TEXTO);
            c.drawText(Tr.t("O jogo usa o idioma se tiver os textos dele; os troféus também"), gx, y + 22 * dp + g.getTextSize() * 1.6f, g);
            float y0 = y + 76 * dp, alt = 56 * dp, largura = Math.min(w - 2 * gx, 720 * dp);
            int vis = Math.max(2, (int) ((h - y0 - 72 * dp) / alt));
            int topoI = Math.max(0, Math.min(escolhido - vis + 1, Idioma.OPCOES.length - vis));
            topoI = Math.max(0, topoI);
            linhasI.clear();
            int atual = Idioma.atual(act);
            for (int i = 0; i < Idioma.OPCOES.length; i++) {
                if (i < topoI || i >= topoI + vis) { linhasI.add(new RectF()); continue; }
                float ly = y0 + (i - topoI) * alt;
                RectF r = new RectF(gx - 12 * dp, ly, gx - 12 * dp + largura, ly + alt - 4 * dp);
                linhasI.add(r);
                boolean sel = i == escolhido;
                if (sel) {
                    Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                    f.setColor(0x1FFFFFFF);
                    c.drawRoundRect(r, 10 * dp, 10 * dp, f);
                    f.setColor(0xFFFFFFFF);
                    c.drawRoundRect(new RectF(r.left, r.top + 10 * dp, r.left + 3 * dp, r.bottom - 10 * dp), 2 * dp, 2 * dp, f);
                }
                Object[] o = Idioma.OPCOES[i];
                c.drawText((String) o[1], r.left + 20 * dp, r.top + 22 * dp, texto(16, true, sel ? 0xFFFFFFFF : 0xE6FFFFFF));
                c.drawText((boolean) o[2] ? Tr.t("Jogo e troféus") : Tr.t("Jogo (os troféus ficam em inglês)"), r.left + 20 * dp, r.top + 41 * dp,
                        texto(12, false, sel ? 0xCCFFFFFF : 0x99FFFFFF));
                if ((int) o[0] == atual) {
                    Paint u = texto(12, true, TINTA);
                    float uw = u.measureText(Tr.t("Em uso")) + 20 * dp, ux = r.right - 16 * dp - uw, uy = r.centerY();
                    Paint ch = new Paint(Paint.ANTI_ALIAS_FLAG);
                    ch.setColor(CREME);
                    c.drawRoundRect(new RectF(ux, uy - 11 * dp, ux + uw, uy + 11 * dp), 11 * dp, 11 * dp, ch);
                    c.drawText(Tr.t("Em uso"), ux + 10 * dp, uy + u.getTextSize() * 0.35f, u);
                }
            }
            dicas(c, w, h, Tr.t("Selecionar"));
        }

        // ---------------------------------------------------------------- tela: proporção
        private float proporcaoDaTela() {
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(m);
            return (float) Math.max(m.widthPixels, m.heightPixels) / Math.max(1, Math.min(m.widthPixels, m.heightPixels));
        }

        private void telaProporcao(Canvas c, int w, int h) {
            Paint v = new Paint();
            v.setColor(0xC4060505);
            c.drawRect(0, 0, w, h, v);
            float gx = margem(), y = 40 * dp;
            Paint t = texto(22, true, 0xFFFFFFFF);
            c.drawText(Tr.t("Tela"), gx, y + t.getTextSize() * 0.8f, t);
            float tela = proporcaoDaTela();
            String desta = String.format(java.util.Locale.US, "%.2f", tela * 9).replace(".00", "").replace('.', ',') + ":9";
            c.drawText(Tr.t("Este celular é ") + desta + Tr.t("  ·  vale na próxima abertura do jogo"), gx, y + 22 * dp + 14 * dp * 1.6f, texto(14, false, TEXTO));
            // aviso: o HUD/texto ainda se parte quando a imagem fica mais larga que 16:9
            {
                String av = Tr.t("Aumentar a proporção quebra os textos. Ainda em implementação.");
                Paint at = texto(13, true, 0xFF1A1206);
                float ay = y + 22 * dp + 14 * dp * 1.6f + 12 * dp, aw = at.measureText(av) + 44 * dp;
                RectF ar = new RectF(gx - 12 * dp, ay, gx - 12 * dp + Math.min(aw, w - 2 * gx + 24 * dp), ay + 26 * dp);
                Paint af = new Paint(Paint.ANTI_ALIAS_FLAG);
                af.setColor(0xFFF2B33D);
                c.drawRoundRect(ar, 13 * dp, 13 * dp, af);
                Paint ai = texto(13, true, 0xFF1A1206);
                c.drawText("!", ar.left + 14 * dp, ar.centerY() + at.getTextSize() * 0.36f, ai);
                c.drawText(cortar(av, at, ar.width() - 40 * dp), ar.left + 28 * dp, ar.centerY() + at.getTextSize() * 0.36f, at);
            }
            int n = Opcoes.PROPORCOES.length + 1; // + a linha das texturas HD
            float y0 = y + 112 * dp, alt = 56 * dp, largura = Math.min(w - 2 * gx, 720 * dp);
            int vis = Math.max(2, (int) ((h - y0 - 72 * dp) / alt));
            int topoA = Math.max(0, Math.min(escolhido - vis + 1, n - vis));
            linhasA.clear();
            String asp = Opcoes.ler(raiz, "aspect", "auto");
            for (int i = 0; i < n; i++) {
                if (i < topoA || i >= topoA + vis) { linhasA.add(new RectF()); continue; }
                float ly = y0 + (i - topoA) * alt;
                RectF r = new RectF(gx - 12 * dp, ly, gx - 12 * dp + largura, ly + alt - 4 * dp);
                linhasA.add(r);
                boolean sel = i == escolhido;
                if (sel) {
                    Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                    f.setColor(0x1FFFFFFF);
                    c.drawRoundRect(r, 10 * dp, 10 * dp, f);
                    f.setColor(0xFFFFFFFF);
                    c.drawRoundRect(new RectF(r.left, r.top + 10 * dp, r.left + 3 * dp, r.bottom - 10 * dp), 2 * dp, 2 * dp, f);
                }
                String nome, det, selo = null;
                if (i == n - 1) { // texturas HD: instalar depois ou apagar
                    boolean tem = texturasInstaladas();
                    nome = Tr.t("Texturas HD");
                    if (etapaTex != null) det = etapaTex + (totalTex > 0 ? "  " + Math.round(feitoTex * 100.0 / totalTex) + "%" : "");
                    else if (erroTex != null) det = erroTex;
                    else if (confirmarTex) det = Tr.t("Toque de novo para apagar as texturas HD (1,7 GB)");
                    else det = tem ? Tr.t("Instaladas: toque para apagar e liberar 1,7 GB") : Tr.t("Não instaladas: toque para baixar e instalar (+1,7 GB)");
                    if (tem && etapaTex == null) selo = Tr.t("Em uso");
                } else {
                    Object[] o = Opcoes.PROPORCOES[i];
                    float a = (float) o[2];
                    nome = (String) o[1];
                    if (a == 0) det = Tr.t("A tela inteira deste celular (") + desta + ")";
                    else if (Math.abs(a - tela) < 0.03f) det = Tr.t("A tela inteira deste celular");
                    else if (a < tela) det = Tr.t("Faixas pretas nas laterais");
                    else det = Tr.t("Mais larga que esta tela: faixas em cima e embaixo");
                    if (o[0].equals(asp)) selo = Tr.t("Em uso");
                }
                c.drawText(nome, r.left + 20 * dp, r.top + 22 * dp, texto(16, true, sel ? 0xFFFFFFFF : 0xE6FFFFFF));
                c.drawText(cortar(det, texto(12, false, 0), r.width() - 140 * dp), r.left + 20 * dp, r.top + 41 * dp,
                        texto(12, false, sel ? 0xCCFFFFFF : 0x99FFFFFF));
                if (selo != null) {
                    Paint u = texto(12, true, TINTA);
                    float uw = u.measureText(selo) + 20 * dp, ux = r.right - 16 * dp - uw, uy = r.centerY();
                    Paint ch = new Paint(Paint.ANTI_ALIAS_FLAG);
                    ch.setColor(CREME);
                    c.drawRoundRect(new RectF(ux, uy - 11 * dp, ux + uw, uy + 11 * dp), 11 * dp, 11 * dp, ch);
                    c.drawText(selo, ux + 10 * dp, uy + u.getTextSize() * 0.35f, u);
                }
            }
            dicas(c, w, h, Tr.t("Selecionar"));
        }

        // ---------------------------------------------------------------- texturas HD (o pacote opcional da release)
        private volatile String etapaTex, erroTex;
        private volatile long feitoTex, totalTex;
        private boolean confirmarTex;

        private boolean texturasInstaladas() {
            File d = new File(raiz, "data/texpack");
            String[] l = d.list();
            return l != null && l.length > 0;
        }

        private void acaoTexturas() {
            if (etapaTex != null) return; // já trabalhando
            erroTex = null;
            final File r = raiz;
            if (texturasInstaladas()) {
                if (!confirmarTex) { confirmarTex = true; invalidate(); return; }
                confirmarTex = false;
                etapaTex = Tr.t("Apagando as texturas HD");
                new Thread(() -> {
                    Atualizador.apagar(new File(r, "data/texpack"));
                    etapaTex = null;
                    postInvalidate();
                }, "apagar-texturas").start();
                invalidate();
                return;
            }
            final byte[] K = Instalador.chaveGuardada(act);
            if (K == null) { // pasta copiada do PC: sem a chave, as texturas vêm da pasta data/texpack do PC
                erroTex = Tr.t("Instalado pela pasta do PC: copie data/texpack do PC");
                invalidate();
                return;
            }
            etapaTex = Tr.t("Procurando a versão mais nova");
            new Thread(() -> {
                File trab = new File(r, ".texturas");
                Instalador.Aviso av = (e, f, t) -> { etapaTex = e; feitoTex = f; totalTex = t; postInvalidate(); };
                Instalador.Cancelar cn = () -> { };
                try {
                    Instalador.Release rel = Instalador.releaseComGrupo("opcional-texturas_hd");
                    if (rel == null) throw new Instalador.Erro(Tr.t("Ainda não há uma versão publicada no GitHub"));
                    Instalador.baixarGrupo(rel, "opcional-texturas_hd", trab, av, cn);
                    Instalador.instalar(Instalador.abrir(trab, "opcional-texturas_hd", K), K, r, Tr.t("Instalando as texturas HD"), av, cn);
                    Atualizador.apagar(trab);
                } catch (Exception e) {
                    erroTex = e.getMessage() != null ? e.getMessage() : e.toString();
                } finally {
                    etapaTex = null;
                    totalTex = 0;
                    postInvalidate();
                }
            }, "texturas").start();
            invalidate();
        }

        private void escolherProporcao() {
            if (escolhido == Opcoes.PROPORCOES.length) { acaoTexturas(); return; }
            confirmarTex = false;
            {
                Opcoes.gravar(raiz, "aspect", (String) Opcoes.PROPORCOES[escolhido][0]);
                Toast.makeText(act, Tr.t("Proporção ") + Opcoes.PROPORCOES[escolhido][1] + Tr.t(" (vale na próxima abertura do jogo)"), Toast.LENGTH_SHORT).show();
            }
            invalidate();
        }

        private void escolherIdioma() {
            Object[] o = Idioma.OPCOES[escolhido];
            Idioma.definir(act, (int) o[0]);
            if (acesso && raiz != null) Opcoes.gravar(raiz, "language", Idioma.traducao((int) o[0]));
            Toast.makeText(act, Tr.t("Idioma: ") + o[1] + Tr.t(" (vale na próxima abertura do jogo)"), Toast.LENGTH_SHORT).show();
            invalidate();
        }

        private void montarDrivers() {
            String antes = escolhido < itens.size() ? itens.get(escolhido)[1] : null;
            itens.clear();
            GpuInfo g = GpuInfo.detectar(act);
            gpuLinha = g.nome + (g.nome.equals(g.rotulo()) ? "" : "  ·  " + g.rotulo());
            boolean auto = Drivers.MODO_AUTO.equals(Drivers.modo(act));
            String and = Drivers.android(act);
            itens.add(new String[]{Tr.t("Automático"), "#auto", statusAuto.isEmpty()
                    ? Tr.t("Escolhe o driver certo para a ") + g.nome : statusAuto});
            itens.add(new String[]{Tr.t("Driver do aparelho"), Drivers.SISTEMA, Tr.t("O driver Vulkan que veio no celular")});
            DriverCatalog.Consulta q = DriverCatalog.guardada(act);
            List<DriverCatalog.Par> pares = DriverCatalog.pares(q == null ? new ArrayList<>() : q.assets);
            String rec = DriverCatalog.recomendado(g, pares, DriverCatalog.regras(act), Drivers.ruins(act), Build.VERSION.SDK_INT, GpuInfo.blob(act));
            java.util.Set<String> dePar = new java.util.HashSet<>();
            ativo = auto ? "#auto" : Drivers.SISTEMA.equals(and) ? Drivers.SISTEMA : "id:" + and;
            for (DriverCatalog.Par p : pares) {
                if (!p.completo() || !p.serve(g)) continue;
                String inst = DriverCatalog.instalado(act, p.arquivo());
                if (inst != null) dePar.add(inst);
                if (!auto && inst != null && inst.equals(and)) ativo = "par:" + p.chave;
                long mb = Math.max(1, p.arquivo().tamanho >> 20);
                itens.add(new String[]{p.nome + "  " + p.versao(), "par:" + p.chave,
                        (p.chave.equals(rec) ? Tr.t("Recomendado  ·  ") : "") + mb + " MB" + (inst != null ? Tr.t("  ·  instalado") : Tr.t("  ·  baixa ao escolher"))});
            }
            if (g.familia.adreno())
                for (String id : Drivers.EMBUTIDOS)
                    itens.add(new String[]{Drivers.rotulo(act, id), "id:" + id, Tr.t("Turnip que vem no app, funciona sem internet")});
            for (String id : Drivers.importados(act))
                if (!dePar.contains(id)) itens.add(new String[]{Drivers.rotulo(act, id), "id:" + id, Tr.t("Importado")});
            itens.add(new String[]{Tr.t("Importar pacote .zip"), "#importar", Tr.t("Pacote .zip de driver (AdrenoTools); ou copie o .zip para a pasta drivers do jogo")});
            itens.add(new String[]{Tr.t("Procurar atualizações"), "#procurar", q == null ? Tr.t("A lista de drivers ainda não foi baixada")
                    : Tr.t("Última consulta: ") + android.text.format.DateFormat.format("dd/MM HH:mm", q.quando)});
            if (!auto && !Drivers.SISTEMA.equals(and) && !Drivers.EMBUTIDOS.contains(and) && Drivers.instalado(act, and))
                itens.add(new String[]{Tr.t("Remover o driver em uso"), "#remover", Tr.t("Apaga ") + Drivers.rotulo(act, and) + Tr.t(" e volta ao automático")});
            escolhido = 0;
            String alvoAcao = antes != null ? antes : ativo;
            for (int i = 0; i < itens.size(); i++) if (itens.get(i)[1].equals(alvoAcao)) escolhido = i;
        }

        private void rodarAuto(boolean forcar) {
            if (autoRodando) return;
            autoRodando = true;
            new Thread(() -> {
                try {
                    Drivers.automatico(act, forcar, st -> post(() -> {
                        statusAuto = st;
                        if (drivers) montarDrivers();
                        invalidate();
                    }));
                } finally {
                    autoRodando = false;
                    post(() -> { if (drivers) montarDrivers(); invalidate(); });
                }
            }, "drivers-auto").start();
        }

        private void procurar() {
            if (Drivers.MODO_AUTO.equals(Drivers.modo(act))) { rodarAuto(true); return; }
            statusAuto = Tr.t("Procurando os drivers mais novos…");
            new Thread(() -> {
                String st;
                try {
                    DriverCatalog.atualizar(act, GpuInfo.detectar(act).familia.adreno());
                    st = "";
                } catch (Exception e) {
                    st = Tr.t("Não deu para consultar: ") + e.getMessage();
                }
                final String f = st;
                post(() -> { statusAuto = f; montarDrivers(); invalidate(); });
            }, "drivers-lista").start();
            montarDrivers();
        }

        private void usarPar(String chave) {
            DriverCatalog.Consulta q = DriverCatalog.guardada(act);
            for (DriverCatalog.Par p : DriverCatalog.pares(q == null ? new ArrayList<>() : q.assets))
                if (p.chave.equals(chave) && p.completo()) {
                    new Thread(() -> {
                        boolean ok = Drivers.instalarPar(act, p, false, st -> post(() -> { statusAuto = st; invalidate(); }));
                        post(() -> {
                            if (ok) Toast.makeText(act, Tr.t("Em uso no próximo jogo: ") + p.nome + " " + p.versao(), Toast.LENGTH_LONG).show();
                            montarDrivers();
                            invalidate();
                        });
                    }, "drivers-par").start();
                    return;
                }
        }

        private void acionarDriver() {
            String a = itens.get(escolhido)[1];
            switch (a) {
                case "#importar": act.escolherZip(); return;
                case "#procurar": procurar(); return;
                case "#auto":
                    Drivers.voltarAoAutomatico(act);
                    statusAuto = "";
                    rodarAuto(false);
                    montarDrivers();
                    break;
                case "#remover":
                    Drivers.remover(act, Drivers.android(act));
                    Drivers.voltarAoAutomatico(act);
                    rodarAuto(false);
                    montarDrivers();
                    break;
                default:
                    if (a.startsWith("par:")) { usarPar(a.substring(4)); return; }
                    String id = a.startsWith("id:") ? a.substring(3) : a;
                    Drivers.escolherManual(act, id);
                    Toast.makeText(act, Tr.t("Em uso no próximo jogo: ") + itens.get(escolhido)[0], Toast.LENGTH_SHORT).show();
                    montarDrivers();
            }
            invalidate();
        }

        // lista sobre a arte escurecida: linhas de 56 dp, a escolhida clara com a barra à esquerda, a em uso com o selo
        private void telaDrivers(Canvas c, int w, int h) {
            Paint v = new Paint();
            v.setColor(0xC4060505);
            c.drawRect(0, 0, w, h, v);
            float gx = margem(), y = 40 * dp;
            Paint t = texto(22, true, 0xFFFFFFFF);
            c.drawText(Tr.t("Driver de vídeo"), gx, y + t.getTextSize() * 0.8f, t);
            Paint g = texto(14, false, TEXTO);
            c.drawText(gpuLinha, gx, y + 22 * dp + g.getTextSize() * 1.6f, g);
            float y0 = y + 76 * dp, alt = 56 * dp, largura = Math.min(w - 2 * gx, 720 * dp);
            int vis = Math.max(2, (int) ((h - y0 - 72 * dp) / alt));
            if (escolhido < topo) topo = escolhido;
            if (escolhido >= topo + vis) topo = escolhido - vis + 1;
            topo = Math.max(0, Math.min(topo, Math.max(0, itens.size() - vis)));
            linhas.clear();
            for (int i = 0; i < itens.size(); i++) {
                if (i < topo || i >= topo + vis) { linhas.add(new RectF()); continue; }
                float ly = y0 + (i - topo) * alt;
                RectF r = new RectF(gx - 12 * dp, ly, gx - 12 * dp + largura, ly + alt - 4 * dp);
                linhas.add(r);
                boolean sel = i == escolhido;
                if (sel) {
                    Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                    f.setColor(0x1FFFFFFF);
                    c.drawRoundRect(r, 10 * dp, 10 * dp, f);
                    f.setColor(0xFFFFFFFF);
                    c.drawRoundRect(new RectF(r.left, r.top + 10 * dp, r.left + 3 * dp, r.bottom - 10 * dp), 2 * dp, 2 * dp, f);
                }
                Paint n = texto(16, true, sel ? 0xFFFFFFFF : 0xE6FFFFFF);
                Paint d = texto(12, false, sel ? 0xCCFFFFFF : 0x99FFFFFF);
                String det = itens.get(i)[2];
                float tx = r.left + 20 * dp;
                c.drawText(itens.get(i)[0], tx, r.top + 22 * dp, n);
                if (!det.isEmpty()) c.drawText(cortar(det, d, r.width() - 120 * dp), tx, r.top + 41 * dp, d);
                if (itens.get(i)[1].equals(ativo)) { // em uso
                    Paint u = texto(12, true, TINTA);
                    String em = Tr.t("Em uso");
                    float uw = u.measureText(em) + 20 * dp, ux = r.right - 16 * dp - uw, uy = r.centerY();
                    Paint ch = new Paint(Paint.ANTI_ALIAS_FLAG);
                    ch.setColor(CREME);
                    c.drawRoundRect(new RectF(ux, uy - 11 * dp, ux + uw, uy + 11 * dp), 11 * dp, 11 * dp, ch);
                    c.drawText(em, ux + 10 * dp, uy + u.getTextSize() * 0.35f, u);
                }
            }
            if (autoRodando) {
                Paint s = texto(12, false, AVISO);
                c.drawText(Tr.t("Verificando os drivers…"), gx, h - 70 * dp, s);
            }
            dicas(c, w, h, Tr.t("Selecionar"));
        }

        // ---------------------------------------------------------------- abertura
        // 1) o PS Vita animado no fundo azul com "Port feito e mantido exclusivamente por Luis Santos" (0 a 10,5 s);
        // 2) tela preta com o aviso contra cópias (de 3,5 s até FIM_AVISO, toque pula depois de 0,8 s na tela);
        // 3) fade para o lançador. Toque no logo vai direto ao aviso.
        private static final float FIM_LOGO = 10.5f, INICIO_AVISO = 10.8f, FIM_AVISO = 18.8f, SAIDA = 0.5f;

        void comecarAbertura() {
            aberturaT = 0;
            invalidate();
        }

        private void avancarAbertura(float dt) {
            aberturaT += dt;
            if (aberturaT >= FIM_AVISO + SAIDA) aberturaT = -1;
        }

        private void pularAbertura() {
            if (aberturaT < 0) return;
            if (aberturaT < FIM_LOGO) aberturaT = FIM_LOGO;
            else if (aberturaT >= INICIO_AVISO + 0.8f && aberturaT < FIM_AVISO) aberturaT = FIM_AVISO;
            invalidate();
        }

        private static int comAlfa(int cor, float a) {
            int al = Math.round(((cor >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
            return (cor & 0x00FFFFFF) | (al << 24);
        }

        private void abertura(Canvas c, int w, int h) {
            c.drawColor(0xFF000000);
            float T = aberturaT;
            if (T < FIM_LOGO) { aberturaVita(c, w, h, T); return; }
            if (T < INICIO_AVISO) return;
            float a = Math.min(1f, (T - INICIO_AVISO) / 0.6f);
            if (T > FIM_AVISO) a = Math.max(0f, 1f - (T - FIM_AVISO) / SAIDA);
            a = suave(a);
            // a letra do sistema do PS Vita não é livre: a Roboto Light é a mais parecida que o Android já traz
            android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            tp.setTextSize(Math.min(19 * dp, h * 0.05f));
            tp.setColor(comAlfa(0xFFFFFFFF, a));
            String texto = Tr.t("Se você baixou em qualquer outro lugar fora do repositório oficial do GitHub, CUIDADO: pode conter vírus!")
                    + "\n\n" + Tr.t("Não confie em APKVISION e em canais do YouTube que disponibilizam links com propagandas em sites duvidosos.");
            int largura = (int) Math.min(w - 2 * margem(), 640 * dp);
            android.text.StaticLayout lay = android.text.StaticLayout.Builder.obtain(texto, 0, texto.length(), tp, largura)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setLineSpacing(0, 1.3f).build();
            float y = (h - lay.getHeight()) / 2f - 14 * dp;
            c.save();
            c.translate((w - largura) / 2f, y);
            lay.draw(c);
            c.restore();
            Paint rod = new Paint(Paint.ANTI_ALIAS_FLAG);
            rod.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            rod.setTextAlign(Paint.Align.CENTER);
            rod.setTextSize(13 * dp);
            rod.setColor(comAlfa(0xB3FFFFFF, a));
            c.drawText("github.com/luisxl15", w / 2f, y + lay.getHeight() + 34 * dp, rod);
            if (T > INICIO_AVISO + 0.8f) {
                rod.setColor(comAlfa(0x73FFFFFF, a * (0.6f + 0.4f * (float) Math.sin(t * 3))));
                c.drawText(Tr.t("Toque para continuar"), w / 2f, h - 30 * dp, rod);
            }
        }

        // ---------------------------------------------------------------- a animação do PS Vita (primeira parte da abertura)
        // Fundo azul com as faixas de luz ondulando (a onda dos menus do PlayStation) e partículas subindo; o console
        // sobe em perspectiva, endireita e balança de leve; a tela acende com as bolhas da LiveArea, os botões de face
        // acendem um por um e um reflexo atravessa o corpo. Tudo em vetor, nada de imagem de fora.
        private final android.graphics.Camera camera3d = new android.graphics.Camera();
        private final android.graphics.Matrix matriz3d = new android.graphics.Matrix();

        private static float faixa(float x, float a, float b) { return Math.max(0f, Math.min(1f, (x - a) / (b - a))); }

        private static float saidaCubica(float x) { float u = 1 - x; return 1 - u * u * u; }

        private static float salto(float x) { // passa um pouco e volta (easeOutBack)
            float c1 = 1.4f, c3 = c1 + 1, u = x - 1;
            return 1 + c3 * u * u * u + c1 * u * u;
        }

        private void aberturaVita(Canvas c, int w, int h, float T) {
            float geral = T < 0.5f ? T / 0.5f : T > FIM_LOGO - 0.6f ? (FIM_LOGO - T) / 0.6f : 1f; // entra e sai do preto
            geral = suave(Math.max(0f, Math.min(1f, geral)));
            fundoAzul(c, w, h, T);
            T *= 0.8f; // as etapas do console andam mais devagar que o relógio; depois dele, o console fica flutuando
            // o console: tamanho pela tela, centro um pouco acima do meio (a frase vai embaixo)
            float cw = Math.min(w * 0.50f, h * 1.10f), ch = cw * 0.445f, cx = w / 2f, cy = h * 0.44f;
            float p = saidaCubica(faixa(T, 0.15f, 1.35f));
            float sobe = (1 - p) * h * 0.42f, escala = 0.80f + 0.20f * salto(faixa(T, 0.15f, 1.35f));
            // entra deitado e virado; depois gira devagar de um lado para o outro (vitrine) para mostrar as laterais
            float rx = 48 * (1 - p) + 14 + 3 * (float) Math.sin(T * 0.9f);
            float ry = -42 * (1 - p) + p * 26 * (float) Math.sin(T * 0.75f - 1.1f);
            // sombra no "chão"
            Paint sombra = new Paint(Paint.ANTI_ALIAS_FLAG);
            sombra.setShader(new android.graphics.RadialGradient(cx, cy + ch * 0.85f, cw * 0.55f, 0x66000814, 0x00000814, Shader.TileMode.CLAMP));
            c.save();
            c.scale(1f, 0.18f, cx, cy + ch * 0.85f);
            sombra.setAlpha((int) (255 * p));
            c.drawCircle(cx, cy + ch * 0.85f, cw * 0.55f, sombra);
            c.restore();
            console3d(c, cx, cy, cw, ch, T, sobe, escala, rx, ry);
            // a frase: branca, maior, sobre uma cápsula de vidro azul-escura (legível em cima das ondas)
            float fa = suave(faixa(T, 1.9f, 2.5f));
            if (fa > 0) {
                String frase = Tr.t("Port feito e mantido exclusivamente por Luis Santos \"DESANTA\"");
                Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
                sub.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                sub.setTextAlign(Paint.Align.CENTER);
                sub.setTextSize(Math.min(21 * dp, w * 0.026f));
                sub.setLetterSpacing(0.02f);
                float fy = cy + ch * 0.5f + h * 0.17f + (1 - fa) * 10 * dp;
                float pw = sub.measureText(frase) + 56 * dp, ph = sub.getTextSize() * 2.3f;
                RectF pil = new RectF(cx - pw / 2, fy - ph / 2, cx + pw / 2, fy + ph / 2);
                Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
                fundo.setShader(new LinearGradient(0, pil.top, 0, pil.bottom, comAlfa(0xFF0B2A78, 0.78f * fa), comAlfa(0xFF041445, 0.88f * fa),
                        Shader.TileMode.CLAMP));
                fundo.setShadowLayer(14 * dp, 0, 4 * dp, comAlfa(0xFF000820, 0.6f * fa));
                c.drawRoundRect(pil, ph / 2, ph / 2, fundo);
                Paint borda = new Paint(Paint.ANTI_ALIAS_FLAG);
                borda.setStyle(Paint.Style.STROKE);
                borda.setStrokeWidth(1.2f * dp);
                borda.setShader(new LinearGradient(0, pil.top, 0, pil.bottom, comAlfa(0xFFFFFFFF, 0.55f * fa), comAlfa(0xFF9FD8FF, 0.15f * fa),
                        Shader.TileMode.CLAMP));
                c.drawRoundRect(pil, ph / 2, ph / 2, borda);
                sub.setColor(comAlfa(0xFFFFFFFF, fa));
                sub.setShadowLayer(3 * dp, 0, 1 * dp, comAlfa(0xFF000000, 0.7f * fa));
                c.drawText(frase, cx, fy + sub.getTextSize() * 0.36f, sub);
            }
            // entrada e saída pelo preto
            if (geral < 1f) {
                Paint v = new Paint();
                v.setColor(comAlfa(0xFF000000, 1 - geral));
                c.drawRect(0, 0, w, h, v);
            }
        }

        // azul profundo com luz no meio, três faixas de luz ondulando e partículas subindo
        private void fundoAzul(Canvas c, int w, int h, float T) {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new android.graphics.RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.75f,
                    new int[]{0xFF1E5EE8, 0xFF0B2F9E, 0xFF04154F, 0xFF020A28}, new float[]{0f, 0.35f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, f);
            ondasVita(c, w, h, T, 1f);
            particulas(c, w, h, T);
        }

        // três faixas de luz ondulando (a onda dos menus do PlayStation); forca 0..1 na opacidade
        private void ondasVita(Canvas c, int w, int h, float T, float forca) {
            for (int k = 0; k < 3; k++) {
                Path onda = new Path();
                float base = h * (0.62f + 0.07f * k), amp = h * (0.06f + 0.02f * k), fase = T * (0.55f + 0.18f * k) + k * 1.7f;
                for (int i = 0; i <= 48; i++) {
                    float x = w * i / 48f;
                    float y = base + amp * (float) Math.sin(x / w * (2.2f + 0.5f * k) * Math.PI + fase)
                            + amp * 0.35f * (float) Math.sin(x / w * 5.3f * Math.PI - fase * 1.3f);
                    if (i == 0) onda.moveTo(x, y);
                    else onda.lineTo(x, y);
                }
                Paint brilho = new Paint(Paint.ANTI_ALIAS_FLAG);
                brilho.setStyle(Paint.Style.STROKE);
                brilho.setStrokeWidth(h * (0.05f - 0.012f * k));
                brilho.setColor(comAlfa(k == 1 ? 0x1E9FD8FF : 0x16FFFFFF, forca));
                brilho.setMaskFilter(new BlurMaskFilter(h * 0.03f, BlurMaskFilter.Blur.NORMAL));
                c.drawPath(onda, brilho);
                Paint fio = new Paint(Paint.ANTI_ALIAS_FLAG);
                fio.setStyle(Paint.Style.STROKE);
                fio.setStrokeWidth(Math.max(1f, 1.2f * dp));
                fio.setColor(comAlfa(k == 1 ? 0x669FD8FF : 0x40FFFFFF, forca));
                c.drawPath(onda, fio);
            }
        }

        private void particulas(Canvas c, int w, int h, float T) {
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); // partículas: posições fixas por índice, sobem em laço
            for (int i = 0; i < 26; i++) {
                float sx = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1, sv = 0.025f + 0.04f * ((i * 7) % 11) / 11f;
                float y = h * (1.05f - ((T * sv + i * 0.137f) % 1.1f)), x = w * sx + 12 * dp * (float) Math.sin(T * 0.8f + i);
                float r = (1.2f + (i % 3)) * dp, a = 0.25f + 0.35f * (float) Math.abs(Math.sin(T * 1.3f + i));
                pt.setColor(comAlfa(0xFFBFE3FF, a));
                c.drawCircle(x, y, r, pt);
            }
        }

        // o PS Vita (o primeiro modelo): corpo preto de pontas arredondadas com aro prata, tela 16:9 no meio, cruz e
        // analógico à esquerda, botões de face e analógico à direita, botão do meio embaixo à esquerda
        // ---------------------------------------------------------------- o PS Vita em 3D
        // O corpo é um sólido: fatias da silhueta empilhadas na profundidade da câmera 3D (de trás para a frente), com a
        // faixa prata no meio da espessura, como no primeiro modelo; os gatilhos saem do topo, atrás; cruz, botões,
        // botão do meio e analógicos saltam da face em alturas diferentes (o analógico tem haste e inclina). Girando, as
        // camadas andam com a perspectiva e as laterais aparecem.
        private float c3cx, c3cy, c3sobe, c3esc, c3rx, c3ry, c3dist;

        private void camada(Canvas c, float z) {
            camera3d.save();
            camera3d.setLocation(0, 0, -c3dist);
            camera3d.rotateX(c3rx);
            camera3d.rotateY(c3ry);
            camera3d.translate(0, 0, z);
            camera3d.getMatrix(matriz3d);
            camera3d.restore();
            matriz3d.preTranslate(-c3cx, -c3cy);
            matriz3d.postTranslate(c3cx, c3cy + c3sobe);
            matriz3d.postScale(c3esc, c3esc, c3cx, c3cy + c3sobe);
            c.concat(matriz3d);
        }

        private void console3d(Canvas c, float cx, float cy, float cw, float ch, float T, float sobe, float esc, float rx, float ry) {
            c3cx = cx; c3cy = cy; c3sobe = sobe; c3esc = esc; c3rx = rx; c3ry = ry;
            c3dist = cw * 2.4f / 72f; // a câmera a ~2,4 corpos de distância (a Camera conta em polegadas de 72 px)
            RectF corpo = new RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
            float rc = ch * 0.48f, D = ch * 0.24f;
            Path forma = new Path();
            forma.addRoundRect(corpo, rc, rc, Path.Direction.CW);
            float aL = aperto(T, 6), aR = aperto(T, 5);
            Paint pGat = new Paint(Paint.ANTI_ALIAS_FLAG);
            pGat.setShader(new LinearGradient(0, corpo.top - ch * 0.06f, 0, corpo.top + ch * 0.2f, 0xFF4A505A, 0xFF101216, Shader.TileMode.CLAMP));
            Paint pPrata = new Paint(Paint.ANTI_ALIAS_FLAG), pPreto = new Paint(Paint.ANTI_ALIAS_FLAG);
            pPrata.setShader(new LinearGradient(0, corpo.top, 0, corpo.bottom, new int[]{0xFFE2E6EC, 0xFF9BA2AD, 0xFF5F6670}, new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
            pPreto.setShader(new LinearGradient(0, corpo.top, 0, corpo.bottom, 0xFF2C3037, 0xFF050607, Shader.TileMode.CLAMP));
            final int N = 22;
            for (int i = N; i >= 1; i--) { // as fatias, de trás para a frente
                float t = i / (float) N, z = D * t;
                c.save();
                camada(c, z);
                if (t > 0.4f && t < 0.95f) { // os gatilhos L e R saem do topo na metade de trás
                    c.drawRoundRect(new RectF(corpo.left + cw * 0.06f, corpo.top - ch * 0.045f + aL * ch * 0.025f, corpo.left + cw * 0.22f,
                            corpo.top + ch * 0.2f), ch * 0.12f, ch * 0.12f, pGat);
                    c.drawRoundRect(new RectF(corpo.right - cw * 0.22f, corpo.top - ch * 0.045f + aR * ch * 0.025f, corpo.right - cw * 0.06f,
                            corpo.top + ch * 0.2f), ch * 0.12f, ch * 0.12f, pGat);
                }
                c.drawPath(forma, t > 0.32f && t < 0.68f ? pPrata : pPreto);
                c.restore();
            }
            // o brilho azul dos gatilhos apertados, no alto atrás
            for (int i = 0; i < 2; i++) {
                float a = i == 0 ? aL : aR;
                if (a <= 0) continue;
                c.save();
                camada(c, D * 0.7f);
                float gx = i == 0 ? corpo.left + cw * 0.14f : corpo.right - cw * 0.14f;
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setShader(new android.graphics.RadialGradient(gx, corpo.top, ch * 0.24f, comAlfa(0xFF6FB6FF, 0.75f * a), 0x006FB6FF, Shader.TileMode.CLAMP));
                c.drawCircle(gx, corpo.top, ch * 0.24f, g);
                c.restore();
            }
            // a face: preto brilhante, o brilho que acompanha o giro, o reflexo que passa, aro, tela, câmera
            c.save();
            camada(c, 0);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, corpo.top, 0, corpo.bottom, new int[]{0xFF33373F, 0xFF16181C, 0xFF07080A}, new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawPath(forma, p);
            c.save();
            c.clipPath(forma);
            Paint topo = new Paint(Paint.ANTI_ALIAS_FLAG);
            topo.setShader(new LinearGradient(0, corpo.top, 0, corpo.top + ch * 0.35f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(corpo.left, corpo.top, corpo.right, corpo.top + ch * 0.35f, topo);
            float bxGiro = cx - ry * cw * 0.03f; // a luz parada no mundo: ao girar, o brilho corre pela face
            Paint giro = new Paint(Paint.ANTI_ALIAS_FLAG);
            giro.setShader(new LinearGradient(bxGiro - cw * 0.25f, corpo.top, bxGiro + cw * 0.25f, corpo.bottom,
                    new int[]{0x00FFFFFF, 0x1CFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(corpo, giro);
            float u = T < 1.25f ? 0f : ((T - 1.25f) % 2.6f);
            if (u > 0 && u < 1) {
                float bx = corpo.left - cw * 0.3f + (cw * 1.6f) * u;
                Paint ref = new Paint(Paint.ANTI_ALIAS_FLAG);
                ref.setShader(new LinearGradient(bx - cw * 0.12f, corpo.top, bx + cw * 0.12f, corpo.bottom,
                        new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(corpo, ref);
            }
            c.restore();
            Paint aro = new Paint(Paint.ANTI_ALIAS_FLAG);
            aro.setStyle(Paint.Style.STROKE);
            aro.setStrokeWidth(Math.max(1.5f, ch * 0.018f));
            aro.setShader(new LinearGradient(0, corpo.top, 0, corpo.bottom, new int[]{0xFFE6EAF0, 0xFF7C838E, 0xFFB9C0CA}, new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawPath(forma, aro);
            float sw = cw * 0.585f, sh = sw * 9 / 16f;
            RectF tela = new RectF(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2);
            Paint moldura = new Paint(Paint.ANTI_ALIAS_FLAG);
            moldura.setColor(0xFF050607);
            c.drawRoundRect(new RectF(tela.left - ch * 0.03f, tela.top - ch * 0.03f, tela.right + ch * 0.03f, tela.bottom + ch * 0.03f), ch * 0.03f, ch * 0.03f, moldura);
            telaVita(c, tela, T);
            Paint cam = new Paint(Paint.ANTI_ALIAS_FLAG);
            cam.setColor(0xFF1E2430);
            c.drawCircle(cx + cw * 0.37f, cy - ch * 0.395f, ch * 0.018f, cam);
            // as bases dos analógicos (buracos escuros) e o brilho do botão do meio ficam na face
            float vivo = faixa(T, 1.45f, 1.9f);
            float sl = cx - cw * 0.37f, sr = cx + cw * 0.37f, sy = cy + ch * 0.235f, sr0 = ch * 0.085f;
            Paint buraco = new Paint(Paint.ANTI_ALIAS_FLAG);
            buraco.setColor(0xFF030304);
            c.drawCircle(sl, sy, sr0 * 1.2f, buraco);
            c.drawCircle(sr, sy, sr0 * 1.2f, buraco);
            float psx = cx - cw * 0.37f, psy = cy + ch * 0.39f;
            float pulso = vivo * (0.5f + 0.5f * (float) Math.sin(T * 3.2f));
            if (pulso > 0) {
                Paint pg = new Paint(Paint.ANTI_ALIAS_FLAG);
                pg.setShader(new android.graphics.RadialGradient(psx, psy, ch * 0.11f, comAlfa(0xFF4FA3FF, 0.65f * pulso), 0x004FA3FF, Shader.TileMode.CLAMP));
                c.drawCircle(psx, psy, ch * 0.11f, pg);
            }
            c.restore();
            // o que salta da face, do mais baixo ao mais alto
            Paint lado = new Paint(Paint.ANTI_ALIAS_FLAG);
            lado.setColor(0xFF0B0C0F);
            // botão do meio, start e select: baixinhos
            float hb0 = ch * 0.014f;
            for (int k = 1; k <= 2; k++) {
                c.save();
                camada(c, -hb0 * k / 2f);
                c.drawCircle(psx, psy, ch * 0.044f, lado);
                for (int i = 0; i < 2; i++) {
                    float px = cx + cw * (i == 0 ? 0.335f : 0.405f), py = cy + ch * 0.385f;
                    c.drawRoundRect(new RectF(px - ch * 0.04f, py - ch * 0.016f, px + ch * 0.04f, py + ch * 0.016f), ch * 0.016f, ch * 0.016f, lado);
                }
                c.restore();
            }
            c.save();
            camada(c, -hb0);
            Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
            ps.setShader(new android.graphics.RadialGradient(psx - ch * 0.01f, psy - ch * 0.01f, ch * 0.06f, 0xFF3A3F48, 0xFF0C0D10, Shader.TileMode.CLAMP));
            c.drawCircle(psx, psy, ch * 0.044f, ps);
            Paint psAro = new Paint(aro);
            psAro.setStrokeWidth(Math.max(1f, ch * 0.01f));
            c.drawCircle(psx, psy, ch * 0.044f, psAro);
            if (pulso > 0) {
                Paint pa = new Paint(Paint.ANTI_ALIAS_FLAG);
                pa.setStyle(Paint.Style.STROKE);
                pa.setStrokeWidth(Math.max(1f, ch * 0.008f));
                pa.setColor(comAlfa(0xFFBFE0FF, pulso));
                c.drawCircle(psx, psy, ch * 0.03f, pa);
            }
            for (int i = 0; i < 2; i++) {
                float px = cx + cw * (i == 0 ? 0.335f : 0.405f), py = cy + ch * 0.385f;
                Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
                sel.setShader(new LinearGradient(0, py - ch * 0.016f, 0, py + ch * 0.016f, 0xFF464B55, 0xFF1C1F24, Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(px - ch * 0.04f, py - ch * 0.016f, px + ch * 0.04f, py + ch * 0.016f), ch * 0.016f, ch * 0.016f, sel);
            }
            c.restore();
            // a cruz e os botões de face: corpo com altura, o topo é o desenho de antes
            float lx = cx - cw * 0.395f, rxb = cx + cw * 0.395f, yb = cy - ch * 0.15f, hb = ch * 0.038f;
            float s = ch * 0.115f, a = s * 0.36f;
            Path cruz = new Path();
            cruz.addRoundRect(new RectF(lx - s, yb - a, lx + s, yb + a), a * 0.4f, a * 0.4f, Path.Direction.CW);
            Path vert = new Path();
            vert.addRoundRect(new RectF(lx - a, yb - s, lx + a, yb + s), a * 0.4f, a * 0.4f, Path.Direction.CW);
            cruz.op(vert, Path.Op.UNION);
            float d = ch * 0.125f;
            float[][] pos = {{rxb, yb - d}, {rxb + d, yb}, {rxb, yb + d}, {rxb - d, yb}};
            int[] cores = {0xFF4DE3CB, 0xFFFF6E6E, 0xFF86B6FF, 0xFFF7A1DC};
            for (int k = 1; k <= 3; k++) {
                c.save();
                camada(c, -hb * k / 4f);
                c.drawPath(cruz, lado);
                for (int i = 0; i < 4; i++) {
                    float alt = 1 - 0.75f * aperto(T, i); // apertado afunda: a coluna fica mais baixa
                    if (k / 4f <= alt) c.drawCircle(pos[i][0], pos[i][1], ch * 0.058f, lado);
                }
                c.restore();
            }
            c.save();
            camada(c, -hb);
            cruzVita(c, lx, yb, s, T);
            c.restore();
            for (int i = 0; i < 4; i++) {
                float ap = aperto(T, i);
                c.save();
                camada(c, -hb * (1 - 0.75f * ap));
                float aceso = faixa(T, 1.55f + 0.12f * i, 1.7f + 0.12f * i) * (1 - 0.6f * faixa(T, 2.1f + 0.12f * i, 2.6f + 0.12f * i));
                botaoFaceVita(c, pos[i][0], pos[i][1], ch * 0.058f, i, cores[i], aceso, ap);
                c.restore();
            }
            // os analógicos: haste que inclina e a cúpula lá em cima
            float[][] incl = {{vivo * (float) Math.sin(T * 2.4f), vivo * 0.75f * (float) Math.cos(T * 2.4f)},
                    {vivo * (float) Math.sin(T * 1.8f + 1f), vivo * 0.7f * (float) Math.sin(T * 3.6f + 2f)}};
            float hs = ch * 0.085f;
            for (int j = 0; j < 2; j++) {
                float x0 = j == 0 ? sl : sr, ox = incl[j][0], oy = incl[j][1];
                for (int k = 1; k <= 5; k++) { // a haste e a lateral da cúpula
                    float f = k / 6f;
                    c.save();
                    camada(c, -hs * f);
                    Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    hp.setColor(k >= 4 ? 0xFF15171B : 0xFF08090B);
                    c.drawCircle(x0 + ox * sr0 * 0.45f * f, sy + oy * sr0 * 0.45f * f, k >= 4 ? sr0 : sr0 * 0.55f, hp);
                    c.restore();
                }
                c.save();
                camada(c, -hs);
                float px = x0 + ox * sr0 * 0.45f, py = sy + oy * sr0 * 0.45f;
                float m = (float) Math.min(1, Math.hypot(ox, oy));
                Paint cup = new Paint(Paint.ANTI_ALIAS_FLAG);
                cup.setShader(new android.graphics.RadialGradient(px - sr0 * 0.3f - ox * sr0 * 0.1f, py - sr0 * 0.35f - oy * sr0 * 0.1f, sr0 * 1.4f,
                        new int[]{0xFF555B66, 0xFF20232A, 0xFF0A0B0D}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(px, py, sr0, cup);
                Paint borda = new Paint(Paint.ANTI_ALIAS_FLAG);
                borda.setStyle(Paint.Style.STROKE);
                borda.setStrokeWidth(Math.max(1f, sr0 * 0.08f));
                borda.setColor(comAlfa(0xFF8CC4FF, 0.2f + 0.5f * m));
                c.drawCircle(px, py, sr0 * 0.7f, borda);
                c.restore();
            }
        }

        // a tela: apagada (vidro escuro com reflexo), clarão ao ligar, depois o azul com as bolhas da LiveArea
        private void telaVita(Canvas c, RectF r, float T) {
            float liga = faixa(T, 1.05f, 1.4f);
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(0xFF07090D);
            c.drawRect(r, f);
            if (liga > 0) {
                Paint azul = new Paint(Paint.ANTI_ALIAS_FLAG);
                azul.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, new int[]{0xFF2F7DFF, 0xFF1546C8, 0xFF0A2A86},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                azul.setAlpha((int) (255 * liga));
                c.drawRect(r, azul);
                c.save();
                c.clipRect(r);
                float[][] bolhas = {{0.18f, 0.32f, 0.11f}, {0.38f, 0.30f, 0.11f}, {0.58f, 0.32f, 0.11f}, {0.78f, 0.30f, 0.11f},
                        {0.28f, 0.68f, 0.11f}, {0.48f, 0.70f, 0.11f}, {0.68f, 0.68f, 0.11f}};
                int[] cores = {0xFFFFFFFF, 0xFFFFC94D, 0xFF6EE7B7, 0xFFFF8FA3, 0xFF9FD0FF, 0xFFD4B5FF, 0xFFFFFFFF};
                for (int i = 0; i < bolhas.length; i++) {
                    float e = faixa(T, 1.35f + 0.09f * i, 1.65f + 0.09f * i);
                    if (e <= 0) continue;
                    float bx = r.left + r.width() * bolhas[i][0], by = r.top + r.height() * bolhas[i][1] + 2 * dp * (float) Math.sin(T * 2 + i);
                    float br = r.height() * bolhas[i][2] * salto(e);
                    Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                    b.setShader(new android.graphics.RadialGradient(bx - br * 0.35f, by - br * 0.4f, br * 1.4f, 0xFFFFFFFF, cores[i], Shader.TileMode.CLAMP));
                    c.drawCircle(bx, by, br, b);
                    Paint brilho = new Paint(Paint.ANTI_ALIAS_FLAG); // o brilho de bolha
                    brilho.setColor(0x99FFFFFF);
                    c.drawOval(new RectF(bx - br * 0.55f, by - br * 0.8f, bx + br * 0.15f, by - br * 0.35f), brilho);
                }
                c.restore();
            }
            float flash = liga > 0 && liga < 1 ? (float) Math.sin(liga * Math.PI) : 0; // o clarão de ligar
            if (flash > 0) {
                Paint fl = new Paint(Paint.ANTI_ALIAS_FLAG);
                fl.setColor(comAlfa(0xFFFFFFFF, flash * 0.85f));
                c.drawRect(r, fl);
            }
            Paint vidro = new Paint(Paint.ANTI_ALIAS_FLAG); // o reflexo do vidro da tela
            vidro.setShader(new LinearGradient(r.left, r.top, r.left + r.width() * 0.6f, r.bottom, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            Path diag = new Path();
            diag.moveTo(r.left, r.top);
            diag.lineTo(r.left + r.width() * 0.55f, r.top);
            diag.lineTo(r.left + r.width() * 0.25f, r.bottom);
            diag.lineTo(r.left, r.bottom);
            diag.close();
            c.drawPath(diag, vidro);
        }

        // O combo que os botões tocam depois de acesos, num laço de 2,4 s: índices 0 △, 1 ○, 2 ✕, 3 □, 5 R, 6 L.
        // Devolve 0..1 (quanto o botão está afundado agora).
        private static final float[][] COMBO = {{0.00f, 3}, {0.22f, 3}, {0.44f, 0}, {0.80f, 2}, {1.05f, 6}, {1.20f, 1}, {1.55f, 5}, {1.75f, 2}, {1.95f, 0}};

        private static float aperto(float T, int qual) {
            if (T < 2.3f) return 0;
            float t = (T - 2.3f) % 2.4f, a = 0;
            for (float[] e : COMBO) {
                if ((int) e[1] != qual) continue;
                float d = t - e[0];
                if (d >= 0 && d < 0.16f) a = Math.max(a, d < 0.04f ? d / 0.04f : 1 - (d - 0.04f) / 0.12f);
            }
            return a;
        }

        // a cruz: cada braço afunda e acende por vez (cima, direita, baixo, esquerda), num laço de 2,4 s
        private void cruzVita(Canvas c, float x, float y, float s, float T) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, y - s, 0, y + s, 0xFF3A3F48, 0xFF14161A, Shader.TileMode.CLAMP));
            float a = s * 0.36f;
            float tc = T < 2.1f ? -1 : (T - 2.1f) % 2.4f;
            for (int i = 0; i < 4; i++) {
                float d = tc - (0.1f + 0.3f * i), ap = tc < 0 || d < 0 || d > 0.2f ? 0 : (d < 0.05f ? d / 0.05f : 1 - (d - 0.05f) / 0.15f);
                c.save();
                c.rotate(90 * i, x, y);
                if (ap > 0) { // o braço apertado: azul por baixo, mais claro
                    Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                    g.setShader(new android.graphics.RadialGradient(x, y - s * 0.65f, s * 0.7f, comAlfa(0xFF6FB6FF, 0.75f * ap), 0x006FB6FF, Shader.TileMode.CLAMP));
                    c.drawCircle(x, y - s * 0.65f, s * 0.7f, g);
                }
                float k = 1 - 0.08f * ap;
                c.save();
                c.scale(k, k, x, y - s * 0.6f);
                c.drawRoundRect(new RectF(x - a, y - s, x + a, y - a * 0.6f), a * 0.4f, a * 0.4f, p);
                c.restore();
                Paint seta = new Paint(Paint.ANTI_ALIAS_FLAG);
                seta.setColor(ap > 0 ? comAlfa(0xFFFFFFFF, 0.4f + 0.6f * ap) : 0xFF5B616C);
                Path t = new Path();
                t.moveTo(x, y - s * 0.82f);
                t.lineTo(x + a * 0.45f, y - s * 0.6f);
                t.lineTo(x - a * 0.45f, y - s * 0.6f);
                t.close();
                c.drawPath(t, seta);
                c.restore();
            }
        }

        private void botaoFaceVita(Canvas c, float x, float y, float r, int qual, int cor, float aceso, float apertado) {
            aceso = Math.max(aceso, apertado);
            r *= 1 - 0.12f * apertado; // afunda
            if (aceso > 0) {
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setShader(new android.graphics.RadialGradient(x, y, r * 2.6f, comAlfa(cor, 0.75f * aceso), comAlfa(cor, 0), Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r * 2.6f, g);
            }
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setShader(new android.graphics.RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.5f, 0xFF3C414A, 0xFF101216, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, b);
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeWidth(Math.max(1f, r * 0.16f));
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setStrokeJoin(Paint.Join.ROUND);
            int base = comAlfa(cor, 0.55f + 0.45f * aceso);
            s.setColor(aceso > 0.5f ? comAlfa(0xFFFFFFFF, aceso) : base);
            float k = r * 0.5f;
            switch (qual) {
                case 0: { Path t = new Path(); t.moveTo(x, y - k); t.lineTo(x + k * 0.95f, y + k * 0.7f); t.lineTo(x - k * 0.95f, y + k * 0.7f); t.close(); c.drawPath(t, s); break; }
                case 1: c.drawCircle(x, y, k * 0.82f, s); break;
                case 2: c.drawLine(x - k * 0.72f, y - k * 0.72f, x + k * 0.72f, y + k * 0.72f, s); c.drawLine(x + k * 0.72f, y - k * 0.72f, x - k * 0.72f, y + k * 0.72f, s); break;
                default: c.drawRect(x - k * 0.68f, y - k * 0.68f, x + k * 0.68f, y + k * 0.68f, s);
            }
        }

        // ---------------------------------------------------------------- apoie o projeto
        private void telaApoio(Canvas c, int w, int h) {
            Paint v = new Paint();
            v.setColor(0xD0060505);
            c.drawRect(0, 0, w, h, v);
            float gx = margem(), y = 40 * dp;
            Paint t = texto(22, true, 0xFFFFFFFF);
            c.drawText(Tr.t("Apoie o projeto"), gx, y + t.getTextSize() * 0.8f, t);
            Paint g = texto(14, false, TEXTO);
            float qrLado = qr != null ? Math.min(h - (40 + 84 + 116) * dp, 230 * dp) : 0; // a legenda fica acima das dicas
            float colW = Math.min(w - 2 * gx - (qr != null ? qrLado * qr.getWidth() / qr.getHeight() + 40 * dp : 0), 460 * dp);
            c.drawText(cortar(Tr.t("O port é gratuito e feito por uma pessoa só. Se ele te divertiu, ajude a mantê-lo vivo."), g, w - 2 * gx), gx,
                    y + 22 * dp + g.getTextSize() * 1.6f, g);
            String[][] op = {{"LivePix (Pix)", Tr.t("Pix na hora; a sua mensagem aparece na live"), "livepix.gg/luisxl15"},
                    {"PayPal", Tr.t("Cartão ou saldo do PayPal, de qualquer país"), "paypal.com"}};
            int[] cor = {0xFF00C2A8, 0xFF3B82F6};
            float y0 = y + 84 * dp, alt = 78 * dp;
            for (int i = 0; i < 2; i++) {
                RectF r = new RectF(gx - 12 * dp, y0 + i * (alt + 12 * dp), gx - 12 * dp + colW, y0 + i * (alt + 12 * dp) + alt);
                cartaoApoio[i].set(r);
                boolean sel = i == escolhido;
                vidro(c, r, 16 * dp, sel);
                Paint barra = new Paint(Paint.ANTI_ALIAS_FLAG);
                barra.setColor(cor[i]);
                c.drawRoundRect(new RectF(r.left + 14 * dp, r.top + 16 * dp, r.left + 18 * dp, r.bottom - 16 * dp), 2 * dp, 2 * dp, barra);
                Paint n = texto(17, true, 0xFFFFFFFF);
                Paint d = texto(12, false, 0xCCFFFFFF);
                Paint u = texto(12, true, cor[i]);
                float tx = r.left + 32 * dp;
                c.drawText(op[i][0], tx, r.top + 28 * dp, n);
                c.drawText(cortar(op[i][1], d, r.width() - 48 * dp), tx, r.top + 47 * dp, d);
                c.drawText(op[i][2], tx, r.top + 65 * dp, u);
                Paint seta = texto(20, false, 0xB3FFFFFF);
                seta.setTextAlign(Paint.Align.RIGHT);
                c.drawText("›", r.right - 18 * dp, r.centerY() + seta.getTextSize() * 0.35f, seta);
            }
            if (qr != null) { // o QR do LivePix, para doar de outro aparelho
                float qw = qrLado * qr.getWidth() / qr.getHeight(), qx = gx - 12 * dp + colW + 40 * dp, qy = y0;
                Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
                fundo.setColor(0xFFFFFFFF);
                c.drawRoundRect(new RectF(qx - 8 * dp, qy - 8 * dp, qx + qw + 8 * dp, qy + qrLado + 8 * dp), 14 * dp, 14 * dp, fundo);
                c.drawBitmap(qr, null, new RectF(qx, qy, qx + qw, qy + qrLado), new Paint(Paint.FILTER_BITMAP_FLAG));
                Paint leg = texto(12, false, 0xB3FFFFFF);
                leg.setTextAlign(Paint.Align.CENTER);
                c.drawText(Tr.t("Ou leia o QR code com outro aparelho"), qx + qw / 2, qy + qrLado + 30 * dp, leg);
            }
            dicas(c, w, h, Tr.t("Abrir"));
        }

        // ---------------------------------------------------------------- navegação pelo controle: duas fileiras
        private static int indice(int[] fileira, int f) {
            for (int i = 0; i < fileira.length; i++) if (fileira[i] == f) return i;
            return -1;
        }

        private void andar(int passo) {
            int[] fileira = indice(ALTO, foco) >= 0 ? ALTO : BAIXO;
            int i = indice(fileira, foco);
            if (i < 0) { focar(F_JOGAR); return; }
            int j = Math.max(0, Math.min(fileira.length - 1, i + passo));
            focar(fileira[j]);
        }


        // ---------------------------------------------------------------- primeira instalação (Instalador.java)
        private boolean instalar;
        File pkgEscolhido, rifEscolhido;
        private boolean querTexturas = true;
        private volatile String etapaInst, erroInst;
        private volatile long feitoInst, totalInst;
        private volatile boolean rodandoInst, cancelarInst;
        private final RectF btPkg = new RectF(), btRif = new RectF(), btTex = new RectF(), btInstalar = new RectF(), btPasta = new RectF();
        // atualização (Atualizador.java): a barra no alto da tela principal
        private volatile String etapaAtu;
        private volatile long feitoAtu, totalAtu;
        private boolean checouAtu;

        private void abrirInstalacao() {
            if (!acesso) { act.pedirAcesso(); return; }
            instalar = true;
            erroInst = null;
            new Thread(() -> { // achar o PKG e o work.bin pode demorar um pouco (pastas grandes)
                if (pkgEscolhido == null) pkgEscolhido = Instalador.acharPkg();
                if (rifEscolhido == null) rifEscolhido = Instalador.acharLicenca();
                postInvalidate();
            }, "achar-pkg").start();
            invalidate();
        }

        private File destinoInstalacao() { return new File(Environment.getExternalStorageDirectory(), Pastas.NOME); }

        // os dados já baixados à mão (Download ou a pasta de trabalho) valem no lugar do download
        private File acharLocal(String grupo) {
            File[] ds = {new File(destinoInstalacao(), ".instalar"), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)};
            for (File d : ds) if (Instalador.arquivo(d, grupo, "manifesto.bin").isFile()) return d;
            return null;
        }

        private void iniciarInstalacao() {
            if (rodandoInst) { cancelarInst = true; return; }
            if (pkgEscolhido == null || rifEscolhido == null) {
                Toast.makeText(act, Tr.t("Falta o PKG ou o work.bin"), Toast.LENGTH_LONG).show();
                return;
            }
            rodandoInst = true;
            cancelarInst = false;
            erroInst = null;
            final File pkg = pkgEscolhido, rif = rifEscolhido, raizI = destinoInstalacao();
            final boolean tex = querTexturas;
            act.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // a tela não apaga no meio
            new Thread(() -> {
                Instalador.Aviso av = (e, f, t) -> { etapaInst = e; feitoInst = f; totalInst = t; postInvalidate(); };
                Instalador.Cancelar cn = () -> { if (cancelarInst) throw new Instalador.Erro(Tr.t("Cancelado")); };
                try {
                    av.andou(Tr.t("Conferindo a licença"), 0, 0);
                    byte[] klic = Instalador.lerLicenca(rif);
                    Instalador.conferirPkg(pkg);
                    byte[] K = Instalador.derivar(klic, Instalador.sha256Pkg(pkg, av, cn));
                    File trabalho = new File(raizI, ".instalar");
                    Instalador.Release rel = null;
                    File dados = acharLocal("dados");
                    if (dados == null) {
                        av.andou(Tr.t("Procurando a versão mais nova"), 0, 0);
                        rel = Instalador.releaseComGrupo("dados"); // a mais nova que traz os dados (não só o APK)
                        if (rel == null) throw new Instalador.Erro(Tr.t("Ainda não há uma versão publicada no GitHub"));
                        Instalador.baixarGrupo(rel, "dados", trabalho, av, cn);
                        dados = trabalho;
                    }
                    Instalador.instalar(Instalador.abrir(dados, "dados", K), K, raizI, Tr.t("Instalando os dados do jogo"), av, cn);
                    if (tex) { // opcional: só se a release (ou a pasta) tiver
                        File t = acharLocal("opcional-texturas_hd");
                        if (t == null) {
                            Instalador.Release rt = Instalador.releaseComGrupo("opcional-texturas_hd");
                            if (rt != null) { Instalador.baixarGrupo(rt, "opcional-texturas_hd", trabalho, av, cn); t = trabalho; }
                        }
                        if (t != null)
                            Instalador.instalar(Instalador.abrir(t, "opcional-texturas_hd", K), K, raizI, Tr.t("Instalando as texturas HD"), av, cn);
                    }
                    Instalador.extrairApp(act, pkg, rif, new File(raizI, "game"), av, cn);
                    Instalador.guardarChave(act, K);
                    Opcoes.gravar(raizI, "resolution", "1"); // celular: começa na resolução do Vita (1x); o menu do jogo muda
                    if (rel != null) act.getSharedPreferences("atualizacao", android.content.Context.MODE_PRIVATE).edit().putString("dados", rel.tag).apply();
                    Atualizador.apagar(trabalho);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        instalar = false;
                        Pastas.guardar(act, raizI.getPath());
                        Tela t = telaAtual != null ? telaAtual : this;
                        t.instalar = false;
                        t.artesLidas = false; // as artes da pasta nova (antes não havia pasta)
                        t.conferir();
                        Toast.makeText(t.act, Tr.t("Jogo instalado"), Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    erroInst = e.getMessage() != null ? e.getMessage() : e.toString();
                } finally {
                    rodandoInst = false;
                    etapaInst = null;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        Tela t = telaAtual != null ? telaAtual : this;
                        t.act.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        t.invalidate();
                    });
                    postInvalidate();
                }
            }, "instalar").start();
            invalidate();
        }

        private void linhaArquivo(Canvas c, RectF r, String titulo, File f, boolean foco) {
            vidro(c, r, 14 * dp, foco);
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
            pt.setColor(f != null ? 0xFF3DDC84 : AVISO);
            c.drawCircle(r.left + 22 * dp, r.centerY(), 5 * dp, pt);
            c.drawText(titulo, r.left + 40 * dp, r.top + 24 * dp, texto(15, true, 0xFFFFFFFF));
            String v = f != null ? f.getPath() : Tr.t("Não encontrado: toque para escolher");
            c.drawText(cortar(v, texto(12, false, 0), r.width() - 60 * dp), r.left + 40 * dp, r.top + 43 * dp, texto(12, false, 0xB3FFFFFF));
        }

        private void telaInstalar(Canvas c, int w, int h) {
            Paint v = new Paint();
            v.setColor(0xD8060505);
            c.drawRect(0, 0, w, h, v);
            float gx = margem(), y = 34 * dp;
            Paint t = texto(22, true, 0xFFFFFFFF);
            c.drawText(Tr.t("Instalar o jogo"), gx, y + t.getTextSize() * 0.8f, t);
            Paint g = texto(13, false, TEXTO);
            c.drawText(cortar(Tr.t("O jogo sai do PKG americano (PCSA00126) e do work.bin da sua licença; os dados vêm cifrados do GitHub."), g, w - 2 * gx),
                    gx, y + 22 * dp + g.getTextSize() * 1.6f, g);
            float col = Math.min(w - 2 * gx, 640 * dp), y0 = y + 66 * dp, alt = 56 * dp;
            btPkg.set(gx - 12 * dp, y0, gx - 12 * dp + col, y0 + alt);
            btRif.set(gx - 12 * dp, y0 + alt + 10 * dp, gx - 12 * dp + col, y0 + 2 * alt + 10 * dp);
            linhaArquivo(c, btPkg, Tr.t("PKG do jogo (gowHD_PSV.pkg, 3,3 GB)"), pkgEscolhido, false);
            linhaArquivo(c, btRif, Tr.t("Licença (work.bin)"), rifEscolhido, false);
            // texturas HD: chave
            float ty = y0 + 2 * alt + 28 * dp;
            btTex.set(gx - 12 * dp, ty - 16 * dp, gx - 12 * dp + col, ty + 16 * dp);
            RectF sw = new RectF(gx, ty - 11 * dp, gx + 38 * dp, ty + 11 * dp);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(querTexturas ? 0xFF3DDC84 : 0x40FFFFFF);
            c.drawRoundRect(sw, 11 * dp, 11 * dp, sp);
            sp.setColor(0xFFFFFFFF);
            c.drawCircle(querTexturas ? sw.right - 11 * dp : sw.left + 11 * dp, ty, 8 * dp, sp);
            c.drawText(Tr.t("Texturas HD (+1,7 GB)"), sw.right + 14 * dp, ty + 5 * dp, texto(14, false, 0xFFFFFFFF));
            // botão e progresso
            float by = ty + 30 * dp, bh = 48 * dp, bw = 200 * dp;
            btInstalar.set(gx, by, gx + bw, by + bh);
            boolean pronto = pkgEscolhido != null && rifEscolhido != null;
            botaoBranco(c, btInstalar, pronto || rodandoInst, false);
            Paint tb = texto(17, true, 0xFF0B0B0C);
            tb.setTextAlign(Paint.Align.CENTER);
            c.drawText(rodandoInst ? Tr.t("Cancelar") : Tr.t("Instalar"), btInstalar.centerX(), btInstalar.centerY() + tb.getTextSize() * 0.36f, tb);
            float px = btInstalar.right + 24 * dp, pw = Math.max(80 * dp, gx - 12 * dp + col - px);
            if (rodandoInst || etapaInst != null) {
                String e = etapaInst != null ? etapaInst : Tr.t("Preparando");
                c.drawText(cortar(e, texto(13, true, 0), pw), px, by + 16 * dp, texto(13, true, 0xFFFFFFFF));
                RectF tr = new RectF(px, by + 26 * dp, px + pw, by + 32 * dp);
                Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
                pb.setColor(0x33FFFFFF);
                c.drawRoundRect(tr, 3 * dp, 3 * dp, pb);
                if (totalInst > 0) {
                    float f = Math.min(1f, feitoInst / (float) totalInst);
                    pb.setColor(0xFF5CD0FF);
                    c.drawRoundRect(new RectF(tr.left, tr.top, tr.left + tr.width() * f, tr.bottom), 3 * dp, 3 * dp, pb);
                    String n = String.format(java.util.Locale.ROOT, "%d%%  ·  %.2f / %.2f GB", Math.round(f * 100), feitoInst / 1e9, totalInst / 1e9);
                    c.drawText(n, px, by + 48 * dp, texto(12, false, 0xB3FFFFFF));
                }
            }
            if (erroInst != null)
                c.drawText(cortar(erroInst, texto(13, false, 0), w - 2 * gx), gx, by + bh + 26 * dp, texto(13, false, AVISO));
            // já tem a pasta do PC: escolher
            String lk = Tr.t("Já tenho a pasta copiada do PC");
            Paint lp = texto(13, true, 0xFF9FD8FF);
            float ly = by + bh + 52 * dp;
            c.drawText(lk, gx, ly, lp);
            btPasta.set(gx - 8 * dp, ly - 20 * dp, gx + lp.measureText(lk) + 8 * dp, ly + 8 * dp);
            dicas(c, w, h, Tr.t("Selecionar"));
        }

        private boolean tocarInstalar(float x, float y) {
            if (rodandoInst && !btInstalar.contains(x, y)) return true; // durante a instalação só o Cancelar
            if (btPkg.contains(x, y)) { act.escolherArquivo(true); return true; }
            if (btRif.contains(x, y)) { act.escolherArquivo(false); return true; }
            if (btTex.contains(x, y)) { querTexturas = !querTexturas; invalidate(); return true; }
            if (btInstalar.contains(x, y)) { iniciarInstalacao(); return true; }
            if (btPasta.contains(x, y)) { act.escolherPasta(); return true; }
            return true;
        }

        // atualização: confere uma vez por abertura do lançador; com versão nova, baixa e instala sozinha
        private void conferirAtualizacao() {
            if (checouAtu) return;
            checouAtu = true;
            new Thread(() -> {
                Instalador.Aviso av = (e, f, t) -> { etapaAtu = e; feitoAtu = f; totalAtu = t; postInvalidate(); };
                Instalador.Cancelar cn = () -> { };
                try {
                    Instalador.Release r = Instalador.ultimaRelease();
                    if (r == null) return;
                    Instalador.Asset apk = Atualizador.apkDe(r);
                    if (apk != null && Atualizador.maisNova(r.tag, Atualizador.versaoInstalada(act))) {
                        if (!Atualizador.podeInstalar(act)) {
                            post(() -> Toast.makeText(act, Tr.t("Permita instalar apps deste app para receber a atualização"), Toast.LENGTH_LONG).show());
                            return;
                        }
                        etapaAtu = Tr.t("Baixando a atualização") + " " + r.tag;
                        File f = Atualizador.baixarApk(act, apk, (e, fe, t) -> av.andou(Tr.t("Baixando a atualização") + " " + r.tag, fe, t), cn);
                        post(() -> {
                            try {
                                Atualizador.instalarApk(act, f);
                            } catch (Exception e) {
                                Toast.makeText(act, Tr.t("A atualização não instalou: ") + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else if (acesso && raiz != null && Pastas.completa(raiz)) {
                        // o pacote de dados pode estar numa release anterior (a mais nova só com o APK)
                        Instalador.Release rd = Instalador.releaseComGrupo("atualizacao");
                        if (rd != null) Atualizador.aplicarDados(act, rd, raiz, av, cn);
                    }
                } catch (Exception e) {
                    android.util.Log.w("gow", "atualização: " + e);
                } finally {
                    etapaAtu = null;
                    postInvalidate();
                }
            }, "atualizacao").start();
        }

        // a barra da atualização no alto, no meio
        private void barraAtualizacao(Canvas c, int w) {
            String e = etapaAtu;
            if (e == null) return;
            Paint t = texto(12, true, 0xFFFFFFFF);
            String s = totalAtu > 0 ? e + "  ·  " + Math.round(100f * feitoAtu / totalAtu) + "%" : e;
            float bw = Math.max(260 * dp, t.measureText(s) + 40 * dp), cx = w / 2f, top = 14 * dp;
            RectF r = new RectF(cx - bw / 2, top, cx + bw / 2, top + 40 * dp);
            vidro(c, r, 20 * dp, false);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(s, cx, r.top + 17 * dp, t);
            RectF tr = new RectF(r.left + 16 * dp, r.bottom - 12 * dp, r.right - 16 * dp, r.bottom - 8 * dp);
            Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
            pb.setColor(0x33FFFFFF);
            c.drawRoundRect(tr, 2 * dp, 2 * dp, pb);
            if (totalAtu > 0) {
                pb.setColor(0xFF5CD0FF);
                c.drawRoundRect(new RectF(tr.left, tr.top, tr.left + tr.width() * Math.min(1f, feitoAtu / (float) totalAtu), tr.bottom), 2 * dp, 2 * dp, pb);
            }
        }

        private static String cortar(String s, Paint p, float max) {
            if (p.measureText(s) <= max) return s;
            int n = p.breakText(s, true, max - p.measureText("…"), null);
            return s.substring(0, Math.max(0, n)) + "…";
        }

        // ---------------------------------------------------------------- entrada
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!rodando) {
                rodando = true;
                ultimoQuadro = 0;
                android.view.Choreographer.getInstance().postFrameCallback(relogio);
            }
            requestFocus(); // sem foco, o controle e o Enter do teclado não chegavam aqui
        }

        @Override
        protected void onDetachedFromWindow() {
            rodando = false;
            android.view.Choreographer.getInstance().removeFrameCallback(relogio);
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (aberturaT >= 0) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) pularAbertura();
                return true;
            }
            if (instalar) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP && !tocarDica(e.getX(), e.getY())) tocarInstalar(e.getX(), e.getY());
                invalidate();
                return true;
            }
            if (apoio && e.getActionMasked() == MotionEvent.ACTION_UP && !tocarDica(e.getX(), e.getY())) {
                for (int i = 0; i < cartaoApoio.length; i++)
                    if (cartaoApoio[i].contains(e.getX(), e.getY())) { escolhido = i; act.abrirLink(i == 0 ? URL_LIVEPIX : URL_PAYPAL); }
                invalidate();
                return true;
            }
            if (trofeus) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        toqueX0 = e.getX();
                        toqueY0 = e.getY();
                        rolagemT0 = rolagemT;
                        arrastandoT = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!arrastandoT && Math.abs(e.getY() - toqueY0) > 10 * dp && areaT.contains(toqueX0, toqueY0)) arrastandoT = true;
                        if (arrastandoT) {
                            rolagemT = Math.max(0, Math.min(maxRolagemT, rolagemT0 - (e.getY() - toqueY0)));
                            invalidate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (arrastandoT) { arrastandoT = false; return true; }
                        break;
                    default:
                        return true;
                }
            }
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX(), y = e.getY();
            if (tocarDica(x, y)) return true;
            if (drivers) {
                for (int i = 0; i < linhas.size(); i++)
                    if (linhas.get(i).contains(x, y)) { escolhido = i; acionarDriver(); return true; }
                return true;
            }
            if (trofeus) {
                for (int i = 0; i < abas.size(); i++)
                    if (abas.get(i).contains(x, y)) { aba = i; linhaT = 0; rolagemT = 0; confirmarZerar = false; invalidate(); return true; }
                if (aba < conjuntos.size() && zerarR.contains(x, y)) {
                    linhaT = conjuntos.get(aba).lista.size();
                    zerarAba();
                    invalidate();
                    return true;
                }
                if (areaT.contains(x, y))
                    for (int i = 0; i < linhasT.size(); i++)
                        if (linhasT.get(i).contains(x, y)) { linhaT = i; confirmarZerar = false; invalidate(); return true; }
                return true;
            }
            if (telaOp) {
                for (int i = 0; i < linhasA.size(); i++)
                    if (linhasA.get(i).contains(x, y)) { escolhido = i; escolherProporcao(); return true; }
                return true;
            }
            if (idiomas) {
                for (int i = 0; i < linhasI.size(); i++)
                    if (linhasI.get(i).contains(x, y)) { escolhido = i; escolherIdioma(); return true; }
                return true;
            }
            for (int i = 0; i < alvo.length; i++)
                if (alvo[i].contains(x, y)) {
                    if (i <= 2 && i != jogo) { focar(i); return true; } // primeiro toque escolhe, o segundo joga
                    foco = i;
                    acionar(i);
                    return true;
                }
            return true;
        }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            if (aberturaT >= 0) { pularAbertura(); return true; }
            if (instalar) {
                if (k == KeyEvent.KEYCODE_BUTTON_A || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_CENTER) { iniciarInstalacao(); return true; }
                if (k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_ESCAPE) return voltar();
                return super.onKeyDown(k, e);
            }
            if (apoio) {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                        escolhido = 1 - Math.max(0, Math.min(1, escolhido)); invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        act.abrirLink(escolhido == 0 ? URL_LIVEPIX : URL_PAYPAL); return true;
                    case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ESCAPE: return voltar();
                    default: return super.onKeyDown(k, e);
                }
            }
            if (drivers) {
                int n = itens.size();
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: escolhido = (escolhido + n - 1) % n; invalidate(); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: escolhido = (escolhido + 1) % n; invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        acionarDriver(); return true;
                    case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ESCAPE: return voltar();
                    default: return super.onKeyDown(k, e);
                }
            }
            if (trofeus) {
                int n = aba < conjuntos.size() ? conjuntos.get(aba).lista.size() + 1 : 1; // o último é o zerar
                int ult = n - 2; // último troféu
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: // sobe uma linha da grade; da primeira linha vai para o zerar
                        linhaT = linhaT == n - 1 ? Math.max(0, ult) : linhaT - colsT < 0 ? n - 1 : linhaT - colsT;
                        seguirT = true; confirmarZerar = false; invalidate(); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: // desce uma linha; da última vai para o zerar, do zerar para o começo
                        linhaT = linhaT == n - 1 ? 0 : linhaT + colsT > ult ? n - 1 : linhaT + colsT;
                        seguirT = true; confirmarZerar = false; invalidate(); return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (colsT > 1 && linhaT < n - 1 && linhaT % colsT > 0) linhaT--;
                        else if (!conjuntos.isEmpty()) { aba = (aba + conjuntos.size() - 1) % conjuntos.size(); linhaT = 0; rolagemT = 0; confirmarZerar = false; }
                        seguirT = true; invalidate(); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (colsT > 1 && linhaT < ult && linhaT % colsT < colsT - 1) linhaT++;
                        else if (!conjuntos.isEmpty()) { aba = (aba + 1) % conjuntos.size(); linhaT = 0; rolagemT = 0; confirmarZerar = false; }
                        seguirT = true; invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_L1:
                        if (!conjuntos.isEmpty()) { aba = (aba + conjuntos.size() - 1) % conjuntos.size(); linhaT = 0; rolagemT = 0; confirmarZerar = false; }
                        invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_R1:
                        if (!conjuntos.isEmpty()) { aba = (aba + 1) % conjuntos.size(); linhaT = 0; rolagemT = 0; confirmarZerar = false; }
                        invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        if (linhaT == n - 1) zerarAba();
                        return true;
                    case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ESCAPE: return voltar();
                    default: return super.onKeyDown(k, e);
                }
            }
            if (telaOp) {
                int n = Opcoes.PROPORCOES.length + 1;
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: escolhido = (escolhido + n - 1) % n; invalidate(); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: escolhido = (escolhido + 1) % n; invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        escolherProporcao(); return true;
                    case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ESCAPE: return voltar();
                    default: return super.onKeyDown(k, e);
                }
            }
            if (idiomas) {
                int n = Idioma.OPCOES.length;
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: escolhido = (escolhido + n - 1) % n; invalidate(); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: escolhido = (escolhido + 1) % n; invalidate(); return true;
                    case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        escolherIdioma(); return true;
                    case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ESCAPE: return voltar();
                    default: return super.onKeyDown(k, e);
                }
            }
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    andar(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    andar(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (indice(ALTO, foco) >= 0) focar(F_JOGAR);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (indice(BAIXO, foco) >= 0) focar(jogo);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                    acionar(foco);
                    return true;
                default:
                    return super.onKeyDown(k, e);
            }
        }
    }
}
