import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codificação de banda base e modulação digital.
 *
 * Parte 1: NRZ unipolar, NRZ polar e Manchester.
 * Parte 2: ASK, FSK e BPSK.
 *
 * Uso:
 *   java CodificacaoModulacao.java                      -> usa a sequência padrão de 16 bits
 *   java CodificacaoModulacao.java 1011001110001101     -> usa a sequência informada (mínimo 16 bits)
 *
 * Os gráficos são gravados como PNG na pasta "graficos".
 */
public class CodificacaoModulacao {

    static final Locale PT = Locale.of("pt", "BR");
    static final String PASTA_GRAFICOS = "graficos";

    // =====================================================================
    // PARÂMETROS
    // =====================================================================

    static final String SEQUENCIA_PADRAO = "1011001110001101"; // 16 bits
    static final double TAXA_BITS = 1000.0;   // Rb [bit/s]  ->  Tb = 1 ms
    static final double AMPLITUDE = 1.0;      // A [V]
    static final double FS = 100_000.0;       // frequência de amostragem [Hz]  ->  100 amostras/bit
    static final double FC = 4000.0;          // portadora do ASK e do BPSK; centro do FSK [Hz]
    static final double DELTA_F = 1000.0;     // desvio de frequência do FSK [Hz]
    static final double F0 = FC - DELTA_F;    // FSK: frequência do bit 0 [Hz]
    static final double F1 = FC + DELTA_F;    // FSK: frequência do bit 1 [Hz]

    // =====================================================================
    // Helpers
    // =====================================================================

    static String f(String formato, Object... args) {
        return String.format(PT, formato, args);
    }

    /** Número sem casas decimais se for inteiro; caso contrário, 2 casas. */
    static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? f("%.0f", v) : f("%.2f", v);
    }

    /** Nível de tensão com sinal e unidade: +1 V, 0 V, -1 V. */
    static String volts(double v) {
        if (v == 0) return "0 V";
        return (v > 0 ? "+" : "-") + num(Math.abs(v)) + " V";
    }

    static void titulo(String texto) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(texto);
        System.out.println("=".repeat(78));
    }

    // =====================================================================
    // MAIN
    // =====================================================================

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        String seq = args.length > 0 ? args[0].trim() : SEQUENCIA_PADRAO;
        if (!seq.matches("[01]{16,}")) {
            System.out.println("Informe uma sequência binária (somente 0 e 1) com no mínimo 16 bits.");
            System.out.println("Uso: java CodificacaoModulacao.java [sequencia_binaria]");
            return;
        }
        int[] bits = new int[seq.length()];
        for (int i = 0; i < bits.length; i++) bits[i] = seq.charAt(i) - '0';

        int spb = (int) Math.round(FS / TAXA_BITS); // amostras por bit
        if (spb % 2 != 0) {
            System.out.println("fs / Rb deve ser um número par de amostras por bit (necessário para o Manchester).");
            return;
        }

        new File(PASTA_GRAFICOS).mkdirs();
        parte1(bits, seq, spb);
        parte2(bits, seq, spb);
        System.out.println();
        System.out.println("Gráficos gravados na pasta '" + PASTA_GRAFICOS + "/'.");
    }

    // =====================================================================
    // PARTE 1 - Codificação de banda base
    // =====================================================================

    static void parte1(int[] bits, String seq, int spb) throws IOException {
        titulo("PARTE 1 - Codificação de banda base");
        System.out.println("Sequência binária (" + bits.length + " bits): " + seq);
        System.out.println(f("Taxa de bits: %,.0f bit/s (Tb = %s ms) | Amplitude A = %s | fs = %,.0f Hz (%d amostras/bit)",
                TAXA_BITS, num(1000.0 / TAXA_BITS), num(AMPLITUDE) + " V", FS, spb));
        System.out.println();
        System.out.println("Convenção adotada:");
        System.out.println("  NRZ unipolar : bit 1 = " + volts(AMPLITUDE) + " | bit 0 = " + volts(0));
        System.out.println("  NRZ polar    : bit 1 = " + volts(AMPLITUDE) + " | bit 0 = " + volts(-AMPLITUDE));
        System.out.println("  Manchester   : transição sempre no meio do bit");
        System.out.println("                 bit 1 = " + volts(-AMPLITUDE) + " na 1ª metade e " + volts(AMPLITUDE)
                + " na 2ª metade (transição de SUBIDA)");
        System.out.println("                 bit 0 = " + volts(AMPLITUDE) + " na 1ª metade e " + volts(-AMPLITUDE)
                + " na 2ª metade (transição de DESCIDA)");

        System.out.println();
        System.out.println(f("%-5s %-5s %-16s %-14s %-24s", "n", "bit", "NRZ unipolar", "NRZ polar", "Manchester (1ª / 2ª metade)"));
        System.out.println("-".repeat(70));
        for (int i = 0; i < bits.length; i++) {
            boolean um = bits[i] == 1;
            String manchester = um ? volts(-AMPLITUDE) + " / " + volts(AMPLITUDE)
                    : volts(AMPLITUDE) + " / " + volts(-AMPLITUDE);
            System.out.println(f("%-5d %-5d %-16s %-14s %-24s", i + 1, bits[i],
                    volts(um ? AMPLITUDE : 0), volts(um ? AMPLITUDE : -AMPLITUDE), manchester));
        }

        // Sinais amostrados
        double[] unipolar = nrzUnipolar(bits, spb);
        double[] polar = nrzPolar(bits, spb);
        double[] manchester = manchester(bits, spb);

        List<Painel> paineis = new ArrayList<>();
        paineis.add(new Painel(
                "NRZ unipolar:  bit 1 = " + volts(AMPLITUDE) + "  |  bit 0 = " + volts(0),
                unipolar, -0.5 * AMPLITUDE, 1.6 * AMPLITUDE,
                new double[]{0, AMPLITUDE}, new String[]{volts(0), volts(AMPLITUDE)},
                new Color(31, 119, 180), false));
        paineis.add(new Painel(
                "NRZ polar:  bit 1 = " + volts(AMPLITUDE) + "  |  bit 0 = " + volts(-AMPLITUDE),
                polar, -1.6 * AMPLITUDE, 1.6 * AMPLITUDE,
                new double[]{-AMPLITUDE, 0, AMPLITUDE}, new String[]{volts(-AMPLITUDE), volts(0), volts(AMPLITUDE)},
                new Color(255, 127, 14), false));
        paineis.add(new Painel(
                "Manchester:  bit 1 = transição de subida no meio do bit (" + volts(-AMPLITUDE) + " para " + volts(AMPLITUDE)
                        + ")  |  bit 0 = transição de descida (" + volts(AMPLITUDE) + " para " + volts(-AMPLITUDE) + ")",
                manchester, -1.6 * AMPLITUDE, 1.6 * AMPLITUDE,
                new double[]{-AMPLITUDE, 0, AMPLITUDE}, new String[]{volts(-AMPLITUDE), volts(0), volts(AMPLITUDE)},
                new Color(44, 160, 44), true));

        List<String> sub = new ArrayList<>();
        sub.add("Sequência: " + seq + "  (" + bits.length + " bits)  |  Rb = " + f("%,.0f", TAXA_BITS) + " bit/s  (Tb = "
                + num(1000.0 / TAXA_BITS) + " ms)  |  A = " + num(AMPLITUDE) + " V");
        sub.add("Linhas tracejadas = limites dos intervalos de bit  |  Linhas pontilhadas (Manchester) = meio do bit");
        desenharPaineis("parte1_codigos_de_linha.png", "Parte 1 - Códigos de linha (banda base)", sub, bits, spb, paineis);
    }

    /** NRZ unipolar: 1 = +A, 0 = 0. */
    static double[] nrzUnipolar(int[] bits, int spb) {
        int n = bits.length;
        double[] s = new double[n * spb + 1];
        for (int k = 0; k < s.length; k++) s[k] = bits[indiceBit(k, spb, n)] == 1 ? AMPLITUDE : 0.0;
        return s;
    }

    /** NRZ polar: 1 = +A, 0 = -A. */
    static double[] nrzPolar(int[] bits, int spb) {
        int n = bits.length;
        double[] s = new double[n * spb + 1];
        for (int k = 0; k < s.length; k++) s[k] = bits[indiceBit(k, spb, n)] == 1 ? AMPLITUDE : -AMPLITUDE;
        return s;
    }

    /** Manchester: bit 1 = -A depois +A (subida); bit 0 = +A depois -A (descida). */
    static double[] manchester(int[] bits, int spb) {
        int n = bits.length;
        double[] s = new double[n * spb + 1];
        for (int k = 0; k < s.length; k++) {
            int i = indiceBit(k, spb, n);
            boolean segundaMetade = (k - i * spb) >= spb / 2;
            boolean alto = bits[i] == 1 ? segundaMetade : !segundaMetade;
            s[k] = alto ? AMPLITUDE : -AMPLITUDE;
        }
        return s;
    }

    /** Índice do bit a que a amostra k pertence (a última amostra fica no último bit). */
    static int indiceBit(int k, int spb, int n) {
        return Math.min(k / spb, n - 1);
    }

    // =====================================================================
    // PARTE 2 - Modulação digital
    // =====================================================================

    static void parte2(int[] bits, String seq, int spb) throws IOException {
        titulo("PARTE 2 - Modulação digital (ASK, FSK e BPSK)");
        System.out.println("Sequência binária (" + bits.length + " bits): " + seq);
        System.out.println();
        System.out.println("Parâmetros utilizados:");
        System.out.println(f("  Taxa de bits (Rb)               : %,.0f bit/s  (Tb = %s ms)", TAXA_BITS, num(1000.0 / TAXA_BITS)));
        System.out.println(f("  Amplitude da portadora (A)      : %s", (num(AMPLITUDE) + " V")));
        System.out.println(f("  Frequência de amostragem (fs)   : %,.0f Hz  (%d amostras/bit)", FS, spb));
        System.out.println(f("  ASK  - portadora fc             : %,.0f Hz  (%s ciclos por bit)", FC, num(FC / TAXA_BITS)));
        System.out.println(f("  FSK  - f0 (bit 0) / f1 (bit 1)  : %,.0f Hz / %,.0f Hz  (%s e %s ciclos por bit; fc = %,.0f Hz, Δf = %,.0f Hz)",
                F0, F1, num(F0 / TAXA_BITS), num(F1 / TAXA_BITS), FC, DELTA_F));
        System.out.println(f("  BPSK - portadora fc             : %,.0f Hz  (%s ciclos por bit)", FC, num(FC / TAXA_BITS)));
        System.out.println();
        System.out.println("Mapeamento adotado:");
        System.out.println("  ASK  : bit 1 = amplitude A (" + (num(AMPLITUDE) + " V") + ") | bit 0 = amplitude 0 (frequência e fase fixas)");
        System.out.println(f("  FSK  : bit 1 = frequência f1 = %,.0f Hz | bit 0 = frequência f0 = %,.0f Hz (amplitude constante, fase contínua)", F1, F0));
        System.out.println("  BPSK : bit 1 = fase 0° | bit 0 = fase 180° (amplitude e frequência constantes)");
        System.out.println("  Forma de onda: s(t) = A cos(2π fc t + φ), com cosseno em todas as técnicas");

        double[] ask = ask(bits, spb);
        double[] fsk = fsk(bits, spb);
        double[] bpsk = bpsk(bits, spb);

        // Sequência binária (nível lógico 0/1) para referência no topo do gráfico
        int n = bits.length;
        double[] logico = new double[n * spb + 1];
        for (int k = 0; k < logico.length; k++) logico[k] = bits[indiceBit(k, spb, n)];

        double a = AMPLITUDE;
        double[] ticksSenoide = {-a, 0, a};
        String[] rotulosSenoide = {volts(-a), volts(0), volts(a)};

        List<Painel> paineis = new ArrayList<>();
        paineis.add(new Painel("Sequência binária transmitida",
                logico, -0.5, 1.6, new double[]{0, 1}, new String[]{"0", "1"}, new Color(90, 90, 90), false));
        paineis.add(new Painel(
                f("ASK:  bit 1 = amplitude A = %s  |  bit 0 = amplitude 0  |  fc = %,.0f Hz", num(a) + " V", FC),
                ask, -1.6 * a, 1.6 * a, ticksSenoide, rotulosSenoide, new Color(31, 119, 180), false));
        paineis.add(new Painel(
                f("FSK:  bit 1 = frequência f1 = %,.0f Hz  |  bit 0 = frequência f0 = %,.0f Hz  |  A = %s", F1, F0, num(a) + " V"),
                fsk, -1.6 * a, 1.6 * a, ticksSenoide, rotulosSenoide, new Color(255, 127, 14), false));
        paineis.add(new Painel(
                f("BPSK:  bit 1 = fase 0°  |  bit 0 = fase 180°  |  fc = %,.0f Hz, A = %s", FC, num(a) + " V"),
                bpsk, -1.6 * a, 1.6 * a, ticksSenoide, rotulosSenoide, new Color(44, 160, 44), false));

        List<String> sub = new ArrayList<>();
        sub.add("Sequência: " + seq + "  (" + bits.length + " bits)  |  Rb = " + f("%,.0f", TAXA_BITS) + " bit/s  (Tb = "
                + num(1000.0 / TAXA_BITS) + " ms)  |  A = " + num(a) + " V  |  fs = " + f("%,.0f", FS) + " Hz");
        sub.add(f("Portadora: fc = %,.0f Hz (ASK e BPSK)  |  FSK: f0 = %,.0f Hz e f1 = %,.0f Hz  |  Linhas tracejadas = limites dos intervalos de bit",
                FC, F0, F1));
        desenharPaineis("parte2_modulacoes_digitais.png", "Parte 2 - Modulações digitais (ASK, FSK e BPSK)", sub, bits, spb, paineis);
    }

    /** ASK (OOK): bit 1 = A cos(2π fc t); bit 0 = 0. */
    static double[] ask(int[] bits, int spb) {
        int n = bits.length;
        double[] s = new double[n * spb + 1];
        for (int k = 0; k < s.length; k++) {
            double amp = bits[indiceBit(k, spb, n)] == 1 ? AMPLITUDE : 0.0;
            s[k] = amp * Math.cos(2 * Math.PI * FC * k / FS);
        }
        return s;
    }

    /** FSK de fase contínua: bit 1 = frequência F1; bit 0 = frequência F0. */
    static double[] fsk(int[] bits, int spb) {
        int n = bits.length;
        double[] s = new double[n * spb + 1];
        double fase = 0;
        for (int k = 0; k < s.length; k++) {
            double freq = bits[indiceBit(k, spb, n)] == 1 ? F1 : F0;
            s[k] = AMPLITUDE * Math.cos(fase);
            fase += 2 * Math.PI * freq / FS;
        }
        return s;
    }

    /** BPSK: bit 1 = fase 0°; bit 0 = fase 180°. */
    static double[] bpsk(int[] bits, int spb) {
        int n = bits.length;
        double[] s = new double[n * spb + 1];
        for (int k = 0; k < s.length; k++) {
            double fase = bits[indiceBit(k, spb, n)] == 1 ? 0.0 : Math.PI;
            s[k] = AMPLITUDE * Math.cos(2 * Math.PI * FC * k / FS + fase);
        }
        return s;
    }

    // =====================================================================
    // GRÁFICOS (Java2D, sem dependências externas)
    // =====================================================================

    record Painel(String titulo, double[] y, double ymin, double ymax,
                  double[] ticks, String[] rotulos, Color cor, boolean meioBit) {}

    static void desenharPaineis(String arquivo, String titulo, List<String> subtitulos,
                                int[] bits, int spb, List<Painel> paineis) throws IOException {
        final int W = 1400, ML = 100, MR = 40, PH = 150, GAP = 48, MB = 75;
        int MT = 75 + 22 * subtitulos.size();
        int np = paineis.size();
        int H = MT + np * PH + (np - 1) * GAP + MB;
        int pw = W - ML - MR;
        int n = bits.length;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        // Título e subtítulos
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        g.drawString(titulo, (W - g.getFontMetrics().stringWidth(titulo)) / 2, 34);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < subtitulos.size(); i++) {
            String s = subtitulos.get(i);
            g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, 62 + i * 22);
        }

        BasicStroke tracejado = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 5f}, 0f);
        BasicStroke pontilhado = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 4f}, 0f);

        for (int pi = 0; pi < np; pi++) {
            Painel p = paineis.get(pi);
            int top = MT + pi * (PH + GAP);

            // Título do painel
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.setColor(p.cor().darker());
            g.drawString(p.titulo(), ML, top - 12);

            // Grade horizontal e rótulos do eixo Y
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            FontMetrics fm = g.getFontMetrics();
            g.setStroke(new BasicStroke(1f));
            for (int j = 0; j < p.ticks().length; j++) {
                int y = pixY(p.ticks()[j], p, top, PH);
                g.setColor(p.ticks()[j] == 0 ? new Color(200, 200, 200) : new Color(228, 228, 228));
                g.drawLine(ML, y, ML + pw, y);
                g.setColor(Color.DARK_GRAY);
                g.drawString(p.rotulos()[j], ML - 8 - fm.stringWidth(p.rotulos()[j]), y + 5);
            }

            // Limites dos intervalos de bit (tracejado) e meio do bit (pontilhado)
            g.setStroke(tracejado);
            g.setColor(new Color(140, 140, 140));
            for (int k = 0; k <= n; k++) {
                int x = ML + (int) Math.round((double) k * pw / n);
                g.drawLine(x, top, x, top + PH);
            }
            if (p.meioBit()) {
                g.setStroke(pontilhado);
                g.setColor(new Color(170, 170, 170));
                for (int k = 0; k < n; k++) {
                    int x = ML + (int) Math.round((k + 0.5) * pw / n);
                    g.drawLine(x, top, x, top + PH);
                }
            }

            // Rótulos dos bits no centro de cada intervalo
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            fm = g.getFontMetrics();
            g.setColor(new Color(90, 90, 90));
            for (int k = 0; k < n; k++) {
                String r = String.valueOf(bits[k]);
                int xc = ML + (int) Math.round((k + 0.5) * pw / n);
                g.drawString(r, xc - fm.stringWidth(r) / 2, top + 17);
            }

            // Sinal
            double[] y = p.y();
            Path2D.Double path = new Path2D.Double();
            for (int i = 0; i < y.length; i++) {
                double x = ML + (double) i / (y.length - 1) * pw;
                double yy = pixYd(y[i], p, top, PH);
                if (i == 0) path.moveTo(x, yy);
                else path.lineTo(x, yy);
            }
            g.setClip(ML, top, pw + 1, PH + 1);
            g.setColor(p.cor());
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            g.setClip(null);

            // Moldura
            g.setColor(new Color(120, 120, 120));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRect(ML, top, pw, PH);
        }

        // Eixo do tempo (abaixo do último painel)
        int base = MT + (np - 1) * (PH + GAP) + PH;
        int passo = (int) Math.ceil(n / 20.0);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(1f));
        for (int k = 0; k <= n; k += passo) {
            int x = ML + (int) Math.round((double) k * pw / n);
            g.drawLine(x, base, x, base + 6);
            String r = num(k * 1000.0 / TAXA_BITS);
            g.drawString(r, x - fm.stringWidth(r) / 2, base + 22);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        String rotuloX = "Tempo (ms)";
        g.drawString(rotuloX, ML + (pw - g.getFontMetrics().stringWidth(rotuloX)) / 2, base + 52);

        g.dispose();
        ImageIO.write(img, "png", new File(PASTA_GRAFICOS, arquivo));
        System.out.println("[gráfico] " + PASTA_GRAFICOS + "/" + arquivo);
    }

    static double pixYd(double v, Painel p, int top, int ph) {
        return top + ph - (v - p.ymin()) / (p.ymax() - p.ymin()) * ph;
    }

    static int pixY(double v, Painel p, int top, int ph) {
        return (int) Math.round(pixYd(v, p, top, ph));
    }
}