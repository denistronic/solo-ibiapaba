package br.com.soloibiapaba.service;
import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
@Service
public class SampleActorService {
    private final UserRepository users;
    public SampleActorService(UserRepository users) { this.users=users; }
    public AuditActor current() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated()) throw new InsufficientAuthenticationException("Autenticação obrigatória");
        var user=users.findForAuditByEmail(authentication.getName()).filter(User::isActive)
            .orElseThrow(()->new InsufficientAuthenticationException("Conta indisponível"));
        return new AuditActor(user.getId(),user.getName(),user.getEmail(),user.getRole());
    }
    public AuditActor requireAdmin() {
        var actor=current();
        if(!actor.admin()) throw new AccessDeniedException("Somente administrador pode realizar esta ação");
        return actor;
    }
}
