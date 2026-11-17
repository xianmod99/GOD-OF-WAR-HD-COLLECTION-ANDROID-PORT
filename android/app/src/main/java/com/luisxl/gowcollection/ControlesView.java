package com.luisxl.gowcollection;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

// Os controles na tela por cima do jogo. Toque que não pega em nenhum controle vira o dedo no painel frontal do
// Vita (o menu da coletânea só anda por toque). O ômega no alto abre o menu dos controles.
final class ControlesView extends View {
    // bits do SceCtrlData (host_input.cpp do vitart)
    static final int SELECT = 0x1, L3 = 0x2, R3 = 0x4, START = 0x8, CIMA = 0x10, DIREITA = 0x20, BAIXO = 0x40,
            ESQUERDA = 0x80, L2 = 0x100, R2 = 0x200, L1 = 0x400, R1 = 0x800, TRIANGULO = 0x1000, CIRCULO = 0x2000,
            CRUZ = 0x4000, QUADRADO = 0x8000;

    interface Saida {
        void controles(int botoes, int lx, int ly, int rx, int ry);
        void toque(boolean dedo, float nx, float ny);
        void sair();
    }

    private enum Tipo { BOTAO, MINI, DIRECIONAL, ANALOGICO, MENU }

    // um controle: centro guardado como distância (dp) de uma âncora (0 esquerda/topo, 1 centro, 2 direita/base)
    private final class Controle {
        final String id;
        final Tipo tipo;
        final int bits, icone, cor;
        final String selo, rotulo; // selo: botão do PlayStation no canto; rotulo: legenda do analógico ou nome do mini
        final boolean esquerdo;
        final int ax, ay;
        final float dx0, dy0, tam0; // padrão (tam = raio)
        float dx, dy;
        // estado
        int dedo = -1;
        boolean apertado;
        int braco; // direcional: braços apertados em bits (1 cima, 2 direita, 4 baixo, 8 esquerda)
        float kx, ky;   // analógico: deslocamento do topo, -1..1
        long ultimoToque;
        Bitmap solto, preso, base;
        Bitmap[] bracos;

        Controle(String id, Tipo tipo, int bits, int icone, int cor, String selo, String rotulo, boolean esquerdo, int ax,
                 int ay, float dx, float dy, float tam) {
            this.id = id; this.tipo = tipo; this.bits = bits; this.icone = icone; this.cor = cor; this.selo = selo;
            this.rotulo = rotulo; this.esquerdo = esquerdo; this.ax = ax; this.ay = ay;
            this.dx0 = this.dx = dx; this.dy0 = this.dy = dy; this.tam0 = tam;
        }

        float escala() { return tipo == Tipo.MENU ? 1f : tamanho; }
        float raio() { return tam0 * dp * escala(); }

        float cx() { return ax == 0 ? dx * dp : ax == 1 ? getWidth() / 2f + dx * dp : getWidth() - dx * dp; }
        float cy() { return ay == 0 ? dy * dp : getHeight() - dy * dp; }

        // área de toque um pouco maior que o desenho (dedo é grosso)
        boolean dentro(float x, float y) {
            float cx = cx(), cy = cy();
            float r = raio() * (tipo == Tipo.ANALOGICO ? 1.2f : tipo == Tipo.BOTAO ? 1.15f : tipo == Tipo.MINI ? 1.4f : 1.1f);
            return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
        }

        void desenhar() {
            reciclar();
            switch (tipo) {
                case BOTAO:
                    solto = visual == 2 ? DesenhoMinimal.acao(raio(), icone, cor, selo, false) : visual == 1 ? DesenhoAero.acao(raio(), icone, cor, selo, false) : Desenho.acao(raio(), icone, cor, selo, false);
                    preso = visual == 2 ? DesenhoMinimal.acao(raio(), icone, cor, selo, true) : visual == 1 ? DesenhoAero.acao(raio(), icone, cor, selo, true) : Desenho.acao(raio(), icone, cor, selo, true);
                    break;
                case MINI:
                    solto = visual == 2 ? DesenhoMinimal.mini(raio(), rotulo, false) : visual == 1 ? DesenhoAero.mini(raio(), rotulo, false) : Desenho.mini(raio(), rotulo, false);
                    preso = visual == 2 ? DesenhoMinimal.mini(raio(), rotulo, true) : visual == 1 ? DesenhoAero.mini(raio(), rotulo, true) : Desenho.mini(raio(), rotulo, true);
                    break;
                case DIRECIONAL:
                    base = visual == 2 ? DesenhoMinimal.direcional(raio(), -1) : visual == 1 ? DesenhoAero.direcional(raio(), -1) : Desenho.direcional(raio(), -1);
                    bracos = new Bitmap[4];
                    for (int i = 0; i < 4; i++) bracos[i] = visual == 2 ? DesenhoMinimal.direcional(raio(), i) : visual == 1 ? DesenhoAero.direcional(raio(), i) : Desenho.direcional(raio(), i);
                    break;
                case ANALOGICO:
                    base = visual == 2 ? DesenhoMinimal.baseAnalogico(raio(), rotulo) : visual == 1 ? DesenhoAero.baseAnalogico(raio(), rotulo) : Desenho.baseAnalogico(raio(), rotulo);
                    solto = visual == 2 ? DesenhoMinimal.topoAnalogico(raio() * 0.5f, false) : visual == 1 ? DesenhoAero.topoAnalogico(raio() * 0.5f, false) : Desenho.topoAnalogico(raio() * 0.5f, false);
                    preso = visual == 2 ? DesenhoMinimal.topoAnalogico(raio() * 0.5f, true) : visual == 1 ? DesenhoAero.topoAnalogico(raio() * 0.5f, true) : Desenho.topoAnalogico(raio() * 0.5f, true);
                    break;
                case MENU:
                    solto = visual == 2 ? DesenhoMinimal.menu(raio(), false) : visual == 1 ? DesenhoAero.menu(raio(), false) : Desenho.menu(raio(), false);
                    preso = visual == 2 ? DesenhoMinimal.menu(raio(), true) : visual == 1 ? DesenhoAero.menu(raio(), true) : Desenho.menu(raio(), true);
                    break;
            }
        }

        void reciclar() {
            for (Bitmap b : new Bitmap[]{solto, preso, base}) if (b != null) b.recycle();
            if (bracos != null) for (Bitmap b : bracos) b.recycle();
            solto = preso = base = null;
            bracos = null;
        }
    }

    private static final String LAYOUT = "v2."; // posições do layout de ícones (as do primeiro ficam de lado)
    private final float densidade;
    private float dp; // dp de verdade vezes o encaixe na altura da tela (onSizeChanged)
    private final List<Controle> controles = new ArrayList<>();
    private final SharedPreferences prefs;
    private final Saida saida;
    private final Vibrator vibrador;
    private float tamanho = 1f, opacidade = 0.8f;
    private boolean vibrar = true, escondidos, editando, menuAberto;
    // visual: 0 Clássico (pedra e ouro do jogo), 1 Cristal (DualSense em vidro, Aero), 2 Minimalista (traço; máscara 3D ao tocar)
    private static final String[] VISUAIS = {Tr.t("Clássico"), Tr.t("Cristal"), Tr.t("Minimalista")}, VISUAIS_ID = {"classico", "cristal", "minimalista"};
    private int visual;
    private boolean autoEscondidos; // um controle físico mexeu: some até o próximo toque
    private int dedoTela = -1;      // o dedo que está no painel de toque do Vita
    private int botoes;
    private final Paint colar = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF[] itens = new RectF[9];
    private final float[] meioAjuste = new float[9]; // Cristal: x entre o − e o + de cada ajuste

    ControlesView(Context ctx, Saida saida) {
        super(ctx);
        this.saida = saida;
        densidade = dp = ctx.getResources().getDisplayMetrics().density;
        prefs = ctx.getSharedPreferences("controles", Context.MODE_PRIVATE);
        vibrador = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        // Distâncias em dp a partir da âncora (0 esquerda/topo, 1 centro, 2 direita/base). Direita: o losango dos botões
        // de face e, num arco por cima, defender, magia e usar; esquerda: o analógico grande e a cruz pequena no alto.
        controles.add(new Controle("cru", Tipo.BOTAO, CRUZ, Icones.PULO, Desenho.AZUL, "cru", null, false, 2, 2, 100, 62, 30));
        controles.add(new Controle("qua", Tipo.BOTAO, QUADRADO, Icones.LAMINA, Desenho.VERMELHO, "qua", null, false, 2, 2, 160, 122, 30));
        controles.add(new Controle("tri", Tipo.BOTAO, TRIANGULO, Icones.PESADO, Desenho.VERMELHO, "tri", null, false, 2, 2, 100, 182, 30));
        controles.add(new Controle("cir", Tipo.BOTAO, CIRCULO, Icones.MAO, Desenho.DOURADO, "cir", null, false, 2, 2, 40, 122, 30));
        controles.add(new Controle("l1", Tipo.BOTAO, L1, Icones.ESCUDO, Desenho.DOURADO, "L1", null, false, 2, 2, 178, 236, 24));
        controles.add(new Controle("r2", Tipo.BOTAO, R2, Icones.RAIO, Desenho.ROXO, "R2", null, false, 2, 2, 112, 262, 24));
        controles.add(new Controle("r1", Tipo.BOTAO, R1, Icones.CHAVE, Desenho.DOURADO, "R1", null, false, 2, 2, 46, 240, 24));
        controles.add(new Controle("dir", Tipo.ANALOGICO, R3, 0, 0, null, "Rolar", false, 2, 2, 262, 82, 56));
        controles.add(new Controle("r3", Tipo.MINI, R3, 0, 0, null, "R3", false, 2, 2, 318, 150, 15));
        controles.add(new Controle("esq", Tipo.ANALOGICO, L3, 0, 0, null, "Mover", true, 0, 2, 122, 104, 74));
        controles.add(new Controle("l3", Tipo.MINI, L3, 0, 0, null, "L3", true, 0, 2, 122, 208, 15));
        controles.add(new Controle("dpad", Tipo.DIRECIONAL, 0, 0, 0, null, null, true, 0, 0, 66, 96, 44));
        controles.add(new Controle("l2", Tipo.BOTAO, L2, Icones.RUNA, Desenho.CINZA, "L2", null, true, 0, 0, 152, 54, 22));
        controles.add(new Controle("sel", Tipo.BOTAO, SELECT, Icones.PAUSA, Desenho.CINZA, null, null, true, 1, 0, -70, 30, 18));
        controles.add(new Controle("sta", Tipo.BOTAO, START, Icones.EVOLUCAO, Desenho.CINZA, null, null, false, 1, 0, 70, 30, 18));
        controles.add(new Controle("menu", Tipo.MENU, 0, 0, 0, null, null, false, 1, 0, 0, 30, 22));
        ler();
    }

    private void ler() {
        tamanho = prefs.getFloat("tamanho", 1f);
        opacidade = prefs.getFloat("opacidade", 0.8f);
        vibrar = prefs.getBoolean("vibrar", true);
        escondidos = prefs.getBoolean("escondidos", false);
        visual = Math.max(0, java.util.Arrays.asList(VISUAIS_ID).indexOf(prefs.getString("visual", "cristal")));
        if (!prefs.getBoolean("v3", false)) { // uma vez: volta ao visual do DualSense (Cristal) e mostra os controles
            visual = 1;
            escondidos = false;
            prefs.edit().putBoolean("v3", true).putString("visual", "cristal").putBoolean("escondidos", false).apply();
        }
        for (Controle c : controles) {
            c.dx = prefs.getFloat(LAYOUT + c.id + ".x", c.dx0);
            c.dy = prefs.getFloat(LAYOUT + c.id + ".y", c.dy0);
        }
    }

    private void gravar() {
        SharedPreferences.Editor e = prefs.edit();
        e.putFloat("tamanho", tamanho).putFloat("opacidade", opacidade).putBoolean("vibrar", vibrar)
                .putBoolean("escondidos", escondidos).putString("visual", VISUAIS_ID[visual]);
        for (Controle c : controles) e.putFloat(LAYOUT + c.id + ".x", c.dx).putFloat(LAYOUT + c.id + ".y", c.dy);
        e.apply();
    }

    private void redesenhar() {
        for (Controle c : controles) c.desenhar();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // o layout foi montado para 420 dp de altura: tela mais baixa (20:9) encolhe tudo na mesma proporção
        if (h > 0) dp = densidade * Math.min(1f, h / densidade / 420f);
        if (w > 0 && h > 0) redesenhar();
    }

    // Os botões da tela somem quando o controle físico é usado, até alguém tocar na tela (false: ficam sempre à vista).
    private static final boolean AUTO_ESCONDER = true;

    // um controle físico mexeu. HYBRID: isso só esconde o desenho, e só com a tela livre; nunca solta um dedo que está
    // num botão, analógico ou no painel de toque, e o próximo toque volta tudo na hora (onTouchEvent)
    void controleFisico() {
        if (!AUTO_ESCONDER || autoEscondidos) return;
        if (dedoTela >= 0) return;
        for (Controle c : controles) if (c.dedo >= 0) return;
        autoEscondidos = true;
        invalidate();
    }

    private boolean visiveis() { return !escondidos && !autoEscondidos; }

    // ---------------------------------------------------------------- desenho
    @Override
    protected void onDraw(Canvas c) {
        int alfa = (int) (opacidade * 255);
        for (Controle k : controles) {
            if (k.tipo != Tipo.MENU && !visiveis() && !editando) continue;
            colar.setAlpha(k.tipo == Tipo.MENU ? Math.max(alfa, 170) : alfa);
            float cx = k.cx(), cy = k.cy();
            switch (k.tipo) {
                case DIRECIONAL:
                    if (k.base == null) break;
                    centro(c, k.base, cx, cy);
                    for (int i = 0; i < 4; i++) if ((k.braco & (1 << i)) != 0) centro(c, k.bracos[i], cx, cy);
                    break;
                case ANALOGICO:
                    if (k.base == null) break;
                    centro(c, k.base, cx, cy);
                    float alc = k.raio() * 0.55f;
                    centro(c, k.dedo >= 0 ? k.preso : k.solto, cx + k.kx * alc, cy + k.ky * alc);
                    break;
                default:
                    Bitmap b = k.apertado ? k.preso : k.solto;
                    if (b != null) centro(c, b, cx, cy);
            }
            if (editando && k.tipo != Tipo.MENU) { // contorno de brasa no que dá para arrastar
                Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
                e.setStyle(Paint.Style.STROKE);
                e.setStrokeWidth(1.5f * dp);
                e.setColor(0xAAFF8A30);
                e.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * dp, 4 * dp}, 0));
                c.drawCircle(cx, cy, k.raio() * 1.08f, e);
            }
        }
        if (editando) {
            faixa(c, visual != 0 ? Tr.t("Arraste os controles  ·  toque no Ω para terminar") : Tr.t("Arraste os controles  ·  toque no ômega para terminar"));
            botoesEdicao(c);
        }
        if (previa && !editando && !menuAberto) botaoLancador(c);
        else btLancador.setEmpty();
        if (menuAberto) painel(c);
        avisarTopoLivre();
    }

    // o aviso de troféu desce para baixo dos controles que cruzam o meio de cima da tela (pausa, Ω, ...)
    private void avisarTopoLivre() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float baixo = 0;
        for (Controle k : controles) {
            if (k.tipo != Tipo.MENU && !visiveis() && !editando) continue;
            float r = k.raio() * (k.tipo == Tipo.BOTAO ? 1.1f : 1f), cx = k.cx(), cy = k.cy();
            if (Math.abs(cx - w / 2) > w * 0.26f + r || cy - r > h * 0.45f) continue;
            baixo = Math.max(baixo, cy + r);
        }
        float frac = baixo / h;
        if (Math.abs(frac - topoLivre) < 0.002f) return;
        topoLivre = frac;
        GameActivity.topoLivreTrofeu(frac);
    }

    private void comecarEdicao() {
        antesDeEditar = new float[controles.size() * 2];
        for (int i = 0; i < controles.size(); i++) {
            antesDeEditar[2 * i] = controles.get(i).dx;
            antesDeEditar[2 * i + 1] = controles.get(i).dy;
        }
        editando = true;
        escondidos = false;
        menuAberto = false;
    }

    // sai do modo de edição (as posições ficam) e volta ao menu, de onde ele veio
    private void terminarEdicao() {
        editando = false;
        arrastado = null;
        menuAberto = true;
        gravar();
        tique();
        invalidate();
    }

    private void desfazerEdicao() {
        if (antesDeEditar == null) return;
        for (int i = 0; i < controles.size() && 2 * i + 1 < antesDeEditar.length; i++) {
            controles.get(i).dx = antesDeEditar[2 * i];
            controles.get(i).dy = antesDeEditar[2 * i + 1];
        }
        gravar();
        tique();
        invalidate();
    }

    // o voltar do sistema: sai da edição ou fecha o menu; false se não havia nada aberto
    boolean voltar() {
        if (editando) { terminarEdicao(); return true; }
        if (menuAberto) { menuAberto = false; invalidate(); return true; }
        return false;
    }

    // na prévia: "‹ Voltar ao lançador" no meio, embaixo (entre os analógicos)
    private void botaoLancador(Canvas c) {
        String s = "‹  " + Tr.t("Voltar ao lançador");
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(DesenhoAero.SISTEMA);
        t.setTextSize(15 * dp);
        t.setTextAlign(Paint.Align.CENTER);
        float h = 42 * dp, w = t.measureText(s) + 48 * dp, cx = getWidth() / 2f, cy = getHeight() - 36 * dp - h / 2;
        btLancador.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        Paint sombra = new Paint(Paint.ANTI_ALIAS_FLAG);
        sombra.setColor(0x80000000);
        sombra.setMaskFilter(new android.graphics.BlurMaskFilter(10 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(btLancador.left, btLancador.top + 4 * dp, btLancador.right, btLancador.bottom + 4 * dp), h / 2, h / 2, sombra);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(0xF2FFFFFF);
        c.drawRoundRect(btLancador, h / 2, h / 2, f);
        t.setColor(0xFF101113);
        c.drawText(s, cx, cy + t.getTextSize() * 0.36f, t);
    }

    // "‹ Voltar" e "Desfazer" embaixo da faixa, no meio
    private void botoesEdicao(Canvas c) {
        String v = "‹  " + Tr.t("Voltar"), d = Tr.t("Desfazer");
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(DesenhoAero.SISTEMA);
        t.setTextSize(14 * dp);
        t.setTextAlign(Paint.Align.CENTER);
        float h = 38 * dp, gap = 12 * dp, cy = 74 * dp + 17 * dp + 12 * dp + h / 2;
        float wv = t.measureText(v) + 40 * dp, wd = t.measureText(d) + 40 * dp, x0 = getWidth() / 2f - (wv + gap + wd) / 2;
        btVoltar.set(x0, cy - h / 2, x0 + wv, cy + h / 2);
        btDesfazer.set(btVoltar.right + gap, cy - h / 2, btVoltar.right + gap + wd, cy + h / 2);
        for (int i = 0; i < 2; i++) {
            RectF r = i == 0 ? btVoltar : btDesfazer;
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
            a.setStyle(Paint.Style.STROKE);
            a.setStrokeWidth(1.2f * dp);
            if (i == 0) { // o principal: branco
                f.setColor(0xF2FFFFFF);
                a.setColor(0xFFFFFFFF);
            } else {
                f.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xD9303236, 0xF0101113, Shader.TileMode.CLAMP));
                a.setColor(0x66FFFFFF);
            }
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setColor(0x80000000);
            s.setMaskFilter(new android.graphics.BlurMaskFilter(8 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(new RectF(r.left, r.top + 4 * dp, r.right, r.bottom + 4 * dp), h / 2, h / 2, s);
            c.drawRoundRect(r, h / 2, h / 2, f);
            c.drawRoundRect(r, h / 2, h / 2, a);
            t.setColor(i == 0 ? 0xFF101113 : 0xFFF7FAFF);
            c.drawText(i == 0 ? v : d, r.centerX(), r.centerY() + t.getTextSize() * 0.36f, t);
        }
    }

    private void centro(Canvas c, Bitmap b, float cx, float cy) {
        c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, colar);
    }

    // aviso em cima, na fonte do jogo com brilho de brasa
    private void faixa(Canvas c, String s) {
        if (visual != 0) { // cápsula de vidro com o texto do sistema
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setTypeface(DesenhoAero.SISTEMA);
            t.setTextSize(13 * dp);
            t.setTextAlign(Paint.Align.CENTER);
            float w = t.measureText(s) + 36 * dp, cx = getWidth() / 2f, cy = 74 * dp;
            RectF r = new RectF(cx - w / 2, cy - 17 * dp, cx + w / 2, cy + 17 * dp);
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(0, r.top, 0, r.bottom, visual == 2 ? 0xE0303236 : 0xE0304662, visual == 2 ? 0xF0101113 : 0xF00C1420, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, f);
            Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
            a.setStyle(Paint.Style.STROKE);
            a.setStrokeWidth(1 * dp);
            a.setColor(0x59FFFFFF);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, a);
            t.setColor(0xFFF7FAFF);
            c.drawText(s, cx, cy + t.getTextSize() * 0.36f, t);
            return;
        }
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Arte.OURO_CLARO);
        t.setShadowLayer(8 * dp, 0, 0, Arte.BRASA);
        Arte.texto(c, s, getWidth() / 2f, 74 * dp, 13 * dp, t, true);
    }

    // o menu dos controles: caixa de pedra com o friso do jogo em cima e embaixo, itens de texto com brilho de fogo
    private String[] textosMenu() {
        return new String[]{
                Tr.t("Continuar"),
                Tr.t("Opacidade   ") + Math.round(opacidade * 100) + "%",
                Tr.t("Tamanho   ") + Math.round(tamanho * 100) + "%",
                Tr.t("Vibração   ") + (vibrar ? Tr.t("ligada") : Tr.t("desligada")),
                Tr.t("Visual   ") + VISUAIS[visual],
                escondidos ? Tr.t("Mostrar controles") : Tr.t("Esconder controles"),
                Tr.t("Mudar posições"),
                Tr.t("Posições originais"),
                Tr.t("Sair do jogo")};
    }

    private RectF caixaMenu() {
        float w = Math.min(getWidth() * 0.62f, 460 * dp), h = Math.min(getHeight() * 0.86f, 360 * dp);
        return new RectF((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
    }

    private void painel(Canvas c) {
        if (visual != 0) { painelCristal(c); return; }
        Paint veu = new Paint();
        veu.setColor(0xA0000000);
        c.drawRect(0, 0, getWidth(), getHeight(), veu);
        RectF cx = caixaMenu();
        Paint pedra = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Arte.pedra(pedra, cx.centerX(), cx.centerY(), cx.width() / 2);
        c.drawRoundRect(cx, 6 * dp, 6 * dp, pedra);
        Paint sombra = new Paint(Paint.ANTI_ALIAS_FLAG);
        sombra.setShader(new LinearGradient(0, cx.top, 0, cx.bottom, new int[]{0x66000000, 0x10000000, 0x88000000},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(cx, 6 * dp, 6 * dp, sombra);
        float fh = 14 * dp;
        if (Arte.friso != null) {
            Paint f = new Paint(Paint.FILTER_BITMAP_FLAG);
            BitmapShader bs = new BitmapShader(Arte.friso, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            float s = fh / Arte.friso.getHeight();
            m.setScale(s, s);
            m.postTranslate(cx.left, cx.top);
            bs.setLocalMatrix(m);
            f.setShader(bs);
            c.drawRect(cx.left, cx.top, cx.right, cx.top + fh, f);
            m.postTranslate(0, cx.height() - fh);
            bs.setLocalMatrix(m);
            c.drawRect(cx.left, cx.bottom - fh, cx.right, cx.bottom, f);
        } else {
            Paint o = new Paint(Paint.ANTI_ALIAS_FLAG);
            o.setShader(Arte.ouroLinear(cx.left, 0, cx.right, 0));
            c.drawRect(cx.left, cx.top + fh * 0.35f, cx.right, cx.top + fh * 0.6f, o);
            c.drawRect(cx.left, cx.bottom - fh * 0.6f, cx.right, cx.bottom - fh * 0.35f, o);
        }
        Paint borda = new Paint(Paint.ANTI_ALIAS_FLAG);
        borda.setStyle(Paint.Style.STROKE);
        borda.setStrokeWidth(2 * dp);
        borda.setShader(Arte.ouroLinear(cx.left, cx.top, cx.right, cx.bottom));
        c.drawRoundRect(cx, 6 * dp, 6 * dp, borda);

        String[] t = textosMenu();
        float topo = cx.top + fh + 10 * dp, passo = (cx.height() - 2 * fh - 20 * dp) / t.length;
        float alt = Math.min(15 * dp, passo * 0.42f);
        for (int i = 0; i < t.length; i++) {
            float y = topo + passo * i;
            itens[i] = new RectF(cx.left, y, cx.right, y + passo);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean ajuste = i >= 1 && i <= 2;
            p.setColor(i == t.length - 1 ? 0xFFE0A08A : Arte.OURO_CLARO);
            p.setShadowLayer(6 * dp, 0, 0, i == 0 ? Arte.BRASA : 0xAA7A2A0A);
            String s = ajuste ? "-   " + t[i] + "   +" : t[i]; // a fonte do jogo não tem setas
            Arte.texto(c, s, cx.centerX(), y + passo / 2 + alt / 2, alt, p, true);
        }
    }

    // o menu no visual Cristal: painel de vidro escuro de cantos grandes, texto do sistema, linhas finas entre os itens
    private void painelCristal(Canvas c) {
        Paint veu = new Paint();
        veu.setColor(0x8C000000);
        c.drawRect(0, 0, getWidth(), getHeight(), veu);
        RectF cx = caixaMenu();
        float raio = 22 * dp;
        Paint sombra = new Paint(Paint.ANTI_ALIAS_FLAG);
        sombra.setColor(0x99000000);
        sombra.setMaskFilter(new android.graphics.BlurMaskFilter(24 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(cx.left, cx.top + 8 * dp, cx.right, cx.bottom + 8 * dp), raio, raio, sombra);
        Paint corpo = new Paint(Paint.ANTI_ALIAS_FLAG);
        corpo.setShader(new LinearGradient(0, cx.top, 0, cx.bottom, visual == 2 ? new int[]{0xF2303236, 0xF51E1F22, 0xF80F1012} : new int[]{0xF22B3E57, 0xF5172435, 0xF80A111B}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(cx, raio, raio, corpo);
        c.save();
        Path forma = new Path();
        forma.addRoundRect(cx, raio, raio, Path.Direction.CW);
        c.clipPath(forma);
        Paint brilho = new Paint(Paint.ANTI_ALIAS_FLAG); // o brilho do Aero na parte de cima
        RectF ov = new RectF(cx.left - cx.width() * 0.1f, cx.top - cx.height() * 0.32f, cx.right + cx.width() * 0.1f, cx.top + cx.height() * 0.22f);
        brilho.setShader(new LinearGradient(0, ov.top, 0, ov.bottom, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawOval(ov, brilho);
        Paint agua = new Paint(Paint.ANTI_ALIAS_FLAG); // a luz azul-água que sobe de baixo
        agua.setShader(new android.graphics.RadialGradient(cx.centerX(), cx.bottom, cx.width() * 0.6f, visual == 2 ? 0x14FFFFFF : 0x335CD0FF, visual == 2 ? 0x00FFFFFF : 0x005CD0FF, Shader.TileMode.CLAMP));
        c.drawRect(cx, agua);
        c.restore();
        Paint aro = new Paint(Paint.ANTI_ALIAS_FLAG);
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(1.2f * dp);
        aro.setShader(new LinearGradient(0, cx.top, 0, cx.bottom, 0x8CFFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(cx, raio, raio, aro);

        String[] t = textosMenu();
        float topo = cx.top + 12 * dp, passo = (cx.height() - 24 * dp) / t.length;
        Paint linha = new Paint();
        linha.setColor(0x1AFFFFFF);
        for (int i = 0; i < t.length; i++) {
            float y = topo + passo * i;
            itens[i] = new RectF(cx.left, y, cx.right, y + passo);
            if (i > 0) c.drawRect(cx.left + 20 * dp, y, cx.right - 20 * dp, y + Math.max(1f, 0.6f * dp), linha);
            boolean ajuste = i >= 1 && i <= 2;
            String[] partes = t[i].split(" {3}");
            Paint n = new Paint(Paint.ANTI_ALIAS_FLAG);
            n.setTypeface(i == 0 ? DesenhoAero.SISTEMA : android.graphics.Typeface.DEFAULT);
            n.setTextSize(Math.min(15 * dp, passo * 0.4f));
            n.setColor(i == t.length - 1 ? 0xFFFF7B72 : i == 0 ? (visual == 2 ? 0xFFFFFFFF : 0xFF5CD0FF) : 0xFFF7FAFF);
            float by = y + passo / 2 + n.getTextSize() * 0.36f;
            c.drawText(partes[0], cx.left + 22 * dp, by, n);
            if (partes.length > 1) { // o valor à direita, mais apagado; os ajustes com − e +
                Paint v = new Paint(n);
                v.setTypeface(android.graphics.Typeface.DEFAULT);
                v.setColor(0xA6F7FAFF);
                v.setTextAlign(Paint.Align.RIGHT);
                String val = ajuste ? "−    " + partes[1] + "    +" : partes[1];
                c.drawText(val, cx.right - 22 * dp, by, v);
                if (ajuste) meioAjuste[i] = cx.right - 22 * dp - v.measureText(val) / 2; // à esquerda daqui é −
            }
        }
    }

    private void tocarMenu(float x, float y) {
        RectF cx = caixaMenu();
        if (!cx.contains(x, y)) {
            menuAberto = false;
            invalidate();
            return;
        }
        int i = -1;
        for (int k = 0; k < itens.length; k++) if (itens[k] != null && itens[k].contains(x, y)) i = k;
        boolean mais = x > (visual != 0 && i >= 0 && meioAjuste[i] > 0 ? meioAjuste[i] : cx.centerX());
        switch (i) {
            case 0: menuAberto = false; break;
            case 1: opacidade = Math.max(0.2f, Math.min(1f, opacidade + (mais ? 0.1f : -0.1f))); break;
            case 2:
                tamanho = Math.max(0.6f, Math.min(1.5f, tamanho + (mais ? 0.1f : -0.1f)));
                redesenhar();
                break;
            case 3: vibrar = !vibrar; break;
            case 4: visual = (visual + 1) % VISUAIS.length; redesenhar(); break;
            case 5: escondidos = !escondidos; menuAberto = false; break;
            case 6: comecarEdicao(); break;
            case 7:
                for (Controle c : controles) { c.dx = c.dx0; c.dy = c.dy0; }
                tamanho = 1f;
                redesenhar();
                break;
            case 8: menuAberto = false; saida.sair(); break;
            default: return;
        }
        tique();
        gravar();
        invalidate();
    }

    private void tique() {
        if (!vibrar || vibrador == null) return;
        if (Build.VERSION.SDK_INT >= 29) vibrador.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        else vibrador.vibrate(VibrationEffect.createOneShot(12, 90));
    }

    // ---------------------------------------------------------------- toque
    private Controle arrastado;
    private float arrastoX, arrastoY;
    // modo de mudar posições: os botões Voltar (termina e volta ao menu) e Desfazer (as posições de antes de entrar)
    private final RectF btVoltar = new RectF(), btDesfazer = new RectF();
    private float[] antesDeEditar;
    private float topoLivre = -1; // o último valor mandado ao aviso de troféu
    private boolean previa;          // aberta pelo lançador só para arrumar os controles: mostra o botão de voltar
    private final RectF btLancador = new RectF();

    void previa(boolean p) { previa = p; invalidate(); }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int acao = ev.getActionMasked(), i = ev.getActionIndex();
        // toque no aviso de troféu (no alto, no centro): abre a descrição e o toque é dele
        if ((acao == MotionEvent.ACTION_DOWN || acao == MotionEvent.ACTION_POINTER_DOWN)
                && GameActivity.tocarTrofeu(ev.getX(i), ev.getY(i))) return true;
        if (autoEscondidos && acao == MotionEvent.ACTION_DOWN) {
            autoEscondidos = false;
            invalidate();
        }
        if (menuAberto) {
            if (acao == MotionEvent.ACTION_DOWN) tocarMenu(ev.getX(), ev.getY());
            return true;
        }
        if (acao == MotionEvent.ACTION_DOWN && !editando && btLancador.contains(ev.getX(), ev.getY())) {
            tique();
            saida.sair();
            return true;
        }
        if (editando) return editar(ev);
        switch (acao) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                apertar(ev.getPointerId(i), ev.getX(i), ev.getY(i));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int k = 0; k < ev.getPointerCount(); k++) mover(ev.getPointerId(k), ev.getX(k), ev.getY(k));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                soltar(ev.getPointerId(i));
                break;
            case MotionEvent.ACTION_CANCEL:
                soltarTudo();
                break;
        }
        enviar();
        invalidate();
        return true;
    }

    private void apertar(int id, float x, float y) {
        for (int k = controles.size() - 1; k >= 0; k--) {
            Controle c = controles.get(k);
            if (c.tipo != Tipo.MENU && !visiveis()) continue;
            if (c.dedo >= 0 || !c.dentro(x, y)) continue;
            if (c.tipo == Tipo.MENU) {
                menuAberto = true;
                tique();
                return;
            }
            c.dedo = id;
            if (c.tipo == Tipo.ANALOGICO) { // dois toques rápidos no analógico = L3/R3
                long agora = System.currentTimeMillis();
                if (agora - c.ultimoToque < 280) c.apertado = true;
                c.ultimoToque = agora;
            }
            mover(id, x, y);
            tique();
            return;
        }
        if (dedoTela < 0) { // painel de toque do Vita
            dedoTela = id;
            toqueTela(true, x, y);
        }
    }

    private void mover(int id, float x, float y) {
        if (id == dedoTela) {
            toqueTela(true, x, y);
            return;
        }
        for (Controle c : controles) {
            if (c.dedo != id) continue;
            float dx = x - c.cx(), dy = y - c.cy();
            switch (c.tipo) {
                case ANALOGICO: {
                    float alc = c.raio() * 0.55f;
                    float nx = dx / alc, ny = dy / alc, m = (float) Math.hypot(nx, ny);
                    if (m > 1) { nx /= m; ny /= m; }
                    c.kx = nx; c.ky = ny;
                    break;
                }
                case DIRECIONAL: {
                    int b = 0;
                    float zona = c.raio() * 0.18f;
                    if (Math.hypot(dx, dy) > zona) {
                        double a = Math.toDegrees(Math.atan2(dy, dx)); // 0 = direita, 90 = baixo
                        // oito direções: cada uma com 45 graus, diagonais apertam dois braços
                        int setor = (int) Math.floor(((a + 360 + 22.5) % 360) / 45);
                        int[] mapa = {2, 2 | 4, 4, 4 | 8, 8, 8 | 1, 1, 1 | 2};
                        b = mapa[setor];
                    }
                    if (b != c.braco && b != 0) tique();
                    c.braco = b;
                    break;
                }
                case BOTAO: {
                    // deslizar de um botão para outro aperta o outro (como no controle de verdade)
                    if (!c.dentro(x, y)) {
                        Controle outro = botaoEm(x, y);
                        if (outro != null && outro.dedo < 0) {
                            c.dedo = -1; c.apertado = false;
                            outro.dedo = id; outro.apertado = true;
                            tique();
                        }
                    } else c.apertado = true;
                    break;
                }
                default:
                    c.apertado = true;
            }
        }
    }

    private Controle botaoEm(float x, float y) {
        for (Controle c : controles) if (c.tipo == Tipo.BOTAO && c.dentro(x, y)) return c;
        return null;
    }

    private void soltar(int id) {
        if (id == dedoTela) {
            dedoTela = -1;
            saida.toque(false, 0, 0);
        }
        for (Controle c : controles) {
            if (c.dedo != id) continue;
            c.dedo = -1;
            c.apertado = false;
            c.braco = 0;
            c.kx = c.ky = 0;
        }
    }

    private void soltarTudo() {
        for (Controle c : controles) {
            c.dedo = -1;
            c.apertado = false;
            c.braco = 0;
            c.kx = c.ky = 0;
        }
        if (dedoTela >= 0) saida.toque(false, 0, 0);
        dedoTela = -1;
        enviar();
    }

    // dedo na imagem do jogo, que fica centrada na proporção do Vita (960x544)
    private void toqueTela(boolean dedo, float x, float y) {
        float w = getWidth(), h = getHeight(), a = 960f / 544f;
        float iw = w, ih = w / a;
        if (ih > h) { ih = h; iw = h * a; }
        float nx = (x - (w - iw) / 2) / iw, ny = (y - (h - ih) / 2) / ih;
        saida.toque(dedo, Math.max(0, Math.min(1, nx)), Math.max(0, Math.min(1, ny)));
    }

    private void enviar() {
        int b = 0, lx = 128, ly = 128, rx = 128, ry = 128;
        for (Controle c : controles) {
            if (c.tipo == Tipo.DIRECIONAL) {
                if ((c.braco & 1) != 0) b |= CIMA;
                if ((c.braco & 2) != 0) b |= DIREITA;
                if ((c.braco & 4) != 0) b |= BAIXO;
                if ((c.braco & 8) != 0) b |= ESQUERDA;
            } else if (c.tipo == Tipo.ANALOGICO) {
                int x = Math.round(128 + c.kx * 127), y = Math.round(128 + c.ky * 127);
                if (c.esquerdo) { lx = x; ly = y; } else { rx = x; ry = y; }
                if (c.apertado) b |= c.bits;
            } else if (c.apertado) b |= c.bits;
        }
        botoes = b;
        saida.controles(b, lx, ly, rx, ry);
    }

    // modo de mudar posições: arrasta pelo centro, a posição fica em dp a partir da âncora do controle
    private boolean editar(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                arrastado = null;
                if (btVoltar.contains(x, y)) { terminarEdicao(); return true; }
                if (btDesfazer.contains(x, y)) { desfazerEdicao(); return true; }
                for (int k = controles.size() - 1; k >= 0; k--) {
                    Controle c = controles.get(k);
                    if (c.dentro(x, y)) { arrastado = c; break; }
                }
                if (arrastado != null && arrastado.tipo == Tipo.MENU) {
                    editando = false;
                    arrastado = null;
                    gravar();
                    tique();
                } else if (arrastado != null) {
                    arrastoX = x - arrastado.cx();
                    arrastoY = y - arrastado.cy();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (arrastado != null) {
                    float cx = x - arrastoX, cy = y - arrastoY;
                    Controle c = arrastado;
                    c.dx = (c.ax == 0 ? cx : c.ax == 1 ? cx - getWidth() / 2f : getWidth() - cx) / dp;
                    c.dy = (c.ay == 0 ? cy : getHeight() - cy) / dp;
                }
                break;
            case MotionEvent.ACTION_UP:
                arrastado = null;
                gravar();
                break;
        }
        invalidate();
        return true;
    }

    int botoes() { return botoes; }
}
