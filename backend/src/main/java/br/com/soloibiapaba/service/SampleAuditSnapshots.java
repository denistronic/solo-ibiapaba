package br.com.soloibiapaba.service;
import br.com.soloibiapaba.domain.*;
import java.util.*;
import java.math.BigDecimal;
import tools.jackson.databind.*;
/** Lista explícita de campos de negócio; nenhuma entidade User é serializada. */
public final class SampleAuditSnapshots {
    private SampleAuditSnapshots() {}
    public static JsonNode details(Sample s,ObjectMapper json) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("id",s.id().toString());data.put("code",s.code());data.put("serviceOrderNumber",s.serviceOrderNumber());
        data.put("block",s.block());data.put("plot",s.plot());data.put("row",s.row());data.put("crop",s.crop());
        data.put("sampledAt",s.sampledAt().toString());data.put("temperature",decimal(s.temperature()));
        data.put("depth",s.depth());data.put("observations",s.observations());
        return json.valueToTree(data);
    }
    public static JsonNode analysis(SoilAnalysis a,ObjectMapper json) {
        if(a==null)return null;
        Map<String,Object> data=new TreeMap<>();
        for(var field:SoilAnalysis.class.getRecordComponents()) {
            try {
                Object value=field.getAccessor().invoke(a);
                if(value instanceof BigDecimal n)value=decimal(n);
                data.put(field.getName(),value);
            } catch(ReflectiveOperationException error) { throw new IllegalStateException("Falha ao montar snapshot do laudo",error); }
        }
        data.put("units",new TreeMap<>(SoilUnits.effectiveUnits(a.units())));
        return json.valueToTree(data);
    }
    private static String decimal(BigDecimal n) { return n==null?null:n.stripTrailingZeros().toPlainString(); }
}
