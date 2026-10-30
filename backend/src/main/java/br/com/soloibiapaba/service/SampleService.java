package br.com.soloibiapaba.service;
import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.dto.*;
import br.com.soloibiapaba.repository.*;
import br.com.soloibiapaba.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class SampleService {
    private final SampleRepository repository;
    private final Clock clock=Clock.system(ZoneId.of("America/Fortaleza"));
    private final InterpretationParameterService parameters;
    private final SampleActorService actors;
    private final SampleAuditRepository events;
    private final ObjectMapper json;
    // Somente construção direta nos testes isolados de cálculo/validação.
    // O bean Spring sempre utiliza o construtor completo abaixo.
    public SampleService(SampleRepository repository) { this(repository,null); }
    public SampleService(SampleRepository repository,InterpretationParameterService parameters) {
        this(repository,parameters,null,null,new ObjectMapper());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public SampleService(SampleRepository repository,InterpretationParameterService parameters,
            SampleActorService actors,SampleAuditRepository events,ObjectMapper json) {
        this.repository=repository;this.parameters=parameters;this.actors=actors;this.events=events;this.json=json;
    }
    public List<Sample> findAll() { return repository.findAll(); }
    public Sample findById(UUID id) { return repository.findById(id).orElseThrow(()->new SampleNotFoundException(id)); }
    private Sample locked(UUID id) { return repository.findByIdForUpdate(id).orElseThrow(()->new SampleNotFoundException(id)); }

    @Transactional
    public Sample create(CreateSampleRequest request) {
        AuditActor actor=actors==null?null:actors.current();
        String crop=culture(request.crop());
        int year=clock.instant().atZone(clock.getZone()).getYear();
        String code="AMO-%d-%04d".formatted(year,repository.nextSequenceForYear(year));
        var sample=new Sample(UUID.randomUUID(),code,request.serviceOrderNumber(),normalize(request.block()),normalize(request.plot()),
            normalize(request.row()),crop,request.sampledAt(),request.temperature(),normalize(request.depth()),Instant.now(clock),null,null,normalize(request.observations()));
        if(actor!=null)sample=sample.withTracking(sample.tracking().created(actor.id()));
        sample=repository.save(sample);
        if(actor!=null)append(sample,actor,"SAMPLE","CREATE",null,SampleAuditSnapshots.details(sample,json),sample.createdAt());
        return sample;
    }

    @Transactional
    public Sample updateDetails(UUID id,CreateSampleRequest request,Long expectedVersion) {
        AuditActor actor=actors.requireAdmin();
        Sample current=locked(id);
        version(current,expectedVersion,true);
        var updated=new Sample(current.id(),current.code(),request.serviceOrderNumber(),normalize(request.block()),normalize(request.plot()),
            normalize(request.row()),culture(request.crop()),request.sampledAt(),request.temperature(),normalize(request.depth()),current.createdAt(),
            current.analysis(),current.analysisUpdatedAt(),normalize(request.observations()),current.tracking());
        var before=SampleAuditSnapshots.details(current,json);var after=SampleAuditSnapshots.details(updated,json);
        if(before.equals(after))return current;
        Instant at=Instant.now(clock);
        updated=repository.save(updated.withTracking(current.tracking().details(actor.id(),at)));
        append(updated,actor,"SAMPLE","UPDATE",before,after,at);
        return updated;
    }

    @Transactional
    public Sample updateAnalysis(UUID id,SoilAnalysisRequest request) { return writeAnalysis(id,request,null,false); }
    @Transactional
    public Sample updateAnalysis(UUID id,SoilAnalysisRequest request,Long expectedVersion,boolean firstOnly) {
        return writeAnalysis(id,request,expectedVersion,firstOnly);
    }
    private Sample writeAnalysis(UUID id,SoilAnalysisRequest request,Long expectedVersion,boolean firstOnly) {
        AuditActor actor=actors==null?null:actors.current();
        Sample current=actor==null?findById(id):locked(id);
        boolean first=current.analysisUpdatedAt()==null;
        if(actor!=null) {
            if(firstOnly&&!first)throw new SampleConflictException("O primeiro laudo já foi registrado.");
            // Pedido baseado em versão antiga é conflito, inclusive duas primeiras gravações simultâneas.
            version(current,expectedVersion,false);
            if(!first&&!actor.admin())throw new AccessDeniedException("Somente administrador pode alterar um laudo já registrado.");
            if(!first)version(current,expectedVersion,true);
        }
        SoilAnalysis analysis=toDomain(request,current.analysis());
        var before=SampleAuditSnapshots.analysis(current.analysis(),json);
        var after=SampleAuditSnapshots.analysis(analysis,json);
        if(!first&&Objects.equals(before,after))return current;
        Instant at=Instant.now(clock);
        Sample updated=current.withAnalysis(analysis,at);
        if(actor!=null)updated=updated.withTracking(current.tracking().analysis(actor.id(),at,first));
        updated=repository.save(updated);
        if(actor!=null)append(updated,actor,"ANALYSIS",first?"CREATE":"UPDATE",first?null:before,after,at);
        return updated;
    }
    private void version(Sample current,Long expected,boolean required) {
        if(expected==null) { if(required)throw new MissingRecordVersionException();return; }
        if(expected<0)throw new IllegalArgumentException("Versão não pode ser negativa.");
        if(current.tracking().recordVersion()!=expected)throw new SampleConflictException("Versão desatualizada.");
    }
    private void append(Sample sample,AuditActor actor,String entity,String action,JsonNode before,JsonNode after,Instant at) {
        events.append(new SampleAuditEvent(UUID.randomUUID(),sample.id(),entity,action,sample.tracking().recordVersion(),
            actor.id(),actor.name(),actor.email(),actor.role().name(),at,before,after));
    }
    private String culture(String value) { return parameters==null?normalize(value):parameters.requireCulture(value); }
    private SoilAnalysis toDomain(SoilAnalysisRequest request, SoilAnalysis previous) {
        // Preserva pH em água informado no histórico, sem inferir CaCl2.
        // Se o CaCl2 anterior existia e foi apagado, seu pH calculado também é limpo.
        BigDecimal phWater = request.phCaCl2() != null
                ? request.phCaCl2().add(new BigDecimal("0.6"))
                : previous != null && previous.phCaCl2() == null ? previous.phWater() : null;

        SoilAnalysis analysis = new SoilAnalysis(
                phWater,
                request.phCaCl2(),
                request.organicMatter(),
                request.organicCarbon(),
                request.phosphorus(),
                request.potassium(),
                request.sodium(),
                request.calcium(),
                request.magnesium(),
                request.aluminum(),
                request.hAl(),
                request.sulfur(),
                request.boron(),
                request.copper(),
                request.iron(),
                request.manganese(),
                request.zinc(),
                request.cecReported(),
                request.baseSatReported(),
                request.clay(),
                request.sand(),
                request.silt(),
                SoilUnits.effectiveUnits(request.units()),
                request.density(),
                request.phosphorusMethod() == null ? "Não informado" : request.phosphorusMethod()
        );
        SoilUnits.normalize(analysis);
        return analysis;
    }

    private String normalize(String value) { return value==null?null:value.strip(); }
}
