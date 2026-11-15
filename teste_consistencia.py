"""
Teste de consistencia e demonstracao com a mensagem ASCII.

1) Canal ideal (sem ruido) e SNR alta (30 dB): a mensagem tem que voltar
   identica em todas as combinacoes de codificacao de linha e modulacao.
2) Canal com ruido: mostra a mensagem recebida e quais bits chegaram errados.

A saida e mostrada na tela e salva em resultados/teste_consistencia.txt
"""

import os
import numpy as np
import simulador as sim

MENSAGEM = "Redes de Computadores - Unisinos 2026/2 - Trabalho II"
SEMENTE = 2026
CODIGOS = ["NRZ", "NRZ-I", "Manchester"]
MODULACOES = ["BPSK", "QPSK", "16-QAM"]

linhas = []   # guarda tudo que for impresso, para salvar no arquivo


def escrever(texto=""):
    print(texto)
    linhas.append(texto)


def teste_recuperacao(bits, canal, snr_db):
    """Roda as 9 combinacoes e confere se a mensagem volta igual."""
    tudo_certo = True
    for codigo in CODIGOS:
        for modulacao in MODULACOES:
            rng = np.random.default_rng(SEMENTE)
            r = sim.transmitir(bits, codigo, modulacao, canal, snr_db, rng)
            texto_rx = sim.bits_para_texto(r["bits_rx"])
            ok = (texto_rx == MENSAGEM) and (r["erros"] == 0)
            if not ok:
                tudo_certo = False
            situacao = "OK" if ok else "FALHOU"
            escrever(f"  {codigo:<10} + {modulacao:<6}: erros = {r['erros']:>3}   BER = {r['ber']:.4f}   {situacao}")
    return tudo_certo


def demonstracao_com_ruido(bits, codigo, modulacao, canal, snr_db):
    """Transmite a mensagem com ruido e mostra onde estao os bits errados."""
    rng = np.random.default_rng(SEMENTE)
    r = sim.transmitir(bits, codigo, modulacao, canal, snr_db, rng)
    posicoes = np.where(bits != r["bits_rx"])[0]

    escrever(f"  {codigo} + {modulacao}, canal {canal}, SNR = {snr_db} dB")
    escrever(f"    enviada : {MENSAGEM}")
    escrever(f"    recebida: {sim.bits_para_texto(r['bits_rx'])}")
    escrever(f"    bits errados: {r['erros']} de {len(bits)}  (BER = {r['ber']:.4f})")
    if codigo == "Manchester":
        escrever(f"    violacoes do Manchester detectadas: {r['violacoes']}")
    if len(posicoes) > 0:
        # mostra no maximo 20 posicoes para nao poluir a tela
        escrever(f"    posicoes dos bits errados (ate 20): {posicoes[:20].tolist()}")
        caracteres = sorted(set((posicoes // 8).tolist()))
        escrever(f"    caracteres afetados: {len(caracteres)} de {len(MENSAGEM)}")
    escrever()


def demonstracao_rajadas(codigo, modulacao, snr_db, copias=40):
    """
    A mensagem e curta e pode nao pegar nenhuma rajada. Por isso aqui ela e
    repetida varias vezes, e mostramos as copias que chegaram com erro.
    """
    bits_longos = sim.texto_para_bits(MENSAGEM * copias)
    rng = np.random.default_rng(SEMENTE)
    r = sim.transmitir(bits_longos, codigo, modulacao, "Rajadas", snr_db, rng)
    texto_rx = sim.bits_para_texto(r["bits_rx"])
    posicoes = np.where(bits_longos != r["bits_rx"])[0]

    escrever(f"  {codigo} + {modulacao}, canal Rajadas, SNR = {snr_db} dB, mensagem repetida {copias} vezes")
    escrever(f"    bits errados: {r['erros']} de {len(bits_longos)}  (BER = {r['ber']:.4f})")

    tamanho = len(MENSAGEM)
    copias_com_erro = 0
    for c in range(copias):
        copia_rx = texto_rx[c * tamanho:(c + 1) * tamanho]
        if copia_rx != MENSAGEM:
            copias_com_erro += 1
            if copias_com_erro <= 3:      # mostra so as 3 primeiras
                escrever(f"    copia {c:>2}: {copia_rx}")
    escrever(f"    copias com erro: {copias_com_erro} de {copias}")
    if len(posicoes) > 0:
        escrever(f"    posicoes dos bits errados (ate 20): {posicoes[:20].tolist()}")
    escrever()


bits = sim.texto_para_bits(MENSAGEM)

escrever("=" * 70)
escrever("TESTE DE CONSISTENCIA")
escrever("=" * 70)
escrever(f"Mensagem: \"{MENSAGEM}\"")
escrever(f"Tamanho: {len(MENSAGEM)} caracteres = {len(bits)} bits")
escrever(f"Primeiros 16 bits: {''.join(str(b) for b in bits[:16])}  ('{MENSAGEM[:2]}')")
escrever()

escrever("1) Canal ideal (sem ruido)")
ok_ideal = teste_recuperacao(bits, "ideal", None)
escrever()

escrever("2) Canal AWGN com SNR alta (30 dB)")
ok_snr_alta = teste_recuperacao(bits, "AWGN", 30)
escrever()

if ok_ideal and ok_snr_alta:
    escrever("Resultado: mensagem recuperada sem erros nas 18 execucoes.")
else:
    escrever("Resultado: HOUVE DIVERGENCIA, verificar o simulador.")
escrever()

escrever("=" * 70)
escrever("DEMONSTRACAO COM RUIDO (identificacao dos bits incorretos)")
escrever("=" * 70)
demonstracao_com_ruido(bits, "NRZ", "BPSK", "AWGN", 6)
demonstracao_com_ruido(bits, "NRZ", "QPSK", "AWGN", 6)
demonstracao_com_ruido(bits, "NRZ", "16-QAM", "AWGN", 6)
demonstracao_com_ruido(bits, "NRZ-I", "QPSK", "AWGN", 6)
demonstracao_com_ruido(bits, "Manchester", "QPSK", "AWGN", 6)
demonstracao_rajadas("NRZ", "QPSK", 20)

os.makedirs("resultados", exist_ok=True)
with open("resultados/teste_consistencia.txt", "w") as arquivo:
    arquivo.write("\n".join(linhas) + "\n")
