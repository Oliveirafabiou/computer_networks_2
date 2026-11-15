"""
Simulador de transmissao digital - Trabalho II de Redes de Computadores.

Cadeia completa:
    texto ASCII -> bits -> codificacao de linha -> modulacao -> canal ruidoso
    -> demodulacao -> decodificacao de linha -> bits -> texto

Este arquivo so tem as funcoes do simulador. Os experimentos ficam em
experimentos.py e os graficos em graficos.py.
"""

import numpy as np


# ---------------------------------------------------------------------------
# 1. Mensagem ASCII <-> bits
# ---------------------------------------------------------------------------

def texto_para_bits(texto):
    """Converte um texto ASCII em um vetor de bits (8 bits por caractere)."""
    bits = []
    for caractere in texto:
        codigo = ord(caractere)            # ex.: 'A' -> 65
        binario = format(codigo, "08b")    # ex.: 65 -> '01000001'
        for b in binario:
            bits.append(int(b))
    return np.array(bits)


def bits_para_texto(bits):
    """Converte um vetor de bits de volta para texto (8 bits por caractere)."""
    texto = ""
    for i in range(0, len(bits) - 7, 8):
        byte = bits[i:i + 8]
        codigo = int("".join(str(int(b)) for b in byte), 2)
        # caracteres fora da faixa imprimivel viram '?', so para exibir na tela
        if 32 <= codigo <= 126:
            texto += chr(codigo)
        else:
            texto += "?"
    return texto


# ---------------------------------------------------------------------------
# 2. Codificacoes de linha
#
# A saida da codificacao de linha e uma sequencia de niveis (0 = baixo,
# 1 = alto). Essa sequencia e o que entra no modulador.
# ---------------------------------------------------------------------------

def nrz_codificar(bits):
    """
    NRZ: o mais simples. Bit 1 -> nivel alto, bit 0 -> nivel baixo, durante
    todo o tempo do bit. Gera 1 nivel por bit. Serve como referencia.
    """
    return bits.copy()


def nrz_decodificar(niveis):
    """NRZ: o nivel recebido ja e o bit."""
    return niveis.copy()


def nrzi_codificar(bits):
    """
    NRZ-I: o bit 1 causa uma TRANSICAO de nivel, o bit 0 mantem o nivel.
    O nivel inicial (antes do primeiro bit) e 0.
    Gera 1 nivel por bit.
    """
    niveis = np.zeros(len(bits), dtype=int)
    nivel_atual = 0
    for i in range(len(bits)):
        if bits[i] == 1:
            nivel_atual = 1 - nivel_atual   # inverte o nivel
        niveis[i] = nivel_atual
    return niveis


def nrzi_decodificar(niveis):
    """
    NRZ-I: se o nivel mudou em relacao ao anterior, o bit e 1; senao e 0.
    O primeiro nivel e comparado com o nivel inicial 0.
    """
    anteriores = np.concatenate(([0], niveis[:-1]))
    bits = (niveis != anteriores).astype(int)
    return bits


def manchester_codificar(bits):
    """
    Manchester (convencao IEEE 802.3):
        bit 0 -> alto, baixo  (1, 0)
        bit 1 -> baixo, alto  (0, 1)
    Sempre existe uma transicao no meio do bit. Gera 2 niveis por bit.
    """
    niveis = np.zeros(2 * len(bits), dtype=int)
    for i in range(len(bits)):
        if bits[i] == 0:
            niveis[2 * i] = 1
            niveis[2 * i + 1] = 0
        else:
            niveis[2 * i] = 0
            niveis[2 * i + 1] = 1
    return niveis


def manchester_decodificar(niveis):
    """
    Manchester: cada par de niveis vira um bit.
        (1, 0) -> 0        (0, 1) -> 1
    Os pares (0, 0) e (1, 1) nao existem no Manchester. Se aparecerem, houve
    erro no canal: contamos como "violacao" e decidimos pela segunda metade.
    Retorna os bits e o numero de violacoes detectadas.
    """
    primeira_metade = niveis[0::2]
    segunda_metade = niveis[1::2]
    bits = segunda_metade.copy()
    violacoes = int(np.sum(primeira_metade == segunda_metade))
    return bits, violacoes


def codificar_linha(bits, codigo):
    """Escolhe a codificacao de linha pelo nome."""
    if codigo == "NRZ":
        return nrz_codificar(bits)
    if codigo == "NRZ-I":
        return nrzi_codificar(bits)
    if codigo == "Manchester":
        return manchester_codificar(bits)
    raise ValueError("Codificacao de linha desconhecida: " + codigo)


def decodificar_linha(niveis, codigo):
    """Decodifica e retorna (bits, violacoes). So o Manchester detecta violacoes."""
    if codigo == "NRZ":
        return nrz_decodificar(niveis), 0
    if codigo == "NRZ-I":
        return nrzi_decodificar(niveis), 0
    if codigo == "Manchester":
        return manchester_decodificar(niveis)
    raise ValueError("Codificacao de linha desconhecida: " + codigo)


# ---------------------------------------------------------------------------
# 3. Modulacoes digitais
#
# Cada simbolo e um numero complexo: parte real = componente I (em fase),
# parte imaginaria = componente Q (quadratura). Todas as constelacoes sao
# normalizadas para potencia media 1, para a comparacao ser justa.
# ---------------------------------------------------------------------------

BITS_POR_SIMBOLO = {"BPSK": 1, "QPSK": 2, "16-QAM": 4}


def completar_com_zeros(bits, k):
    """Completa com zeros ate o tamanho ser multiplo de k (bits por simbolo)."""
    sobra = len(bits) % k
    if sobra != 0:
        bits = np.concatenate((bits, np.zeros(k - sobra, dtype=int)))
    return bits


def bpsk_modular(bits):
    """BPSK: bit 0 -> +1 (fase 0 graus), bit 1 -> -1 (fase 180 graus)."""
    return (1 - 2 * bits).astype(complex)


def bpsk_demodular(simbolos):
    """BPSK: se a parte real e negativa, o bit e 1."""
    return (simbolos.real < 0).astype(int)


def qpsk_modular(bits):
    """
    QPSK: 2 bits por simbolo. O primeiro bit define o eixo I e o segundo o
    eixo Q (0 -> positivo, 1 -> negativo). Divide por raiz de 2 para a
    potencia media ser 1.
    """
    bits = completar_com_zeros(bits, 2)
    i = 1 - 2 * bits[0::2]
    q = 1 - 2 * bits[1::2]
    return (i + 1j * q) / np.sqrt(2)


def qpsk_demodular(simbolos):
    """QPSK: o sinal de cada eixo decide um bit."""
    bits = np.zeros(2 * len(simbolos), dtype=int)
    bits[0::2] = (simbolos.real < 0).astype(int)
    bits[1::2] = (simbolos.imag < 0).astype(int)
    return bits


# 16-QAM: cada eixo carrega 2 bits em 4 niveis de amplitude (-3, -1, +1, +3).
# Usamos codigo Gray: niveis vizinhos diferem em apenas 1 bit.
#     00 -> -3     01 -> -1     11 -> +1     10 -> +3
# A potencia media da constelacao com esses niveis e 10, por isso dividimos
# por raiz de 10.
RAIZ_10 = np.sqrt(10)


def dois_bits_para_nivel(b0, b1):
    """Converte pares de bits (Gray) em niveis -3, -1, +1, +3."""
    nivel = np.zeros(len(b0))
    nivel[(b0 == 0) & (b1 == 0)] = -3
    nivel[(b0 == 0) & (b1 == 1)] = -1
    nivel[(b0 == 1) & (b1 == 1)] = 1
    nivel[(b0 == 1) & (b1 == 0)] = 3
    return nivel


def nivel_para_dois_bits(valor):
    """Decide o nivel mais proximo (limiares em -2, 0, +2) e devolve os 2 bits."""
    b0 = (valor > 0).astype(int)             # lado positivo -> primeiro bit 1
    b1 = (np.abs(valor) < 2).astype(int)     # niveis internos (-1, +1) -> segundo bit 1
    return b0, b1


def qam16_modular(bits):
    """16-QAM: 4 bits por simbolo (2 para o eixo I e 2 para o eixo Q)."""
    bits = completar_com_zeros(bits, 4)
    i = dois_bits_para_nivel(bits[0::4], bits[1::4])
    q = dois_bits_para_nivel(bits[2::4], bits[3::4])
    return (i + 1j * q) / RAIZ_10


def qam16_demodular(simbolos):
    """16-QAM: decide o nivel de cada eixo separadamente."""
    simbolos = simbolos * RAIZ_10   # volta para a escala -3, -1, +1, +3
    bits = np.zeros(4 * len(simbolos), dtype=int)
    bits[0::4], bits[1::4] = nivel_para_dois_bits(simbolos.real)
    bits[2::4], bits[3::4] = nivel_para_dois_bits(simbolos.imag)
    return bits


def modular(bits, modulacao):
    """Escolhe a modulacao pelo nome."""
    if modulacao == "BPSK":
        return bpsk_modular(bits)
    if modulacao == "QPSK":
        return qpsk_modular(bits)
    if modulacao == "16-QAM":
        return qam16_modular(bits)
    raise ValueError("Modulacao desconhecida: " + modulacao)


def demodular(simbolos, modulacao):
    """Escolhe a demodulacao pelo nome."""
    if modulacao == "BPSK":
        return bpsk_demodular(simbolos)
    if modulacao == "QPSK":
        return qpsk_demodular(simbolos)
    if modulacao == "16-QAM":
        return qam16_demodular(simbolos)
    raise ValueError("Modulacao desconhecida: " + modulacao)


# ---------------------------------------------------------------------------
# 4. Canais
# ---------------------------------------------------------------------------

def ruido_gaussiano(quantidade, potencia, rng):
    """
    Gera ruido branco gaussiano complexo com a potencia total pedida.
    A potencia e dividida igualmente entre os eixos I e Q.
    """
    desvio = np.sqrt(potencia / 2)
    return desvio * (rng.standard_normal(quantidade) + 1j * rng.standard_normal(quantidade))


def canal_awgn(simbolos, snr_db, rng):
    """
    Canal AWGN. A SNR e a razao entre a potencia do sinal (1, pois as
    constelacoes sao normalizadas) e a potencia do ruido:
        SNR = S / N   ->   N = S / SNR
        SNR(dB) = 10 * log10(SNR)   ->   SNR = 10 ** (SNR(dB) / 10)
    """
    snr = 10 ** (snr_db / 10)
    potencia_ruido = 1 / snr
    return simbolos + ruido_gaussiano(len(simbolos), potencia_ruido, rng)


def canal_rajadas(simbolos, snr_db, rng, prob_inicio=0.001, tamanho=50, potencia_rajada=1.0):
    """
    Segundo cenario: AWGN + ruido impulsivo em rajadas.

    Alem do ruido AWGN normal, cada simbolo tem probabilidade 'prob_inicio' de
    iniciar uma rajada. Durante a rajada ('tamanho' simbolos seguidos) e somado
    um ruido extra forte, de potencia 'potencia_rajada' (1.0 = mesma potencia
    do sinal). Esse ruido extra NAO depende da SNR: ele representa uma
    interferencia externa (ex.: um motor ligando perto do cabo).

    Retorna os simbolos recebidos e uma mascara dizendo quais simbolos foram
    atingidos por rajada.
    """
    recebidos = canal_awgn(simbolos, snr_db, rng)

    n = len(simbolos)
    em_rajada = np.zeros(n, dtype=bool)
    inicios = np.where(rng.random(n) < prob_inicio)[0]
    for inicio in inicios:
        em_rajada[inicio:inicio + tamanho] = True

    ruido_extra = ruido_gaussiano(n, potencia_rajada, rng)
    recebidos = recebidos + ruido_extra * em_rajada
    return recebidos, em_rajada


# ---------------------------------------------------------------------------
# 5. Metricas
# ---------------------------------------------------------------------------

def calcular_ber(bits_enviados, bits_recebidos):
    """BER = bits errados / bits transmitidos. Retorna (ber, numero de erros)."""
    erros = int(np.sum(bits_enviados != bits_recebidos))
    return erros / len(bits_enviados), erros


def eficiencia_espectral(modulacao, codigo):
    """
    Eficiencia espectral nominal em bits/s/Hz.
    Consideramos a banda igual a taxa de simbolos (1 simbolo/s por Hz), entao
    a modulacao sozinha entrega log2(M) = bits por simbolo.
    O Manchester gera 2 niveis por bit, entao so metade do que e transmitido
    e informacao: a eficiencia cai pela metade.
    """
    k = BITS_POR_SIMBOLO[modulacao]
    if codigo == "Manchester":
        return k / 2
    return float(k)


# ---------------------------------------------------------------------------
# 6. Cadeia completa
# ---------------------------------------------------------------------------

def transmitir(bits, codigo, modulacao, canal, snr_db, rng):
    """
    Executa a cadeia completa e retorna um dicionario com os resultados.

    canal pode ser: "ideal" (sem ruido), "AWGN" ou "Rajadas".
    """
    # --- transmissor ---
    niveis = codificar_linha(bits, codigo)
    simbolos_tx = modular(niveis, modulacao)

    # --- canal ---
    if canal == "ideal":
        simbolos_rx = simbolos_tx.copy()
    elif canal == "AWGN":
        simbolos_rx = canal_awgn(simbolos_tx, snr_db, rng)
    elif canal == "Rajadas":
        simbolos_rx, _ = canal_rajadas(simbolos_tx, snr_db, rng)
    else:
        raise ValueError("Canal desconhecido: " + canal)

    # --- receptor ---
    niveis_rx = demodular(simbolos_rx, modulacao)
    niveis_rx = niveis_rx[:len(niveis)]      # descarta os zeros de preenchimento
    bits_rx, violacoes = decodificar_linha(niveis_rx, codigo)

    ber, erros = calcular_ber(bits, bits_rx)
    return {
        "bits_rx": bits_rx,
        "ber": ber,
        "erros": erros,
        "violacoes": violacoes,
        "simbolos_tx": simbolos_tx,
        "simbolos_rx": simbolos_rx,
    }
