package com.seniorinsight.report;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.seniorinsight.report.application.porta.saida.EnviadorComprovante;

/** {@link EnviadorComprovante} dos testes: guarda os envios em memória. */
public class ComprovantesEnviados implements EnviadorComprovante {

    public record Envio(String email, UUID protocolo, Instant solicitadoEm, Instant concluidoEm) {
    }

    private final List<Envio> envios = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String email, UUID protocolo, Instant solicitadoEm, Instant concluidoEm) {
        envios.add(new Envio(email, protocolo, solicitadoEm, concluidoEm));
    }

    public List<Envio> doProtocolo(UUID protocolo) {
        return envios.stream().filter(envio -> envio.protocolo().equals(protocolo)).toList();
    }
}
