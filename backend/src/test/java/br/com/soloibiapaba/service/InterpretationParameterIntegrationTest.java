package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.repository.InterpretationParameterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, inheritListeners = false)
class InterpretationParameterIntegrationTest {
    @Autowired InterpretationParameterService parameters;
    @Autowired InterpretationParameterRepository repository;
    @Autowired SoilInterpretationService reports;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired org.springframework.security.web.FilterChainProxy springSecurityFilterChain;
    @Autowired br.com.soloibiapaba.repository.UserRepository users;
    @Autowired JwtService jwt;

    @Test void shouldAllowOnlyAdministratorToChangeParametersThroughApi() throws Exception {
        var viewer = users.save(new br.com.soloibiapaba.domain.User("Leitor", "leitor-teste@example.test", "unused", br.com.soloibiapaba.domain.Role.USER));
        var admin = users.save(new br.com.soloibiapaba.domain.User("Administrador", "admin-teste@example.test", "unused", br.com.soloibiapaba.domain.Role.ADMIN));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain).build();
        String path = "/api/interpretation-parameters";
        String body = "{\"crop\":\"Permissões\",\"parameters\":[{\"indicator\":\"pH em água (calculado)\",\"minimum\":5.5,\"maximum\":6.5}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .header("Authorization", "Bearer " + jwt.generateToken(viewer))
                .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertThat(parameters.ranges("Permissões")).isEmpty();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .header("Authorization", "Bearer " + jwt.generateToken(admin))
                .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .param("crop", "Permissões").header("Authorization", "Bearer " + jwt.generateToken(viewer)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(parameters.ranges("Permissões")).hasSize(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is4xxClientError());
    }

    private InterpretationParameterService.Range range(String minimum, String maximum) {
        return new InterpretationParameterService.Range("pH em água (calculado)", new BigDecimal(minimum), new BigDecimal(maximum));
    }
    private SoilAnalysis analysis() {
        return new SoilAnalysis(null, new BigDecimal("5.4"), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
    @Test void shouldPersistByCultureAndUseNewLimitsWithoutChangingCalculationsOrCorrelations() {
        repository.deleteAll();
        var initial = reports.analyze(analysis(), "Acerola");
        assertThat(initial.indicators().getFirst().level()).isEqualTo("Não configurado");
        parameters.save("Acerola", List.of(range("6.1", "7")));
        assertThat(repository.findByCrop("acerola")).hasSize(1);
        assertThat(parameters.profile(" ACEROLA ").parameters().getFirst().minimum()).isEqualByComparingTo("6.1");
        var low = reports.analyze(analysis(), "Acerola");
        assertThat(low.indicators().getFirst().level()).isEqualTo("Baixo");
        assertThat(reports.analyze(analysis(), "Milho").indicators().getFirst().level()).isEqualTo("Não configurado");
        parameters.save("Milho", List.of(range("4", "5")));
        parameters.save("Acerola", List.of(range("6", "6")));
        var middle = reports.analyze(analysis(), "Acerola");
        assertThat(middle.indicators().getFirst().level()).isEqualTo("Médio");
        assertThat(reports.analyze(analysis(), "Milho").indicators().getFirst().level()).isEqualTo("Alto");
        assertThat(middle.derived()).isEqualTo(initial.derived());
        assertThat(middle.insights()).isEqualTo(initial.insights());
        assertThat(middle.indicators()).anySatisfy(i -> {
            assertThat(i.label()).isEqualTo("Matéria orgânica");
            assertThat(i.level()).isEqualTo("Sem dado");
        });
        assertThat(middle.indicators()).anySatisfy(i -> {
            assertThat(i.label()).isEqualTo("Saturação por bases");
            assertThat(i.level()).isEqualTo("Sem dado");
        });
        parameters.save("Acerola", List.of());
        assertThat(reports.analyze(analysis(), "Acerola").indicators().getFirst().level()).isEqualTo("Não configurado");
        assertThat(repository.findByCrop("milho")).hasSize(1);
    }
    @Test void shouldRejectInvalidRangesAtomicallyAndIncludeBothBoundaries() {
        parameters.save("Validação", List.of(range("5.5", "6.5")));
        assertThatThrownBy(() -> parameters.save("Validação", List.of(range("7", "6")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parameters.save("Validação", List.of(range("5", "6"), range("5", "6")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parameters.save("Validação", List.of(new InterpretationParameterService.Range("pH em água (calculado)", null, BigDecimal.TEN)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(parameters.profile("Validação").parameters().getFirst().minimum()).isEqualByComparingTo("5.5");
        var r = range("5.5", "6.5");
        assertThat(InterpretationParameterService.classify(new BigDecimal("5.5"), r)).isEqualTo("Médio");
        assertThat(InterpretationParameterService.classify(new BigDecimal("6.5"), r)).isEqualTo("Médio");
        assertThat(InterpretationParameterService.classify(new BigDecimal("6.5001"), r)).isEqualTo("Alto");
    }
}
