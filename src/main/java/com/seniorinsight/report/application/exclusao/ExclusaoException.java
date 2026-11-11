package com.seniorinsight.report.application.exclusao;

/** Pedido de exclusão recusado. O {@code Tipo} diz o que o adaptador de entrada deve responder. */
public class ExclusaoException extends RuntimeException {

    public enum Tipo { TITULAR_NAO_ENCONTRADO, ACESSO_NEGADO, CONFLITO }

    private final Tipo tipo;

    private ExclusaoException(Tipo tipo, String mensagem) {
        super(mensagem);
        this.tipo = tipo;
    }

    public Tipo tipo() {
        return tipo;
    }

    public static ExclusaoException titularNaoEncontrado() {
        return new ExclusaoException(Tipo.TITULAR_NAO_ENCONTRADO, "Titular não encontrado");
    }

    public static ExclusaoException acessoNegado() {
        return new ExclusaoException(Tipo.ACESSO_NEGADO,
                "Só o próprio titular pode pedir a exclusão dos seus dados");
    }

    public static ExclusaoException pedidoEmAndamento() {
        return new ExclusaoException(Tipo.CONFLITO, "Já existe um pedido de exclusão em andamento para o titular");
    }

    public static ExclusaoException assinaturaAtiva() {
        return new ExclusaoException(Tipo.CONFLITO,
                "Há uma assinatura Premium ativa: cancele a assinatura antes de pedir a exclusão");
    }
}
