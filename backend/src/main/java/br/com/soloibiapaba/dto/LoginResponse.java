package br.com.soloibiapaba.dto;


public record LoginResponse(
        String token,
        String name,
        String email,
        String role
) {}