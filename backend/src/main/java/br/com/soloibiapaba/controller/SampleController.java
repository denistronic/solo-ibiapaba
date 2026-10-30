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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SampleResponse> findById(@PathVariable UUID id) {
        return response(sampleService.findById(id),false);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<SampleResponse> create(@Valid @RequestBody CreateSampleRequest request) {
        return response(sampleService.create(request),true);
    }

    @PutMapping("/{id}/analysis")
    public ResponseEntity<SampleResponse> updateAnalysis(
            @PathVariable UUID id,
            @Valid @RequestBody SoilAnalysisRequest request,
            @RequestHeader(value="If-Match",required=false) String ifMatch,
            @RequestHeader(value="If-None-Match",required=false) String ifNoneMatch) {
        if(ifNoneMatch!=null&&!ifNoneMatch.equals("*"))throw new IllegalArgumentException("If-None-Match deve ser *.");
        if(ifMatch!=null&&ifNoneMatch!=null)throw new IllegalArgumentException("Use apenas If-Match ou If-None-Match.");
        return response(sampleService.updateAnalysis(id,request,parseVersion(ifMatch),ifNoneMatch!=null),false);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SampleResponse> updateDetails(@PathVariable UUID id,@Valid @RequestBody CreateSampleRequest request,
            @RequestHeader(value="If-Match",required=false) String ifMatch) {
        return response(sampleService.updateDetails(id,request,parseVersion(ifMatch)),false);
    }
    private ResponseEntity<SampleResponse> response(br.com.soloibiapaba.domain.Sample sample,boolean created) {
        return ResponseEntity.status(created?HttpStatus.CREATED:HttpStatus.OK)
            .eTag("\""+sample.tracking().recordVersion()+"\"").body(SampleResponse.from(sample));
    }
    private Long parseVersion(String header) {
        if(header==null)return null;
        String value=header.strip();
        if(value.startsWith("\"")&&value.endsWith("\"")&&value.length()>1)value=value.substring(1,value.length()-1);
        if(!value.matches("[0-9]+"))throw new IllegalArgumentException("If-Match deve conter uma versão inteira, por exemplo \"1\".");
        try { return Long.parseLong(value); } catch(NumberFormatException e) { throw new IllegalArgumentException("Versão fora do limite."); }
    }

    @GetMapping("/{id}/interpretation")
    public SoilReportResponse interpretation(@PathVariable UUID id) {
        var sample = sampleService.findById(id);
        if (sample.analysis() == null) {
            throw new AnalysisNotFoundException(id);
        }
        return interpretationService.analyze(sample.analysis(), sample.crop());
    }
}
