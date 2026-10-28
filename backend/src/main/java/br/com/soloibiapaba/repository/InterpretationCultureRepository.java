package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.InterpretationCulture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterpretationCultureRepository extends JpaRepository<InterpretationCulture, String> {
    List<InterpretationCulture> findAllByOrderByDisplayNameAsc();
}
