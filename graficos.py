"""
Gera os graficos e as tabelas a partir de resultados/resultados_brutos.csv.
(Rode experimentos.py antes.)

Saidas:
    figuras/fig1_ber_awgn.png          BER x SNR das 3 modulacoes (AWGN) + curvas teoricas
    figuras/fig2_awgn_vs_rajadas.png   comparacao dos dois canais
    figuras/fig3_codigos_de_linha.png  comparacao das codificacoes de linha (QPSK)
    figuras/fig4_constelacoes.png      constelacoes antes e depois do canal
    resultados/tabela_resumo.csv       media, desvio, minimo e maximo da BER
    resultados/tabela_compromisso.csv  BER x eficiencia espectral
"""

import os
from math import erfc, sqrt
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")           # salva as figuras em arquivo, sem abrir janela
import matplotlib.pyplot as plt
import simulador as sim

MODULACOES = ["BPSK", "QPSK", "16-QAM"]
CODIGOS = ["NRZ", "NRZ-I", "Manchester"]

# cor e marcador fixos para cada modulacao (usados em todas as figuras)
COR = {"BPSK": "#0072B2", "QPSK": "#D55E00", "16-QAM": "#009E73"}
MARCADOR = {"BPSK": "o", "QPSK": "s", "16-QAM": "^"}
# cor e marcador das codificacoes de linha (figura 3)
COR_CODIGO = {"NRZ": "#7B4FB5", "NRZ-I": "#C2407A", "Manchester": "#9A7400"}
MARCADOR_CODIGO = {"NRZ": "o", "NRZ-I": "D", "Manchester": "v"}

BER_MINIMA_GRAFICO = 1e-6

os.makedirs("figuras", exist_ok=True)
dados = pd.read_csv("resultados/resultados_brutos.csv")


# ---------------------------------------------------------------------------
# Tabela resumo: junta as 5 repeticoes de cada combinacao
# ---------------------------------------------------------------------------
resumo = dados.groupby(["canal", "codigo_linha", "modulacao", "snr_db"])["ber"].agg(
    ber_media="mean", ber_desvio="std", ber_min="min", ber_max="max").reset_index()
resumo.to_csv("resultados/tabela_resumo.csv", index=False)


def filtrar(canal, codigo, modulacao):
    """Devolve as linhas do resumo de uma combinacao, em ordem de SNR."""
    f = resumo[(resumo["canal"] == canal) &
               (resumo["codigo_linha"] == codigo) &
               (resumo["modulacao"] == modulacao)]
    return f.sort_values("snr_db")


# ---------------------------------------------------------------------------
# Curvas teoricas de BER em canal AWGN (em funcao da SNR por simbolo)
#   Q(x) = 0.5 * erfc(x / raiz(2))
#   BPSK:   BER = Q(raiz(2 * SNR))
#   QPSK:   BER = Q(raiz(SNR))
#   16-QAM: BER ~ (3/4) * Q(raiz(SNR / 5))   (aproximacao, boa para SNR media/alta)
# ---------------------------------------------------------------------------
def funcao_q(x):
    return 0.5 * erfc(x / sqrt(2))


def ber_teorica(modulacao, snr_db):
    snr = 10 ** (snr_db / 10)
    if modulacao == "BPSK":
        return funcao_q(sqrt(2 * snr))
    if modulacao == "QPSK":
        return funcao_q(sqrt(snr))
    if modulacao == "16-QAM":
        return 0.75 * funcao_q(sqrt(snr / 5))


def desenhar_curva(eixo, f, cor, marcador, rotulo, estilo="-", preenchido=True):
    """
    Desenha a BER media com barras indo do minimo ao maximo das repeticoes.
    Pontos com BER media igual a zero nao aparecem (escala logaritmica).
    """
    f = f[f["ber_media"] > 0]
    abaixo = f["ber_media"] - f["ber_min"]
    acima = f["ber_max"] - f["ber_media"]
    eixo.errorbar(f["snr_db"], f["ber_media"], yerr=[abaixo, acima],
                  color=cor, marker=marcador, linestyle=estilo, linewidth=2,
                  markersize=7, capsize=4, label=rotulo,
                  markerfacecolor=cor if preenchido else "white")


def formatar_eixos(eixo, titulo):
    eixo.set_yscale("log")
    eixo.set_ylim(BER_MINIMA_GRAFICO, 1)
    eixo.set_xticks(sorted(dados["snr_db"].unique()))
    eixo.set_xlabel("SNR (dB)")
    eixo.set_ylabel("BER")
    eixo.set_title(titulo)
    eixo.grid(True, which="both", color="#dddddd", linewidth=0.6)
    eixo.legend()


# ---------------------------------------------------------------------------
# Figura 1: BER x SNR em AWGN (codificacao NRZ) + teoria
# ---------------------------------------------------------------------------
figura, eixo = plt.subplots(figsize=(8, 5.5))
snrs_finos = np.arange(0, 20.1, 0.25)
for m in MODULACOES:
    desenhar_curva(eixo, filtrar("AWGN", "NRZ", m), COR[m], MARCADOR[m], m + " (simulado)", estilo="none")
    teoria = [ber_teorica(m, s) for s in snrs_finos]
    eixo.plot(snrs_finos, teoria, color=COR[m], linestyle="--", linewidth=1.5, label=m + " (teorico)")
formatar_eixos(eixo, "BER x SNR em canal AWGN (NRZ)\nmedia de 5 repeticoes de 100.000 bits; barras = minimo e maximo")
figura.tight_layout()
figura.savefig("figuras/fig1_ber_awgn.png", dpi=150)
plt.close(figura)


# ---------------------------------------------------------------------------
# Figura 2: AWGN x Rajadas (codificacao NRZ)
# ---------------------------------------------------------------------------
figura, eixo = plt.subplots(figsize=(8, 5.5))
for m in MODULACOES:
    desenhar_curva(eixo, filtrar("AWGN", "NRZ", m), COR[m], MARCADOR[m], m + " - AWGN")
    desenhar_curva(eixo, filtrar("Rajadas", "NRZ", m), COR[m], MARCADOR[m], m + " - AWGN + rajadas",
                   estilo="--", preenchido=False)
formatar_eixos(eixo, "Comparacao dos canais: AWGN x AWGN + rajadas (NRZ)\nmedia de 5 repeticoes; barras = minimo e maximo")
figura.tight_layout()
figura.savefig("figuras/fig2_awgn_vs_rajadas.png", dpi=150)
plt.close(figura)


# ---------------------------------------------------------------------------
# Figura 3: codificacoes de linha (modulacao QPSK, canal AWGN)
# ---------------------------------------------------------------------------
figura, eixo = plt.subplots(figsize=(8, 5.5))
for c in CODIGOS:
    # Manchester tracejado, porque a curva dele fica em cima da curva do NRZ
    estilo = "--" if c == "Manchester" else "-"
    desenhar_curva(eixo, filtrar("AWGN", c, "QPSK"), COR_CODIGO[c], MARCADOR_CODIGO[c], c, estilo=estilo)
formatar_eixos(eixo, "Codificacoes de linha com QPSK em canal AWGN\nmedia de 5 repeticoes; barras = minimo e maximo")
figura.tight_layout()
figura.savefig("figuras/fig3_codigos_de_linha.png", dpi=150)
plt.close(figura)


# ---------------------------------------------------------------------------
# Figura 4: constelacoes antes e depois do canal AWGN
# ---------------------------------------------------------------------------
SNR_CONSTELACAO = 14
rng = np.random.default_rng(2026)
figura, eixos = plt.subplots(2, 3, figsize=(11, 7.5))
for coluna, m in enumerate(MODULACOES):
    bits = rng.integers(0, 2, 2000 * sim.BITS_POR_SIMBOLO[m])   # 2000 simbolos
    tx = sim.modular(bits, m)
    rx = sim.canal_awgn(tx, SNR_CONSTELACAO, rng)

    cima = eixos[0, coluna]
    cima.scatter(tx.real, tx.imag, color=COR[m], s=60)
    cima.set_title(m + " - transmitido")

    baixo = eixos[1, coluna]
    baixo.scatter(rx.real, rx.imag, color=COR[m], s=4, alpha=0.5)
    baixo.set_title(m + " - recebido")

    for eixo in (cima, baixo):
        eixo.set_xlim(-1.8, 1.8)
        eixo.set_ylim(-1.8, 1.8)
        eixo.set_aspect("equal")
        eixo.axhline(0, color="#999999", linewidth=0.8)
        eixo.axvline(0, color="#999999", linewidth=0.8)
        eixo.set_xlabel("I (em fase)")
        eixo.set_ylabel("Q (quadratura)")
figura.suptitle(f"Constelacoes antes e depois do canal AWGN (SNR = {SNR_CONSTELACAO} dB, 2000 simbolos)")
figura.tight_layout()
figura.savefig("figuras/fig4_constelacoes.png", dpi=150)
plt.close(figura)


# ---------------------------------------------------------------------------
# Tabela de compromisso BER x eficiencia espectral (codificacao NRZ)
# ---------------------------------------------------------------------------
def ber_em(canal, modulacao, snr_db):
    f = filtrar(canal, "NRZ", modulacao)
    return f[f["snr_db"] == snr_db]["ber_media"].iloc[0]


def snr_minima(modulacao, ber_alvo):
    """Menor SNR simulada (AWGN, NRZ) em que a BER media fica <= ber_alvo."""
    f = filtrar("AWGN", "NRZ", modulacao)
    atende = f[f["ber_media"] <= ber_alvo]
    if len(atende) == 0:
        return None
    return int(atende["snr_db"].min())


linhas = []
for m in MODULACOES:
    linhas.append({
        "modulacao": m,
        "bits_por_simbolo": sim.BITS_POR_SIMBOLO[m],
        "eficiencia_bits_s_hz": sim.eficiencia_espectral(m, "NRZ"),
        "ber_awgn_6dB": ber_em("AWGN", m, 6),
        "ber_awgn_10dB": ber_em("AWGN", m, 10),
        "ber_awgn_14dB": ber_em("AWGN", m, 14),
        "ber_awgn_20dB": ber_em("AWGN", m, 20),
        "snr_min_para_ber_1e-3_dB": snr_minima(m, 1e-3),
        "ber_rajadas_20dB": ber_em("Rajadas", m, 20),
    })
compromisso = pd.DataFrame(linhas)
compromisso.to_csv("resultados/tabela_compromisso.csv", index=False)

print("Tabela de compromisso (codificacao NRZ):")
print(compromisso.to_string(index=False, float_format=lambda v: f"{v:.2e}" if 0 < v < 0.1 else f"{v:g}"))
print("\nFiguras salvas em figuras/ e tabelas em resultados/")
