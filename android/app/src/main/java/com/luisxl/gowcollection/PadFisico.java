package com.luisxl.gowcollection;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Locale;

// O controle físico (USB/OTG, Bluetooth) traduzido para o SceCtrlData do Vita (bits do ControlesView).
//
// Nada aqui assume um layout fixo: os eixos saem do MotionRange de cada InputDevice, e os botões aceitam tanto os
// KEYCODE_BUTTON_A/B/X/Y... "padrão do Android" quanto os KEYCODE_BUTTON_1..16 de um joystick HID genérico (o que um
// PS3 USB vira quando o kernel do aparelho não tem o driver da Sony). O estado é por dispositivo e somado no fim, assim
// um segundo InputDevice do mesmo controle (sensores de movimento, por exemplo) não zera os analógicos do primeiro.
//
// Se um controle ainda vier trocado, <pasta do jogo>/padmap.txt corrige sem recompilar (veja carregarMapa) e o logcat
// (tag GoWPad) mostra os eixos e os códigos de tecla que o aparelho realmente entrega.
final class PadFisico {
    private static final String TAG = "GoWPad";
    private static final float ZONA_MIN = 0.12f, ZONA_MAX = 0.30f;
    // L2/R2 chegam ao jogo só como botão (hle_sys.cpp::ctrl_read não lê gatilho analógico). Liga perto dos 64/255 do
    // caminho XInput do próprio vitart, com histerese para o gatilho não tremer.
    private static final float GATILHO_LIGA = 0.3f, GATILHO_DESLIGA = 0.2f;
    private static final int AUTO = -2, NENHUM = -1; // padmap.txt: eixo escolhido pelo programa / sem eixo

    // padmap.txt
    private final SparseIntArray teclasUsuario = new SparseIntArray(); // keycode -> bit (0 = ignorar)
    private int uLx = AUTO, uLy = AUTO, uRx = AUTO, uRy = AUTO, uL2 = AUTO, uR2 = AUTO;

    private final SparseArray<Dispositivo> dispositivos = new SparseArray<>();
    private final SparseBooleanArray teclasAvisadas = new SparseBooleanArray();

    // soma de todos os dispositivos
    private int somaBotoes;
    private float somaLx, somaLy, somaRx, somaRy;

    // ---------------------------------------------------------------- consulta
    int botoes() { return somaBotoes; }
    float lx() { return somaLx; }
    float ly() { return somaLy; }
    float rx() { return somaRx; }
    float ry() { return somaRy; }

    // o controle está sendo usado agora (botão, direcional, gatilho ou analógico fora da zona morta)
    boolean ativo() {
        return somaBotoes != 0 || Math.hypot(somaLx, somaLy) > 0.2 || Math.hypot(somaRx, somaRy) > 0.2;
    }

    static boolean daFonte(InputDevice d) {
        if (d == null) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    // ---------------------------------------------------------------- eventos
    // KeyEvent de controle: true se virou botão do Vita (o evento é consumido); false segue para o resto do app
    boolean tecla(KeyEvent e) {
        int acao = e.getAction();
        if (acao != KeyEvent.ACTION_DOWN && acao != KeyEvent.ACTION_UP) return false;
        int k = e.getKeyCode();
        InputDevice dev = e.getDevice();
        if (!daFonte(dev) && !KeyEvent.isGamepadButton(k)) return false; // teclado e voltar do sistema não entram
        int i = teclasUsuario.indexOfKey(k);
        int bit = i >= 0 ? teclasUsuario.valueAt(i) : botaoPadrao(k);
        if (bit == 0) {
            if (!teclasAvisadas.get(k)) {
                teclasAvisadas.put(k, true);
                Log.w(TAG, "tecla de controle sem mapa: " + KeyEvent.keyCodeToString(k) + " (" + k + ") scancode="
                        + e.getScanCode() + " dispositivo=" + (dev != null ? dev.getName() : "?")
                        + " -> ajuste em padmap.txt: key " + k + " = CROSS");
            }
            return false;
        }
        Dispositivo d = obter(e.getDeviceId(), dev);
        d.teclas = acao == KeyEvent.ACTION_DOWN ? d.teclas | bit : d.teclas & ~bit;
        somar();
        return true;
    }

    // MotionEvent (ACTION_MOVE) de joystick: true se o estado do controle mudou
    boolean movimento(MotionEvent e) {
        Dispositivo d = obter(e.getDeviceId(), e.getDevice());
        boolean mudou = d.atualizar(e);
        if (mudou) somar();
        return mudou;
    }

    // perdeu o foco, o app foi para segundo plano ou o controle saiu: nada fica "preso"
    void limpar() {
        for (int i = 0; i < dispositivos.size(); i++) dispositivos.valueAt(i).zerar(); // o perfil de eixos fica
        somar();
    }

    void remover(int id) {
        dispositivos.remove(id);
        somar();
    }

    private Dispositivo obter(int id, InputDevice dev) {
        Dispositivo d = dispositivos.get(id);
        if (d == null) {
            d = new Dispositivo(id, dev);
            dispositivos.put(id, d);
        }
        return d;
    }

    private void somar() {
        int b = 0;
        float lx = 0, ly = 0, rx = 0, ry = 0;
        for (int i = 0; i < dispositivos.size(); i++) {
            Dispositivo d = dispositivos.valueAt(i);
            b |= d.botoes();
            if (Math.hypot(d.sLx, d.sLy) > Math.hypot(lx, ly)) { lx = d.sLx; ly = d.sLy; }
            if (Math.hypot(d.sRx, d.sRy) > Math.hypot(rx, ry)) { rx = d.sRx; ry = d.sRy; }
        }
        somaBotoes = b;
        somaLx = lx; somaLy = ly; somaRx = rx; somaRy = ry;
    }

    // ---------------------------------------------------------------- botões
    private static int botaoPadrao(int k) {
        switch (k) {
            // layout padrão do Android (Xbox/Sony com keylayout): A embaixo, B à direita, X à esquerda, Y em cima
            case KeyEvent.KEYCODE_BUTTON_A: return ControlesView.CRUZ;
            case KeyEvent.KEYCODE_BUTTON_B: return ControlesView.CIRCULO;
            case KeyEvent.KEYCODE_BUTTON_X: return ControlesView.QUADRADO;
            case KeyEvent.KEYCODE_BUTTON_Y: return ControlesView.TRIANGULO;
            case KeyEvent.KEYCODE_BUTTON_L1: return ControlesView.L1;
            case KeyEvent.KEYCODE_BUTTON_R1: return ControlesView.R1;
            case KeyEvent.KEYCODE_BUTTON_L2: return ControlesView.L2;
            case KeyEvent.KEYCODE_BUTTON_R2: return ControlesView.R2;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return ControlesView.L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return ControlesView.R3;
            case KeyEvent.KEYCODE_BUTTON_START: return ControlesView.START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return ControlesView.SELECT;
            case KeyEvent.KEYCODE_BACK: return ControlesView.SELECT; // controle sem SELECT: o "voltar" dele
            case KeyEvent.KEYCODE_DPAD_UP: return ControlesView.CIMA;
            case KeyEvent.KEYCODE_DPAD_DOWN: return ControlesView.BAIXO;
            case KeyEvent.KEYCODE_DPAD_LEFT: return ControlesView.ESQUERDA;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return ControlesView.DIREITA;
            default:
                // Joystick HID genérico: KEYCODE_BUTTON_1..16 na ordem do relatório HID do PS3 (Select, L3, R3, Start,
                // cima, direita, baixo, esquerda, L2, R2, L1, R1, triângulo, círculo, cruz, quadrado), que é a ordem
                // dos bits do SceCtrlData: botão n = 1 << (n - 1). É uma suposição para esse tipo de controle; se o
                // seu vier diferente, padmap.txt troca cada um.
                if (k >= KeyEvent.KEYCODE_BUTTON_1 && k <= KeyEvent.KEYCODE_BUTTON_16)
                    return 1 << (k - KeyEvent.KEYCODE_BUTTON_1);
                return 0;
        }
    }

    private static int bitPorNome(String n) {
        switch (n) {
            case "SELECT": return ControlesView.SELECT;
            case "L3": return ControlesView.L3;
            case "R3": return ControlesView.R3;
            case "START": return ControlesView.START;
            case "UP": return ControlesView.CIMA;
            case "RIGHT": return ControlesView.DIREITA;
            case "DOWN": return ControlesView.BAIXO;
            case "LEFT": return ControlesView.ESQUERDA;
            case "L2": return ControlesView.L2;
            case "R2": return ControlesView.R2;
            case "L1": return ControlesView.L1;
            case "R1": return ControlesView.R1;
            case "TRIANGLE": return ControlesView.TRIANGULO;
            case "CIRCLE": return ControlesView.CIRCULO;
            case "CROSS": return ControlesView.CRUZ;
            case "SQUARE": return ControlesView.QUADRADO;
            case "NONE": return 0;
            default: return -1;
        }
    }

    // ---------------------------------------------------------------- padmap.txt (opcional)
    // Uma linha por regra, '#' começa comentário:
    //   key 188 = SELECT        código de tecla (número ou KEYCODE_...) -> SELECT L3 R3 START UP RIGHT DOWN LEFT L2 R2 L1 R1
    //   key BUTTON_13 = CROSS      TRIANGLE CIRCLE CROSS SQUARE, ou NONE para ignorar
    //   lstick = X,Y            analógico esquerdo / direito: dois eixos (AXIS_X, Z, RZ, RX, RY, GENERIC_1...)
    //   rstick = RX,RY
    //   l2 = RX                 gatilhos analógicos: um eixo cada (ou NONE)
    //   r2 = RY
    void carregarMapa(File f) {
        if (f == null || !f.isFile()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                int h = l.indexOf('#');
                if (h >= 0) l = l.substring(0, h);
                l = l.trim();
                int eq = l.indexOf('=');
                if (eq <= 0) continue;
                String esq = l.substring(0, eq).trim().toLowerCase(Locale.ROOT), dir = l.substring(eq + 1).trim().toUpperCase(Locale.ROOT);
                if (!aplicarRegra(esq, dir)) Log.w(TAG, "padmap.txt: regra ignorada: " + l);
            }
        } catch (Exception e) {
            Log.w(TAG, "padmap.txt ilegível: " + e);
        }
    }

    private boolean aplicarRegra(String esq, String dir) {
        if (esq.startsWith("key")) {
            int k = codigoTecla(esq.substring(3).trim());
            int bit = bitPorNome(dir);
            if (k <= 0 || bit < 0) return false;
            teclasUsuario.put(k, bit);
            Log.i(TAG, "padmap.txt: tecla " + k + " -> " + dir);
            return true;
        }
        switch (esq) {
            case "lstick": case "rstick": {
                String[] p = dir.split("\\s*,\\s*");
                if (p.length != 2) return false;
                int a = nomeEixo(p[0]), b = nomeEixo(p[1]);
                if (a == AUTO || b == AUTO) return false;
                if (esq.equals("lstick")) { uLx = a; uLy = b; } else { uRx = a; uRy = b; }
                return true;
            }
            case "l2": case "r2": {
                int a = nomeEixo(dir);
                if (a == AUTO) return false;
                if (esq.equals("l2")) uL2 = a; else uR2 = a;
                return true;
            }
            default: return false;
        }
    }

    private static int codigoTecla(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignorada) {
            String n = s.toUpperCase(Locale.ROOT);
            return KeyEvent.keyCodeFromString(n.startsWith("KEYCODE_") ? n : "KEYCODE_" + n);
        }
    }

    // NENHUM = "none"; AUTO = nome inválido
    private static int nomeEixo(String s) {
        if (s.equals("NONE")) return NENHUM;
        int a = MotionEvent.axisFromString(s.startsWith("AXIS_") ? s : "AXIS_" + s);
        return a >= 0 ? a : AUTO;
    }

    // ---------------------------------------------------------------- um eixo, com a faixa que o aparelho informa
    private static final class Eixo {
        final int id;
        final float min, max, flat;

        Eixo(int id, float min, float max, float flat) { this.id = id; this.min = min; this.max = max; this.flat = flat; }

        // -1..1 em torno do centro da faixa (analógico)
        float centrado(MotionEvent e) {
            float metade = (max - min) / 2f;
            if (metade <= 0f) return 0f;
            return Math.max(-1f, Math.min(1f, (e.getAxisValue(id) - (max + min) / 2f) / metade));
        }

        // 0..1 do mínimo ao máximo da faixa (gatilho: repouso no mínimo, seja ele 0 ou -1)
        float gatilho(MotionEvent e) {
            float t = max - min;
            if (t <= 0f) return 0f;
            return Math.max(0f, Math.min(1f, (e.getAxisValue(id) - min) / t));
        }

        float zonaMorta() {
            float metade = (max - min) / 2f;
            return Math.max(ZONA_MIN, Math.min(ZONA_MAX, metade > 0f ? flat / metade : 0f));
        }

        // repouso encostado no mínimo da faixa: é gatilho, não analógico (analógico repousa no meio)
        boolean repousaNoMinimo(MotionEvent e) { return gatilho(e) < 0.1f; }
    }

    private static Eixo eixo(InputDevice d, int id) {
        InputDevice.MotionRange r = d.getMotionRange(id, InputDevice.SOURCE_JOYSTICK);
        return r == null ? null : new Eixo(id, r.getMin(), r.getMax(), r.getFlat());
    }

    private static Eixo primeiro(Eixo a, Eixo b) { return a != null ? a : b; }

    // analógico com zona morta radial: não achata as diagonais como a por eixo
    private static float radial(float x, float y, float zona, boolean doY) {
        float m = (float) Math.hypot(x, y);
        if (m <= zona) return 0f;
        return (doY ? y : x) * (Math.min(m, 1f) - zona) / ((1f - zona) * m);
    }

    // ---------------------------------------------------------------- um dispositivo
    private final class Dispositivo {
        final int id;
        final String nome;
        Eixo lx, ly, rx, ry, l2, r2;           // o que este controle usa
        Eixo z, rz, rxa, rya;                  // candidatos ambíguos: analógico direito ou gatilhos
        boolean hat, calibrar;
        int teclas, hatBits;
        boolean g2l, g2r;
        float sLx, sLy, sRx, sRy;

        Dispositivo(int id, InputDevice d) {
            this.id = id;
            nome = d != null ? d.getName() : "?";
            if (d == null) return;
            descrever(d);
            lx = eixo(d, MotionEvent.AXIS_X);
            ly = eixo(d, MotionEvent.AXIS_Y);
            if (lx == null || ly == null) lx = ly = null;
            z = eixo(d, MotionEvent.AXIS_Z);
            rz = eixo(d, MotionEvent.AXIS_RZ);
            rxa = eixo(d, MotionEvent.AXIS_RX);
            rya = eixo(d, MotionEvent.AXIS_RY);
            // gatilhos que o Android já declara como tal
            l2 = primeiro(eixo(d, MotionEvent.AXIS_LTRIGGER), eixo(d, MotionEvent.AXIS_BRAKE));
            r2 = primeiro(eixo(d, MotionEvent.AXIS_RTRIGGER), eixo(d, MotionEvent.AXIS_GAS));
            if (z != null && rz != null) { rx = z; ry = rz; }
            else if (rxa != null && rya != null) { rx = rxa; ry = rya; }
            // sem gatilho declarado, Z/RZ/RX/RY podem ser analógico direito ou L2/R2: decide no primeiro evento
            calibrar = l2 == null && r2 == null && (z != null && rz != null || rxa != null && rya != null);
            hat = eixo(d, MotionEvent.AXIS_HAT_X) != null && eixo(d, MotionEvent.AXIS_HAT_Y) != null;
            // padmap.txt manda
            if (uLx != AUTO || uLy != AUTO) { lx = escolhido(d, uLx, lx); ly = escolhido(d, uLy, ly); }
            if (uRx != AUTO || uRy != AUTO) { rx = escolhido(d, uRx, rx); ry = escolhido(d, uRy, ry); }
            if (uL2 != AUTO) l2 = escolhido(d, uL2, l2);
            if (uR2 != AUTO) r2 = escolhido(d, uR2, r2);
            if (uRx != AUTO || uRy != AUTO || uL2 != AUTO || uR2 != AUTO) calibrar = false;
            if (!calibrar) resumo("layout");
        }

        private Eixo escolhido(InputDevice d, int pedido, Eixo padrao) {
            return pedido == AUTO ? padrao : pedido == NENHUM ? null : eixo(d, pedido);
        }

        // primeiro evento: gatilho repousa no mínimo da faixa, analógico no meio
        private void calibrarCom(MotionEvent e) {
            calibrar = false;
            boolean parZ = z != null && rz != null, parR = rxa != null && rya != null;
            boolean zGat = parZ && z.repousaNoMinimo(e) && rz.repousaNoMinimo(e);
            boolean rGat = parR && rxa.repousaNoMinimo(e) && rya.repousaNoMinimo(e);
            rx = ry = null;
            if (parZ && !zGat) { rx = z; ry = rz; }
            else if (parR && !rGat) { rx = rxa; ry = rya; }
            if (zGat) { l2 = z; r2 = rz; }
            else if (rGat) { l2 = rxa; r2 = rya; }
            resumo("layout detectado");
        }

        void zerar() {
            teclas = hatBits = 0;
            g2l = g2r = false;
            sLx = sLy = sRx = sRy = 0f;
        }

        int botoes() {
            return teclas | hatBits | (g2l ? ControlesView.L2 : 0) | (g2r ? ControlesView.R2 : 0);
        }

        boolean atualizar(MotionEvent e) {
            if (calibrar) calibrarCom(e);
            float a = 0, b = 0, c = 0, f = 0;
            if (lx != null && ly != null) {
                float x = lx.centrado(e), y = ly.centrado(e), z0 = Math.max(lx.zonaMorta(), ly.zonaMorta());
                a = radial(x, y, z0, false);
                b = radial(x, y, z0, true);
            }
            if (rx != null && ry != null) {
                float x = rx.centrado(e), y = ry.centrado(e), z0 = Math.max(rx.zonaMorta(), ry.zonaMorta());
                c = radial(x, y, z0, false);
                f = radial(x, y, z0, true);
            }
            boolean nl2 = l2 != null && l2.gatilho(e) > (g2l ? GATILHO_DESLIGA : GATILHO_LIGA);
            boolean nr2 = r2 != null && r2.gatilho(e) > (g2r ? GATILHO_DESLIGA : GATILHO_LIGA);
            int h = 0;
            if (hat) {
                float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
                h = (hx < -0.5f ? ControlesView.ESQUERDA : 0) | (hx > 0.5f ? ControlesView.DIREITA : 0)
                        | (hy < -0.5f ? ControlesView.CIMA : 0) | (hy > 0.5f ? ControlesView.BAIXO : 0);
            }
            boolean mudou = a != sLx || b != sLy || c != sRx || f != sRy || nl2 != g2l || nr2 != g2r || h != hatBits;
            sLx = a; sLy = b; sRx = c; sRy = f; g2l = nl2; g2r = nr2; hatBits = h;
            return mudou;
        }

        // logcat: o que o aparelho entrega (para diagnosticar um controle que venha com outro layout)
        private void descrever(InputDevice d) {
            Log.i(TAG, "controle #" + id + " \"" + nome + "\" vendor=0x" + Integer.toHexString(d.getVendorId()) + " product=0x"
                    + Integer.toHexString(d.getProductId()) + " fontes=0x" + Integer.toHexString(d.getSources()));
            List<InputDevice.MotionRange> rs = d.getMotionRanges();
            for (InputDevice.MotionRange r : rs)
                Log.i(TAG, "  eixo " + MotionEvent.axisToString(r.getAxis()) + " fonte=0x" + Integer.toHexString(r.getSource())
                        + " min=" + r.getMin() + " max=" + r.getMax() + " flat=" + r.getFlat());
        }

        private void resumo(String titulo) {
            Log.i(TAG, "  " + titulo + ": esq=" + nm(lx) + "," + nm(ly) + " dir=" + nm(rx) + "," + nm(ry) + " L2=" + nm(l2)
                    + " R2=" + nm(r2) + " hat=" + hat);
        }

        private String nm(Eixo e) { return e == null ? "-" : MotionEvent.axisToString(e.id); }
    }
}
