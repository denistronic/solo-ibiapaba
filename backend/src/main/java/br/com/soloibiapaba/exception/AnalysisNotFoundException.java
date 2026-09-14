package br.com.soloibiapaba.exception;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(UUID sampleId) {
        super("A amostragem ainda não possui laudo: " + sampleId);
    }
}
