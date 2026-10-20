package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.InterpretationParameter;
import br.com.soloibiapaba.repository.InterpretationParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

@Service
public class InterpretationParameterService {
    public record Definition(String indicator, String unit) {}
    public record Range(String indicator, BigDecimal minimum, BigDecimal maximum) {}
    public record Parameter(String indicator, String unit, BigDecimal minimum, BigDecimal maximum) {}
    public record Profile(String crop, List<Parameter> parameters) {}
    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("pH em água (calculado)", ""),
            new Definition("Matéria orgânica", "g/dm³"),
            new Definition("Carbono orgânico total", "g/dm³"),
            new Definition("Fósforo (P) — Resina", "g/dm³"),
            new Definition("Potássio (K)", "mmolc/dm³"),
            new Definition("Sódio (Na)", "mmolc/dm³"),
            new Definition("Cálcio (Ca)", "mmolc/dm³"),
            new Definition("Magnésio (Mg)", "mmolc/dm³"),
            new Definition("Alumínio total (Al)", "mmolc/dm³"),
            new Definition("Acidez total (H+Al)", "mmolc/dm³"),
            new Definition("CTC informada pelo laboratório", "mmolc/dm³"),
            new Definition("V informado pelo laboratório", "%"),
            new Definition("Saturação por bases", "%"),
            new Definition("Saturação por alumínio", "%"),
            new Definition("Enxofre (S)", "g/dm³"),
            new Definition("Boro (B)", "g/dm³"),
            new Definition("Cobre (Cu)", "g/dm³"),
            new Definition("Ferro (Fe)", "g/dm³"),
            new Definition("Manganês (Mn)", "g/dm³"),
            new Definition("Zinco (Zn)", "g/dm³"),
            new Definition("Argila", "g/kg"),
            new Definition("Silte", "g/kg"),
            new Definition("Areia total", "g/kg"));
    private final InterpretationParameterRepository repository;
    public InterpretationParameterService(InterpretationParameterRepository repository) { this.repository = repository; }

    private String normalize(String crop) {
        if (crop == null || crop.isBlank() || crop.strip().length() > 120)
            throw new IllegalArgumentException("Informe uma cultura com até 120 caracteres.");
        return Normalizer.normalize(crop.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    @Transactional(readOnly = true)
    public Profile profile(String crop) {
        var ranges = ranges(crop);
        return new Profile(crop.strip(), DEFINITIONS.stream().map(d -> {
            var r = ranges.get(d.indicator());
            return new Parameter(d.indicator(), d.unit(), r == null ? null : r.minimum(), r == null ? null : r.maximum());
        }).toList());
    }
    @Transactional(readOnly = true)
    public Map<String, Range> ranges(String crop) {
        var result = new HashMap<String, Range>();
        repository.findByCrop(normalize(crop)).forEach(p -> result.put(p.getIndicator(),
                new Range(p.getIndicator(), p.getMinimum(), p.getMaximum())));
        return result;
    }
    @Transactional
    public Profile save(String crop, List<Range> parameters) {
        String normalized = normalize(crop);
        if (parameters == null || parameters.size() > DEFINITIONS.size())
            throw new IllegalArgumentException("Lista de parâmetros inválida.");
        Set<String> seen = new HashSet<>();
        List<InterpretationParameter> entities = new ArrayList<>();
        for (var r : parameters) {
            if (r == null || DEFINITIONS.stream().noneMatch(d -> d.indicator().equals(r.indicator())) || !seen.add(r.indicator()))
                throw new IllegalArgumentException("Indicador inválido ou repetido.");
            if (r.minimum() == null && r.maximum() == null) continue;
            if (r.minimum() == null || r.maximum() == null || r.minimum().signum() < 0 || r.minimum().compareTo(r.maximum()) > 0)
                throw new IllegalArgumentException("Informe os dois limites não negativos, com mínimo menor ou igual ao máximo: " + r.indicator());
            for (var value : List.of(r.minimum(), r.maximum())) {
                var stripped = value.stripTrailingZeros();
                if (stripped.scale() > 10 || stripped.precision() - stripped.scale() > 14)
                    throw new IllegalArgumentException("Limite fora da precisão permitida: " + r.indicator());
            }
            entities.add(new InterpretationParameter(normalized, r.indicator(), r.minimum(), r.maximum()));
        }
        repository.deleteByCrop(normalized);
        repository.flush();
        repository.saveAllAndFlush(entities);
        return profile(crop);
    }
    public static String classify(BigDecimal value, Range range) {
        if (value == null) return "Sem dado";
        if (range == null) return "Não configurado";
        if (value.compareTo(range.minimum()) < 0) return "Baixo";
        if (value.compareTo(range.maximum()) > 0) return "Alto";
        return "Médio";
    }
}
