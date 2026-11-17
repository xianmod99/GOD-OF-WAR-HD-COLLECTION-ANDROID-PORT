package com.luisxl.gowcollection;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Tela "Mapear botões do controle": escolhe um botão do Vita, aperta o botão físico do controle e pronto
// (ex.: aperta o X do controle para ser o CROSS). Grava em <pasta do jogo>/padmap.txt, as mesmas linhas
// "key <código> = <BOTÃO>" que o PadFisico já lê ao abrir o jogo; por isso vale na próxima vez que o jogo abrir.
// Linhas do padmap.txt que não são "key" (lstick, rstick, l2, r2...) são mantidas como estão.
public final class MapearControleActivity extends Activity {
    // nome no padmap.txt, rótulo
    private static final String[][] BOTOES = {
            {"CROSS", "Cross (X)"}, {"CIRCLE", "Circle (O)"}, {"SQUARE", "Square"}, {"TRIANGLE", "Triangle"},
            {"L1", "L1"}, {"R1", "R1"}, {"L2", "L2"}, {"R2", "R2"},
            {"UP", "D-pad Up"}, {"DOWN", "D-pad Down"}, {"LEFT", "D-pad Left"}, {"RIGHT", "D-pad Right"},
            {"START", "Start"}, {"SELECT", "Select"}, {"L3", "L3 (stick click)"}, {"R3", "R3 (stick click)"},
    };
    // o layout padrão do PadFisico (Android: A embaixo, B à direita, X à esquerda, Y em cima)
    private static final Object[][] PADRAO = {
            {KeyEvent.KEYCODE_BUTTON_A, "CROSS"}, {KeyEvent.KEYCODE_BUTTON_B, "CIRCLE"},
            {KeyEvent.KEYCODE_BUTTON_X, "SQUARE"}, {KeyEvent.KEYCODE_BUTTON_Y, "TRIANGLE"},
            {KeyEvent.KEYCODE_BUTTON_L1, "L1"}, {KeyEvent.KEYCODE_BUTTON_R1, "R1"},
            {KeyEvent.KEYCODE_BUTTON_L2, "L2"}, {KeyEvent.KEYCODE_BUTTON_R2, "R2"},
            {KeyEvent.KEYCODE_BUTTON_THUMBL, "L3"}, {KeyEvent.KEYCODE_BUTTON_THUMBR, "R3"},
            {KeyEvent.KEYCODE_BUTTON_START, "START"}, {KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT"},
            {KeyEvent.KEYCODE_DPAD_UP, "UP"}, {KeyEvent.KEYCODE_DPAD_DOWN, "DOWN"},
            {KeyEvent.KEYCODE_DPAD_LEFT, "LEFT"}, {KeyEvent.KEYCODE_DPAD_RIGHT, "RIGHT"},
    };

    private static final int FUNDO = 0xFF121214, CARTAO = 0xFF1E1E22, DESTAQUE = 0xFF3B6FD4, TEXTO = 0xFFEDEDED, DISCRETO = 0xFF9A9AA2;

    private File arquivo;
    private final Map<Integer, String> mapa = new LinkedHashMap<>(); // código de tecla -> nome do botão (ou NONE)
    private final List<String> outras = new ArrayList<>();           // linhas do padmap.txt que não são "key"
    private int escutando = -1;
    private TextView aviso;
    private LinearLayout lista;
    private final TextView[] atuais = new TextView[BOTOES.length];
    private final View[] linhas = new View[BOTOES.length];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Pastas.carregar(this);
        arquivo = new File(Pastas.raiz(), "padmap.txt");
        carregar();
        montar();
        atualizar();
    }

    // ---------------------------------------------------------------- arquivo
    private static int codigo(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignorada) {
            String n = s.toUpperCase(Locale.ROOT);
            return KeyEvent.keyCodeFromString(n.startsWith("KEYCODE_") ? n : "KEYCODE_" + n);
        }
    }

    private static boolean nomeValido(String n) {
        if (n.equals("NONE")) return true;
        for (String[] bt : BOTOES) if (bt[0].equals(n)) return true;
        return false;
    }

    private void carregar() {
        mapa.clear();
        outras.clear();
        try {
            if (arquivo.isFile()) {
                for (String l : Files.readAllLines(arquivo.toPath(), StandardCharsets.UTF_8)) {
                    String limpa = l;
                    int h = limpa.indexOf('#');
                    if (h >= 0) limpa = limpa.substring(0, h);
                    limpa = limpa.trim();
                    int eq = limpa.indexOf('=');
                    String esq = eq > 0 ? limpa.substring(0, eq).trim() : "";
                    if (esq.toLowerCase(Locale.ROOT).startsWith("key") && esq.length() > 3 && Character.isWhitespace(esq.charAt(3))) {
                        int k = codigo(esq.substring(3).trim());
                        String nome = limpa.substring(eq + 1).trim().toUpperCase(Locale.ROOT);
                        if (k > 0 && nomeValido(nome)) mapa.put(k, nome);
                    } else if (!l.trim().isEmpty() && !l.startsWith("# Gerado")) {
                        outras.add(l);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }
        if (mapa.isEmpty()) padrao();
    }

    private void padrao() {
        mapa.clear();
        for (Object[] p : PADRAO) mapa.put((Integer) p[0], (String) p[1]);
    }

    private boolean salvar() {
        StringBuilder sb = new StringBuilder("# Gerado pela tela \"Mapear botoes\". Uma regra por linha: key <codigo> = <BOTAO>\n");
        for (Map.Entry<Integer, String> e : mapa.entrySet())
            sb.append("key ").append(e.getKey()).append(" = ").append(e.getValue()).append("  # ")
                    .append(KeyEvent.keyCodeToString(e.getKey())).append('\n');
        for (String l : outras) sb.append(l).append('\n');
        try {
            File pai = arquivo.getParentFile();
            if (pai != null) //noinspection ResultOfMethodCallIgnored
                pai.mkdirs();
            Files.write(arquivo.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            Toast.makeText(this, Tr.t("Não consegui gravar o padmap.txt: permita o acesso aos arquivos no lançador") + "\n" + arquivo, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ---------------------------------------------------------------- tela
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable fundo(int cor, float raio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raio));
        return g;
    }

    private Button botao(String texto, View.OnClickListener ao) {
        Button bt = new Button(this);
        bt.setText(texto);
        bt.setAllCaps(false);
        bt.setTextColor(TEXTO);
        bt.setBackground(fundo(0xFF34343C, 10));
        bt.setPadding(dp(14), 0, dp(14), 0);
        bt.setMinHeight(0);
        bt.setMinimumHeight(0);
        bt.setOnClickListener(ao);
        return bt;
    }

    private void montar() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FUNDO);
        raiz.setOnApplyWindowInsetsListener((v, ins) -> {
            @SuppressWarnings("deprecation") int l = ins.getSystemWindowInsetLeft(), t = ins.getSystemWindowInsetTop(),
                    r = ins.getSystemWindowInsetRight(), bt = ins.getSystemWindowInsetBottom();
            v.setPadding(l + dp(16), t + dp(10), r + dp(16), bt + dp(10));
            return ins;
        });
        raiz.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView titulo = new TextView(this);
        titulo.setText(Tr.t("Mapear botões do controle"));
        titulo.setTextColor(TEXTO);
        titulo.setTextSize(20);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        raiz.addView(titulo);

        TextView dica = new TextView(this);
        dica.setText(Tr.t("Toque em um botão do Vita e aperte o botão do controle que vai ser ele. Vale na próxima vez que o jogo abrir."));
        dica.setTextColor(DISCRETO);
        dica.setTextSize(13);
        dica.setPadding(0, dp(2), 0, dp(6));
        raiz.addView(dica);

        aviso = new TextView(this);
        aviso.setTextColor(Color.WHITE);
        aviso.setTextSize(15);
        aviso.setTypeface(Typeface.DEFAULT_BOLD);
        aviso.setGravity(Gravity.CENTER);
        aviso.setBackground(fundo(DESTAQUE, 10));
        aviso.setPadding(dp(10), dp(10), dp(10), dp(10));
        aviso.setVisibility(View.GONE);
        aviso.setOnClickListener(v -> parar());
        raiz.addView(aviso, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView rolagem = new ScrollView(this);
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        rolagem.addView(lista);
        raiz.addView(rolagem, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int i = 0; i < BOTOES.length; i++) {
            final int idx = i;
            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.HORIZONTAL);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            linha.setPadding(dp(14), dp(6), dp(10), dp(6));
            linha.setFocusable(true);
            linha.setOnClickListener(v -> comecar(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));

            TextView nome = new TextView(this);
            nome.setText(Tr.t(BOTOES[i][1]));
            nome.setTextColor(TEXTO);
            nome.setTextSize(16);
            nome.setTypeface(Typeface.DEFAULT_BOLD);
            linha.addView(nome, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            atuais[i] = new TextView(this);
            atuais[i].setTextColor(DISCRETO);
            atuais[i].setTextSize(14);
            atuais[i].setGravity(Gravity.END);
            linha.addView(atuais[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button limpar = botao(Tr.t("Limpar"), v -> limpar(idx));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            bp.setMargins(dp(10), 0, 0, 0);
            linha.addView(limpar, bp);

            linhas[i] = linha;
            lista.addView(linha, lp);
        }

        LinearLayout rodape = new LinearLayout(this);
        rodape.setOrientation(LinearLayout.HORIZONTAL);
        rodape.setGravity(Gravity.END);
        rodape.setPadding(0, dp(8), 0, 0);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        rp.setMargins(dp(10), 0, 0, 0);
        rodape.addView(botao(Tr.t("Restaurar padrão"), v -> {
            parar();
            padrao();
            if (salvar()) Toast.makeText(this, Tr.t("Padrão restaurado"), Toast.LENGTH_SHORT).show();
            atualizar();
        }), rp);
        rodape.addView(botao(Tr.t("Voltar"), v -> finish()), rp);
        raiz.addView(rodape, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(raiz);
    }

    private void atualizar() {
        for (int i = 0; i < BOTOES.length; i++) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, String> e : mapa.entrySet())
                if (e.getValue().equals(BOTOES[i][0])) {
                    if (sb.length() > 0) sb.append(", ");
                    String n = KeyEvent.keyCodeToString(e.getKey());
                    sb.append(n.startsWith("KEYCODE_") ? n.substring(8) : n);
                }
            atuais[i].setText(sb.length() > 0 ? sb.toString() : "—");
            linhas[i].setBackground(fundo(i == escutando ? DESTAQUE : CARTAO, 10));
        }
        if (escutando >= 0) {
            aviso.setText(String.format(Tr.t("Aperte agora o botão do controle para: %s  (toque aqui ou Voltar para cancelar)"),
                    Tr.t(BOTOES[escutando][1])));
            aviso.setVisibility(View.VISIBLE);
        } else {
            aviso.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- mapear
    private void comecar(int i) {
        escutando = i;
        atualizar();
    }

    private void parar() {
        escutando = -1;
        atualizar();
    }

    private void limpar(int i) {
        escutando = -1;
        for (Map.Entry<Integer, String> e : mapa.entrySet())
            if (e.getValue().equals(BOTOES[i][0])) e.setValue("NONE"); // NONE: o padrão daquela tecla também fica desligado
        salvar();
        atualizar();
    }

    private void atribuir(int tecla) {
        String nome = BOTOES[escutando][0];
        // um botão do Vita só tem uma tecla física: as que já eram dele ficam desligadas (NONE)
        for (Map.Entry<Integer, String> e : mapa.entrySet())
            if (e.getValue().equals(nome)) e.setValue("NONE");
        mapa.put(tecla, nome); // e a tecla física pertence só ao botão novo
        String n = KeyEvent.keyCodeToString(tecla);
        escutando = -1;
        if (salvar())
            Toast.makeText(this, (n.startsWith("KEYCODE_") ? n.substring(8) : n) + " → " + nome, Toast.LENGTH_SHORT).show();
        atualizar();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (escutando < 0) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(e);
        boolean controle = PadFisico.daFonte(e.getDevice()) || KeyEvent.isGamepadButton(k);
        if (!controle) return super.dispatchKeyEvent(e); // o "voltar" do celular cai no onBackPressed e cancela
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) atribuir(k);
        return true; // consome o ACTION_UP também, para não clicar em nada
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (escutando >= 0) parar();
        else super.onBackPressed();
    }
}
