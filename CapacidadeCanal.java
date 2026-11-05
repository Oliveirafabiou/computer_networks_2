import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Capacidade de canais de comunicação: modelos de Nyquist e Shannon.
 *
 * Uso:
 *   java CapacidadeCanal.java                      -> roda os 4 experimentos (tabelas + gráficos + análises)
 *   java CapacidadeCanal.java nyquist 5000 8       -> Nyquist com B = 5000 Hz e N = 8 níveis
 *   java CapacidadeCanal.java shannon 5000 1000    -> Shannon com B = 5000 Hz e SNR linear = 1000
 *   java CapacidadeCanal.java shannon 5000 30dB    -> Shannon com B = 5000 Hz e SNR = 30 dB
 *
 * Os gráficos são gravados como PNG na pasta "graficos".
 */
public class CapacidadeCanal {

    static final Locale PT = Locale.of("pt", "BR");
    static final String PASTA_GRAFICOS = "graficos";

    // =====================================================================
    // 1. MODELOS
    // =====================================================================

    static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    /** Nyquist (canal sem ruído): C = 2 B log2(N), em bit/s. */
    static double nyquist(double bHz, int niveis) {
        if (bHz <= 0) throw new IllegalArgumentException("B deve ser > 0 Hz");
        if (niveis < 2) throw new IllegalArgumentException("N deve ser >= 2 níveis");
        return 2 * bHz * log2(niveis);
    }

    /** Shannon (canal com ruído): C = B log2(1 + SNR), em bit/s, com SNR em escala linear. */
    static double shannon(double bHz, double snrLinear) {
        if (bHz <= 0) throw new IllegalArgumentException("B deve ser > 0 Hz");
        if (snrLinear < 0) throw new IllegalArgumentException("SNR linear deve ser >= 0");
        return bHz * log2(1 + snrLinear);
    }

    /** SNR (dB) -> escala linear: SNR = 10^(SNR_dB / 10). */
    static double dbParaLinear(double snrDb) {
        return Math.pow(10, snrDb / 10.0);
    }

    // =====================================================================
    // Utilitários de formatação
    // =====================================================================

    static String f(String formato, Object... args) {
        return String.format(PT, formato, args);
    }

    /** Formata uma taxa com unidade: bit/s, kbit/s ou Mbit/s. */
    static String taxa(double bps) {
        if (bps >= 1e6) return f("%.3f Mbit/s", bps / 1e6);
        if (bps >= 1e3) return f("%.2f kbit/s", bps / 1e3);
        return f("%.2f bit/s", bps);
    }

    static void titulo(String texto) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(texto);
        System.out.println("=".repeat(78));
    }

    static void linhaHorizontal(int n) {
        System.out.println("-".repeat(n));
    }

    // =====================================================================
    // MAIN
    // =====================================================================

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        // Garante acentos corretos no terminal, independentemente do locale do sistema
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            new File(PASTA_GRAFICOS).mkdirs();
            experimento1();
            experimento2();
            experimento3();
            experimento4();
            System.out.println();
            System.out.println("Gráficos gravados na pasta '" + PASTA_GRAFICOS + "/'.");
        } else {
            modoLinhaDeComando(args);
        }
    }

    static void modoLinhaDeComando(String[] args) {
        try {
            switch (args[0].toLowerCase()) {
                case "nyquist" -> {
                    double b = lerNumero(args[1]);
                    int n = (int) lerNumero(args[2]);
                    double c = nyquist(b, n);
                    System.out.println(f("Nyquist: B = %,.0f Hz, N = %d níveis (%.2f bit/símbolo)", b, n, log2(n)));
                    System.out.println(f("C = 2 * B * log2(N) = %,.2f bit/s  (%s)", c, taxa(c)));
                }
                case "shannon" -> {
                    double b = lerNumero(args[1]);
                    String s = args[2].toLowerCase();
                    double snr;
                    if (s.endsWith("db")) {
                        double db = lerNumero(s.substring(0, s.length() - 2));
                        snr = dbParaLinear(db);
                        System.out.println(f("Shannon: B = %,.0f Hz, SNR = %.2f dB = %.4f (linear)", b, db, snr));
                    } else {
                        snr = lerNumero(s);
                        System.out.println(f("Shannon: B = %,.0f Hz, SNR = %.4f (linear) = %.2f dB", b, snr, 10 * Math.log10(snr)));
                    }
                    double c = shannon(b, snr);
                    System.out.println(f("C = B * log2(1 + SNR) = %,.2f bit/s  (%s)", c, taxa(c)));
                }
                default -> uso();
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            uso();
        } catch (IllegalArgumentException e) {
            System.out.println("Parâmetro inválido: " + e.getMessage());
        }
    }

    static double lerNumero(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    static void uso() {
        System.out.println("Uso:");
        System.out.println("  java CapacidadeCanal.java                    (roda os 4 experimentos)");
        System.out.println("  java CapacidadeCanal.java nyquist <B_Hz> <N>");
        System.out.println("  java CapacidadeCanal.java shannon <B_Hz> <SNR_linear | SNR_dB com sufixo dB>");
        System.out.println("Exemplos:");
        System.out.println("  java CapacidadeCanal.java nyquist 5000 8");
        System.out.println("  java CapacidadeCanal.java shannon 5000 1000");
        System.out.println("  java CapacidadeCanal.java shannon 5000 30dB");
    }

    // =====================================================================
    // 2. EXPERIMENTO 1 - Capacidade de Nyquist
    // =====================================================================

    static void experimento1() throws IOException {
        titulo("EXPERIMENTO 1 - Capacidade de Nyquist: C = 2 B log2(N)");

        double[] bs = {1_000, 5_000, 10_000};
        int[] ns = {2, 4, 8, 16};

        System.out.println("Capacidade (bit/s):");
        System.out.print(f("%-12s", "B \\ N"));
        for (int n : ns) System.out.print(f("%14s", "N = " + n));
        System.out.println();
        linhaHorizontal(12 + 14 * ns.length);
        for (double b : bs) {
            System.out.print(f("%-12s", f("%,.0f Hz", b)));
            for (int n : ns) System.out.print(f("%,14.0f", nyquist(b, n)));
            System.out.println();
        }

        // Gráfico C x N, uma curva por B
        Color[] cores = {new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44)};
        List<Serie> series = new ArrayList<>();
        for (int i = 0; i < bs.length; i++) {
            double[] x = new double[ns.length];
            double[] y = new double[ns.length];
            for (int j = 0; j < ns.length; j++) {
                x[j] = ns[j];
                y[j] = nyquist(bs[i], ns[j]) / 1000.0;
            }
            series.add(new Serie(f("B = %,.0f kHz", bs[i] / 1000), x, y, cores[i], false, true));
        }
        double[] ticks = {2, 4, 8, 16};
        String[] rotulos = {"2", "4", "8", "16"};
        desenharGrafico("exp1_nyquist_C_vs_N.png", "Experimento 1 - Capacidade de Nyquist (C x N)",
                "Número de níveis de sinalização (N)", "Capacidade (kbit/s)", series, ticks, rotulos);
    }

    // =====================================================================
    // 3. EXPERIMENTO 2 - Capacidade de Shannon
    // =====================================================================

    static void experimento2() throws IOException {
        titulo("EXPERIMENTO 2 - Capacidade de Shannon: C = B log2(1 + SNR)");

        double[] snrs = {1_000, 5_000, 10_000, 50_000};
        double[] bsKHz = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        System.out.println("Capacidade (bit/s):");
        System.out.print(f("%-10s", "B (kHz)"));
        for (double s : snrs) System.out.print(f("%16s", f("SNR = %,.0f", s)));
        System.out.println();
        linhaHorizontal(10 + 16 * snrs.length);

        double maior = Double.NEGATIVE_INFINITY, menor = Double.POSITIVE_INFINITY;
        double bMaior = 0, sMaior = 0, bMenor = 0, sMenor = 0;
        for (double bk : bsKHz) {
            System.out.print(f("%-10.0f", bk));
            for (double s : snrs) {
                double c = shannon(bk * 1000, s);
                System.out.print(f("%,16.0f", c));
                if (c > maior) { maior = c; bMaior = bk; sMaior = s; }
                if (c < menor) { menor = c; bMenor = bk; sMenor = s; }
            }
            System.out.println();
        }

        System.out.println();
        System.out.println(f("Maior capacidade: %,.2f bit/s (%s) com B = %.0f kHz e SNR = %,.0f",
                maior, taxa(maior), bMaior, sMaior));
        System.out.println(f("Menor capacidade: %,.2f bit/s (%s) com B = %.0f kHz e SNR = %,.0f",
                menor, taxa(menor), bMenor, sMenor));

        // Gráfico C x B, uma curva por SNR
        Color[] cores = {new Color(31, 119, 180), new Color(255, 127, 14),
                new Color(44, 160, 44), new Color(214, 39, 40)};
        List<Serie> series = new ArrayList<>();
        for (int i = 0; i < snrs.length; i++) {
            double[] y = new double[bsKHz.length];
            for (int j = 0; j < bsKHz.length; j++) y[j] = shannon(bsKHz[j] * 1000, snrs[i]) / 1000.0;
            series.add(new Serie(f("SNR = %,.0f", snrs[i]), bsKHz.clone(), y, cores[i], false, true));
        }
        String[] rotulos = new String[bsKHz.length];
        for (int i = 0; i < bsKHz.length; i++) rotulos[i] = f("%.0f", bsKHz[i]);
        desenharGrafico("exp2_shannon_C_vs_B.png", "Experimento 2 - Capacidade de Shannon (C x B)",
                "Largura de banda B (kHz)", "Capacidade (kbit/s)", series, bsKHz, rotulos);
    }

    // =====================================================================
    // 4. EXPERIMENTO 3 - Influência do ruído
    // =====================================================================

    static void experimento3() throws IOException {
        titulo("EXPERIMENTO 3 - Influência do ruído (B = 5 kHz, SNR de 0 a 40 dB)");

        double b = 5000;
        int passos = 9; // 0, 5, ..., 40
        double[] db = new double[passos];
        double[] lin = new double[passos];
        double[] c = new double[passos];
        for (int i = 0; i < passos; i++) {
            db[i] = 5.0 * i;
            lin[i] = dbParaLinear(db[i]);
            c[i] = shannon(b, lin[i]);
        }

        System.out.println(f("%-10s %-16s %-16s %-14s %-16s", "SNR (dB)", "SNR (linear)", "C (bit/s)", "C/B (bit/s/Hz)", "Δ C (bit/s)"));
        linhaHorizontal(76);
        for (int i = 0; i < passos; i++) {
            String delta = i == 0 ? "-" : f("%,.0f", c[i] - c[i - 1]);
            System.out.println(f("%-10.0f %-16.4f %-16s %-14.4f %-16s",
                    db[i], lin[i], f("%,.0f", c[i]), c[i] / b, delta));
        }

        List<Serie> series = new ArrayList<>();
        double[] y = new double[passos];
        for (int i = 0; i < passos; i++) y[i] = c[i] / 1000.0;
        series.add(new Serie("Shannon, B = 5 kHz", db.clone(), y, new Color(31, 119, 180), false, true));
        String[] rotulos = new String[passos];
        for (int i = 0; i < passos; i++) rotulos[i] = f("%.0f", db[i]);
        desenharGrafico("exp3_shannon_C_vs_SNRdB.png", "Experimento 3 - Influência do ruído (C x SNR em dB)",
                "SNR (dB)", "Capacidade (kbit/s)", series, db, rotulos);

        System.out.println();
        System.out.println("Comparação em 10, 20, 30 e 40 dB:");
        double[] alvo = {10, 20, 30, 40};
        double anterior = Double.NaN;
        for (double d : alvo) {
            double cc = shannon(b, dbParaLinear(d));
            String extra = Double.isNaN(anterior) ? "" : f("  (+%s em relação a %.0f dB)", taxa(cc - anterior), d - 10);
            System.out.println(f("    %2.0f dB (SNR = %,8.0f) -> %s%s", d, dbParaLinear(d), taxa(cc), extra));
            anterior = cc;
        }
    }

    // =====================================================================
    // 5. EXPERIMENTO 4 - Nyquist versus Shannon
    // =====================================================================

    static void experimento4() throws IOException {
        titulo("EXPERIMENTO 4 - Nyquist versus Shannon (B = 3 kHz, SNR = 30 dB)");

        double b = 3000;
        double snrDb = 30;
        double snr = dbParaLinear(snrDb);
        double limite = shannon(b, snr);
        int[] ns = {2, 4, 8, 16, 32, 64};

        System.out.println(f("SNR = %.0f dB = %,.0f (linear)", snrDb, snr));
        System.out.println(f("Limite de Shannon: C = %,.2f bit/s (%s)", limite, taxa(limite)));
        System.out.println();
        System.out.println(f("%-6s %-14s %-18s %-18s %-14s", "N", "bits/símbolo", "Nyquist (bit/s)", "Shannon (bit/s)", "Nyquist > Shannon?"));
        linhaHorizontal(86);

        List<Integer> excedem = new ArrayList<>();
        double[] xs = new double[ns.length];
        double[] ys = new double[ns.length];
        String[] rotulos = new String[ns.length];
        for (int i = 0; i < ns.length; i++) {
            double cn = nyquist(b, ns[i]);
            boolean excede = cn > limite;
            if (excede) excedem.add(ns[i]);
            System.out.println(f("%-6d %-14.2f %-18s %-18s %-14s",
                    ns[i], log2(ns[i]), f("%,.0f", cn), f("%,.2f", limite),
                    excede ? "SIM (+" + f("%,.0f", cn - limite) + " bit/s)" : "não"));
            xs[i] = log2(ns[i]); // eixo em escala log2 para espaçar bem os níveis
            ys[i] = cn / 1000.0;
            rotulos[i] = String.valueOf(ns[i]);
        }

        List<Serie> series = new ArrayList<>();
        series.add(new Serie("Nyquist (canal ideal)", xs, ys, new Color(31, 119, 180), false, true));
        series.add(new Serie(f("Limite de Shannon (%s)", taxa(limite)),
                new double[]{xs[0], xs[xs.length - 1]}, new double[]{limite / 1000.0, limite / 1000.0},
                new Color(214, 39, 40), true, false));
        desenharGrafico("exp4_nyquist_vs_shannon.png", "Experimento 4 - Nyquist x Shannon (B = 3 kHz, SNR = 30 dB)",
                "Número de níveis N (eixo em escala log2)", "Capacidade (kbit/s)", series, xs, rotulos);

        System.out.println();
        System.out.println("Configurações em que Nyquist ultrapassa Shannon: N = " + excedem);
    }

    // =====================================================================
    // GRÁFICOS (Java2D, sem dependências externas)
    // =====================================================================

    record Serie(String nome, double[] x, double[] y, Color cor, boolean tracejada, boolean marcadores) {}

    static void desenharGrafico(String arquivo, String titulo, String rotuloX, String rotuloY,
                                List<Serie> series, double[] posTicksX, String[] rotulosTicksX) throws IOException {
        final int W = 1000, H = 620;
        final int ML = 90, MR = 40, MT = 60, MB = 80;
        int pw = W - ML - MR, ph = H - MT - MB;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        // Escala
        double xmin = posTicksX[0], xmax = posTicksX[posTicksX.length - 1];
        double pad = (xmax - xmin) * 0.04;
        xmin -= pad;
        xmax += pad;
        double ymax = 0;
        for (Serie s : series) for (double v : s.y()) ymax = Math.max(ymax, v);
        double[] esc = escalaY(ymax);
        double passoY = esc[0], topoY = esc[1];

        // Grade e eixos
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        FontMetrics fm = g.getFontMetrics();
        String fmtY = (passoY % 1 == 0) ? "%.0f" : "%.1f";
        g.setStroke(new BasicStroke(1f));
        for (double v = 0; v <= topoY + passoY * 1e-6; v += passoY) {
            int py = (int) Math.round(MT + ph - v / topoY * ph);
            g.setColor(new Color(225, 225, 225));
            g.drawLine(ML, py, ML + pw, py);
            g.setColor(Color.DARK_GRAY);
            String r = f(fmtY, v);
            g.drawString(r, ML - 10 - fm.stringWidth(r), py + fm.getAscent() / 2 - 2);
        }
        for (int i = 0; i < posTicksX.length; i++) {
            int px = (int) Math.round(ML + (posTicksX[i] - xmin) / (xmax - xmin) * pw);
            g.setColor(new Color(235, 235, 235));
            g.drawLine(px, MT, px, MT + ph);
            g.setColor(Color.DARK_GRAY);
            g.drawLine(px, MT + ph, px, MT + ph + 5);
            g.drawString(rotulosTicksX[i], px - fm.stringWidth(rotulosTicksX[i]) / 2, MT + ph + 22);
        }
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(ML, MT, ML, MT + ph);
        g.drawLine(ML, MT + ph, ML + pw, MT + ph);

        // Séries
        for (Serie s : series) {
            g.setColor(s.cor());
            if (s.tracejada()) {
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{10f, 8f}, 0f));
            } else {
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
            for (int i = 0; i + 1 < s.x().length; i++) {
                g.drawLine(mapX(s.x()[i], xmin, xmax, ML, pw), mapY(s.y()[i], topoY, MT, ph),
                        mapX(s.x()[i + 1], xmin, xmax, ML, pw), mapY(s.y()[i + 1], topoY, MT, ph));
            }
            if (s.marcadores()) {
                g.setStroke(new BasicStroke(1f));
                for (int i = 0; i < s.x().length; i++) {
                    int px = mapX(s.x()[i], xmin, xmax, ML, pw), py = mapY(s.y()[i], topoY, MT, ph);
                    g.setColor(s.cor());
                    g.fillOval(px - 5, py - 5, 10, 10);
                    g.setColor(Color.WHITE);
                    g.drawOval(px - 5, py - 5, 10, 10);
                }
            }
        }

        // Título e rótulos
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        FontMetrics fmT = g.getFontMetrics();
        g.drawString(titulo, (W - fmT.stringWidth(titulo)) / 2, 34);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        FontMetrics fmL = g.getFontMetrics();
        g.drawString(rotuloX, ML + (pw - fmL.stringWidth(rotuloX)) / 2, H - 25);
        Graphics2D gy = (Graphics2D) g.create();
        gy.rotate(-Math.PI / 2);
        gy.drawString(rotuloY, -(MT + (ph + fmL.stringWidth(rotuloY)) / 2), 28);
        gy.dispose();

        // Legenda (canto superior esquerdo da área de plotagem)
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        FontMetrics fmG = g.getFontMetrics();
        int larg = 0;
        for (Serie s : series) larg = Math.max(larg, fmG.stringWidth(s.nome()));
        int lx = ML + 15, ly = MT + 12, alt = series.size() * 24 + 10;
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRect(lx, ly, larg + 60, alt);
        g.setColor(new Color(180, 180, 180));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(lx, ly, larg + 60, alt);
        int yy = ly + 22;
        for (Serie s : series) {
            g.setColor(s.cor());
            if (s.tracejada()) {
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
            } else {
                g.setStroke(new BasicStroke(2.5f));
            }
            g.drawLine(lx + 8, yy - 5, lx + 38, yy - 5);
            g.setColor(Color.BLACK);
            g.drawString(s.nome(), lx + 46, yy);
            yy += 24;
        }

        g.dispose();
        ImageIO.write(img, "png", new File(PASTA_GRAFICOS, arquivo));
        System.out.println("[gráfico] " + PASTA_GRAFICOS + "/" + arquivo);
    }

    static int mapX(double x, double xmin, double xmax, int ml, int pw) {
        return (int) Math.round(ml + (x - xmin) / (xmax - xmin) * pw);
    }

    static int mapY(double y, double topo, int mt, int ph) {
        return (int) Math.round(mt + ph - y / topo * ph);
    }

    /** Escolhe um passo "bonito" para o eixo Y; retorna {passo, topo}. */
    static double[] escalaY(double max) {
        double bruto = max / 6.0;
        double mag = Math.pow(10, Math.floor(Math.log10(bruto)));
        double passo = mag * 10;
        for (double cand : new double[]{1, 2, 2.5, 5, 10}) {
            if (cand * mag >= bruto) {
                passo = cand * mag;
                break;
            }
        }
        double topo = Math.ceil(max / passo - 1e-9) * passo;
        return new double[]{passo, topo};
    }
}