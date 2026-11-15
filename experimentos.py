"""
Experimentos do trabalho.

Para cada combinacao de canal, codificacao de linha, modulacao e SNR, transmite
N_BITS bits aleatorios e mede a BER. Cada combinacao e repetida N_REPETICOES
vezes, cada vez com uma semente diferente.

Saidas:
    resultados/resultados_brutos.csv  -> uma linha por execucao
    resultados/configuracao.json      -> parametros usados
"""

import json
import os
import time
import numpy as np
import pandas as pd
import simulador as sim

# ----------------------------- configuracao --------------------------------
CONFIG = {
    "n_bits": 100000,                       # bits de informacao por execucao
    "n_repeticoes": 5,                      # repeticoes com sementes diferentes
    "semente_base": 2026,                   # semente da repeticao r = semente_base + r
    "snrs_db": [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20],
    "codigos_de_linha": ["NRZ", "NRZ-I", "Manchester"],
    "modulacoes": ["BPSK", "QPSK", "16-QAM"],
    "canais": ["AWGN", "Rajadas"],
    # parametros do canal com rajadas (valores padrao de sim.canal_rajadas)
    "rajada_prob_inicio": 0.001,            # chance de cada simbolo iniciar uma rajada
    "rajada_tamanho": 50,                   # duracao da rajada em simbolos
    "rajada_potencia": 1.0,                 # potencia do ruido extra (1.0 = igual ao sinal)
    "definicao_snr": "potencia media do sinal / potencia do ruido, por simbolo",
}
# ---------------------------------------------------------------------------

os.makedirs("resultados", exist_ok=True)
with open("resultados/configuracao.json", "w") as arquivo:
    json.dump(CONFIG, arquivo, indent=4)

inicio = time.time()
linhas = []

for repeticao in range(CONFIG["n_repeticoes"]):
    semente = CONFIG["semente_base"] + repeticao
    rng = np.random.default_rng(semente)

    # os mesmos bits sao usados em todas as combinacoes desta repeticao
    bits = rng.integers(0, 2, CONFIG["n_bits"])

    for canal in CONFIG["canais"]:
        for codigo in CONFIG["codigos_de_linha"]:
            for modulacao in CONFIG["modulacoes"]:
                for snr_db in CONFIG["snrs_db"]:
                    r = sim.transmitir(bits, codigo, modulacao, canal, snr_db, rng)
                    linhas.append({
                        "canal": canal,
                        "codigo_linha": codigo,
                        "modulacao": modulacao,
                        "snr_db": snr_db,
                        "repeticao": repeticao,
                        "semente": semente,
                        "n_bits": CONFIG["n_bits"],
                        "n_simbolos": len(r["simbolos_tx"]),
                        "erros": r["erros"],
                        "ber": r["ber"],
                        "violacoes_manchester": r["violacoes"],
                        "eficiencia_bits_s_hz": sim.eficiencia_espectral(modulacao, codigo),
                    })
    print(f"Repeticao {repeticao + 1} de {CONFIG['n_repeticoes']} concluida (semente {semente})")

tabela = pd.DataFrame(linhas)
tabela.to_csv("resultados/resultados_brutos.csv", index=False)

print(f"\n{len(tabela)} execucoes salvas em resultados/resultados_brutos.csv")
print(f"Tempo total: {time.time() - inicio:.1f} s")
