package com.seniorinsight.report.adapters.in.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.seniorinsight.report.application.exclusao.ExclusaoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Respostas de erro no formato {@code Erro} do contrato: timestamp, status, error, message e path. */
@RestControllerAdvice
class TratadorErros {

    @ExceptionHandler(ExclusaoException.class)
    ResponseEntity<Map<String, Object>> exclusao(ExclusaoException e, HttpServletRequest requisicao) {
        HttpStatus status = switch (e.tipo()) {
            case TITULAR_NAO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case ACESSO_NEGADO -> HttpStatus.FORBIDDEN;
            case CONFLITO -> HttpStatus.CONFLICT;
        };
        return erro(status, e.getMessage(), requisicao);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException e, HttpServletRequest requisicao) {
        String mensagem = e.getBindingResult().getFieldErrors().stream()
                .map(campo -> campo.getDefaultMessage())
                .findFirst().orElse("Dados da requisição inválidos");
        return erro(HttpStatus.BAD_REQUEST, mensagem, requisicao);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> requisicaoInvalida(Exception e, HttpServletRequest requisicao) {
        return erro(HttpStatus.BAD_REQUEST, "Dados da requisição inválidos", requisicao);
    }

    private static ResponseEntity<Map<String, Object>> erro(HttpStatus status, String mensagem,
                                                            HttpServletRequest requisicao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", Instant.now().toString());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", mensagem);
        corpo.put("path", requisicao.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}
