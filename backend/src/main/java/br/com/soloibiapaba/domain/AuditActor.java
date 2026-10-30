package br.com.soloibiapaba.domain;
import java.util.UUID;
public record AuditActor(UUID id,String name,String email,Role role) {
    public boolean admin() { return role==Role.ADMIN; }
}
