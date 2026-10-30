package br.com.soloibiapaba.exception;
public class MissingRecordVersionException extends RuntimeException { public MissingRecordVersionException() { super("Informe If-Match com a versão lida do registro. Reabra a amostra antes de editar."); } }
