package br.com.soloibiapaba.exception;

import java.util.UUID;

public class SampleNotFoundException extends RuntimeException {

    public SampleNotFoundException(UUID id) {
        super("Amostragem não encontrada: " + id);
    }
}
