package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.InterpretationCulture;
import br.com.soloibiapaba.domain.InterpretationParameter;
import br.com.soloibiapaba.repository.InterpretationCultureRepository;
import br.com.soloibiapaba.repository.InterpretationParameterRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final InterpretationCultureRepository cultureRepository;

    /** Construtor usado pelos testes unitários que analisam apenas os intervalos. */
    public InterpretationParameterService(InterpretationParameterRepository repository) {
        this.repository = repository;
        this.cultureRepository = null;
    }

    @Autowired
    public InterpretationParameterService(
            InterpretationParameterRepository repository,
            InterpretationCultureRepository cultureRepository) {
        this.repository = repository;
        this.cultureRepository = cultureRepository;
    }

    private String normalize(String crop) {
        if (crop == null || crop.isBlank() || crop.strip().length() > 120)
            throw new IllegalArgumentException("Informe uma cultura com até 120 caracteres.");
        String normalized = Normalizer.normalize(crop.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("Informe um nome de cultura válido.");
        return normalized;
    }

    @Transactional(readOnly = true)
    public String requireCulture(String crop) {
        return cultureRepository.findById(normalize(crop))
                .map(InterpretationCulture::getDisplayName)
                .orElseThrow(() -> new IllegalArgumentException("Selecione uma cultura cadastrada e salva na tela Parâmetros."));
    }

    @Transactional(readOnly = true)
    public List<String> cultures() {
        if (cultureRepository == null) return List.of();
        return cultureRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(InterpretationCulture::getDisplayName)
                .toList();
    }

    @Transactional(readOnly = true)
    public Profile template() {
        return new Profile("", DEFINITIONS.stream()
                .map(d -> new Parameter(d.indicator(), d.unit(), null, null)).toList());
    }

    @Transactional(readOnly = true)
    public Profile profile(String crop) {
        String normalized = normalize(crop);
        String displayName = crop.strip();
        if (cultureRepository != null) {
            displayName = cultureRepository.findById(normalized)
                    .map(InterpretationCulture::getDisplayName).orElse(displayName);
        }
        return profileFor(normalized, displayName);
    }

    private Profile profileFor(String normalized, String displayName) {
        var ranges = rangesByNormalizedCrop(normalized);
        return new Profile(displayName, DEFINITIONS.stream().map(d -> {
            var range = ranges.get(d.indicator());
            return new Parameter(d.indicator(), d.unit(), range == null ? null : range.minimum(), range == null ? null : range.maximum());
        }).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Range> ranges(String crop) {
        return rangesByNormalizedCrop(normalize(crop));
    }

    private Map<String, Range> rangesByNormalizedCrop(String normalizedCrop) {
        var result = new HashMap<String, Range>();
        repository.findByCrop(normalizedCrop).forEach(parameter -> result.put(parameter.getIndicator(),
                new Range(parameter.getIndicator(), parameter.getMinimum(), parameter.getMaximum())));
        return result;
    }

    @Transactional
    public Profile create(String crop, List<Range> parameters) {
        String normalized = normalize(crop);
        if (cultureRepository == null) throw new IllegalStateException("O cadastro de culturas não está disponível.");

        if (cultureRepository.existsById(normalized)) {
            throw new IllegalArgumentException("Já existe uma cultura com esse nome. Selecione-a na lista para editar seus parâmetros.");
        }

        String displayName = crop.strip();
        cultureRepository.saveAndFlush(new InterpretationCulture(normalized, displayName));
        return replaceParameters(normalized, displayName, parameters);
    }

    @Transactional
    public Profile save(String crop, List<Range> parameters) {
        String normalized = normalize(crop);
        String displayName = crop.strip();
        if (cultureRepository != null) {
            var existingCulture = cultureRepository.findById(normalized);
            if (existingCulture.isPresent()) {
                displayName = existingCulture.get().getDisplayName();
            } else {
                cultureRepository.saveAndFlush(new InterpretationCulture(normalized, displayName));
            }
        }
        return replaceParameters(normalized, displayName, parameters);
    }

    private Profile replaceParameters(String normalized, String displayName, List<Range> parameters) {
        if (parameters == null || parameters.size() > DEFINITIONS.size())
            throw new IllegalArgumentException("Lista de parâmetros inválida.");
        Set<String> seen = new HashSet<>();
        List<InterpretationParameter> entities = new ArrayList<>();
        for (var range : parameters) {
            if (range == null || DEFINITIONS.stream().noneMatch(d -> d.indicator().equals(range.indicator())) || !seen.add(range.indicator()))
                throw new IllegalArgumentException("Indicador inválido ou repetido.");
            if (range.minimum() == null && range.maximum() == null) continue;
            if (range.minimum() == null || range.maximum() == null || range.minimum().signum() < 0 || range.minimum().compareTo(range.maximum()) > 0)
                throw new IllegalArgumentException("Informe os dois limites não negativos, com mínimo menor ou igual ao máximo: " + range.indicator());
            for (var value : List.of(range.minimum(), range.maximum())) {
                var stripped = value.stripTrailingZeros();
                if (stripped.scale() > 10 || stripped.precision() - stripped.scale() > 14)
                    throw new IllegalArgumentException("Limite fora da precisão permitida: " + range.indicator());
            }
            String unit = DEFINITIONS.stream().filter(d -> d.indicator().equals(range.indicator())).findFirst().orElseThrow().unit();
            BigDecimal ceiling = unit.equals("%") ? new BigDecimal("100")
                    : unit.equals("g/kg") ? new BigDecimal("1000")
                    : range.indicator().equals("pH em água (calculado)") ? new BigDecimal("14") : null;
            if (ceiling != null && range.maximum().compareTo(ceiling) > 0)
                throw new IllegalArgumentException("Limite acima do máximo físico permitido: " + range.indicator());
            entities.add(new InterpretationParameter(normalized, range.indicator(), range.minimum(), range.maximum()));
        }
        repository.deleteByCrop(normalized);
        repository.flush();
        repository.saveAllAndFlush(entities);
        return profileFor(normalized, displayName);
    }

    public static String classify(BigDecimal value, Range range) {
        if (value == null) return "Sem dado";
        if (range == null) return "Não configurado";
        if (value.compareTo(range.minimum()) < 0) return "Baixo";
        if (value.compareTo(range.maximum()) > 0) return "Alto";
        return "Médio";
    }
}
