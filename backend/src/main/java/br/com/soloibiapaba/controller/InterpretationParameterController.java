package br.com.soloibiapaba.controller;

import br.com.soloibiapaba.service.InterpretationParameterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interpretation-parameters")
public class InterpretationParameterController {
    private final InterpretationParameterService service;

    public InterpretationParameterController(InterpretationParameterService service) {
        this.service = service;
    }

    public record SaveRequest(String crop, List<InterpretationParameterService.Range> parameters) {}

    @GetMapping("/cultures")
    public List<String> cultures() {
        return service.cultures();
    }

    @GetMapping("/template")
    public InterpretationParameterService.Profile template() {
        return service.template();
    }

    @GetMapping
    public InterpretationParameterService.Profile get(@RequestParam String crop) {
        return service.profile(crop);
    }

    @PostMapping
    public InterpretationParameterService.Profile create(@RequestBody SaveRequest request) {
        return service.create(request.crop(), request.parameters());
    }

    @PutMapping
    public InterpretationParameterService.Profile save(@RequestBody SaveRequest request) {
        return service.save(request.crop(), request.parameters());
    }
}
