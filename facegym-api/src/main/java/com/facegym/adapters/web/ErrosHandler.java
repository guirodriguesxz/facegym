package com.facegym.adapters.web;

import com.facegym.application.ConsentimentoAusente;
import com.facegym.application.NaoEncontrado;
import com.facegym.application.port.ReconhecimentoIndisponivel;
import com.facegym.application.port.RostoNaoEncontrado;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.facegym.adapters.memoria.MuitosDesafios;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ErrosHandler {

    private static ResponseEntity<Map<String, String>> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("mensagem", mensagem));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        HttpStatus s = "CPF já cadastrado".equals(e.getMessage()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return erro(s, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException e) {
        var campo = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse("Requisição inválida");
        return erro(HttpStatus.BAD_REQUEST, campo);
    }

    @ExceptionHandler(NaoEncontrado.class)
    ResponseEntity<Map<String, String>> naoEncontrado(NaoEncontrado e) { return erro(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(ConsentimentoAusente.class)
    ResponseEntity<Map<String, String>> consentimento(ConsentimentoAusente e) { return erro(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicado(DuplicateKeyException e) { return erro(HttpStatus.CONFLICT, "Registro duplicado"); }

    @ExceptionHandler(RostoNaoEncontrado.class)
    ResponseEntity<Map<String, String>> semRosto(RostoNaoEncontrado e) { return erro(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }

    @ExceptionHandler(ReconhecimentoIndisponivel.class)
    ResponseEntity<Map<String, String>> indisponivel(ReconhecimentoIndisponivel e) {
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Reconhecimento facial indisponível, tente em instantes");
    }

    @ExceptionHandler(MuitosDesafios.class)
    ResponseEntity<Map<String, String>> muitosDesafios(MuitosDesafios e) { return erro(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> grande(MaxUploadSizeExceededException e) {
        return erro(HttpStatus.PAYLOAD_TOO_LARGE, "Foto maior que 5 MB");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        return erro(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    @ExceptionHandler(com.facegym.application.LimiteDeVisitantes.class)
    ResponseEntity<Map<String, String>> limite(com.facegym.application.LimiteDeVisitantes e) {
        return erro(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
}
