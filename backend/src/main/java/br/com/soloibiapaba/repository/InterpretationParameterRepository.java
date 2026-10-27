package br.com.soloibiapaba.repository;
import br.com.soloibiapaba.domain.InterpretationParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface InterpretationParameterRepository extends JpaRepository<InterpretationParameter, UUID> {
    List<InterpretationParameter> findByCrop(String crop);
    void deleteByCrop(String crop);
}
