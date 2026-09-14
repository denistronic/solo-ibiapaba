package br.com.soloibiapaba.controller;

import br.com.soloibiapaba.dto.CreateSampleRequest;
import br.com.soloibiapaba.dto.SampleResponse;
import br.com.soloibiapaba.dto.SoilAnalysisRequest;
import br.com.soloibiapaba.dto.SoilReportResponse;
import br.com.soloibiapaba.exception.AnalysisNotFoundException;
import br.com.soloibiapaba.service.SampleService;
import br.com.soloibiapaba.service.SoilInterpretationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/samples")
public class SampleController {

    private final SampleService sampleService;
    private final SoilInterpretationService interpretationService;

    public SampleController(
            SampleService sampleService,
            SoilInterpretationService interpretationService) {
        this.sampleService = sampleService;
        this.interpretationService = interpretationService;
    }

    @GetMapping
    public List<SampleResponse> findAll() {
        return sampleService.findAll().stream().map(SampleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SampleResponse findById(@PathVariable UUID id) {
        return SampleResponse.from(sampleService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SampleResponse create(@Valid @RequestBody CreateSampleRequest request) {
        return SampleResponse.from(sampleService.create(request));
    }

    @PutMapping("/{id}/analysis")
    public SampleResponse updateAnalysis(
            @PathVariable UUID id,
            @Valid @RequestBody SoilAnalysisRequest request) {

        return SampleResponse.from(sampleService.updateAnalysis(id, request));
    }

    @GetMapping("/{id}/interpretation")
    public SoilReportResponse interpretation(@PathVariable UUID id) {
        var sample = sampleService.findById(id);
        if (sample.analysis() == null) {
            throw new AnalysisNotFoundException(id);
        }
        return interpretationService.analyze(sample.analysis());
    }
}
