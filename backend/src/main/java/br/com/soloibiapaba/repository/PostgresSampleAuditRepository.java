package br.com.soloibiapaba.repository;
import br.com.soloibiapaba.domain.SampleAuditEvent;
import br.com.soloibiapaba.dto.*;
import java.util.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
@Repository
@Profile("!test")
public class PostgresSampleAuditRepository implements SampleAuditRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public PostgresSampleAuditRepository(JdbcClient jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    @Override public void append(SampleAuditEvent e) {
        jdbc.sql("""
            INSERT INTO sample_audit_events(id,sample_id,entity_type,action,record_version,actor_user_id,
                actor_name,actor_email,actor_role,occurred_at,before_data,after_data)
            VALUES(:id,:sample,:entity,:action,:version,:actor,:name,:email,:role,:at,CAST(:before AS jsonb),CAST(:after AS jsonb))
            """)
            .param("id",e.id()).param("sample",e.sampleId()).param("entity",e.entityType()).param("action",e.action())
            .param("version",e.recordVersion()).param("actor",e.actorUserId()).param("name",e.actorName())
            .param("email",e.actorEmail()).param("role",e.actorRole()).param("at",Timestamp.from(e.occurredAt()))
            .param("before",e.beforeData()==null?null:json.writeValueAsString(e.beforeData()))
            .param("after",json.writeValueAsString(e.afterData())).update();
    }
    @Override public AuditPage query(AuditFilter f) {
        var conditions=new ArrayList<String>();var params=new HashMap<String,Object>();
        add(conditions,params,"sample_id = :sampleId","sampleId",f.sampleId());
        add(conditions,params,"actor_user_id = :actorId","actorId",f.actorId());
        add(conditions,params,"entity_type = :entity","entity",f.entityType());
        add(conditions,params,"action = :action","action",f.action());
        add(conditions,params,"occurred_at >= :fromTime","fromTime",f.from()==null?null:Timestamp.from(f.from()));
        add(conditions,params,"occurred_at <= :toTime","toTime",f.to()==null?null:Timestamp.from(f.to()));
        String where=conditions.isEmpty()?"":" WHERE "+String.join(" AND ",conditions);
        long total=jdbc.sql("SELECT count(*) FROM sample_audit_events"+where).params(params).query(Long.class).single();
        params.put("limit",f.size());params.put("offset",(long)f.page()*f.size());
        var events=jdbc.sql("SELECT * FROM sample_audit_events"+where+" ORDER BY occurred_at DESC, record_version DESC, id DESC LIMIT :limit OFFSET :offset")
            .params(params).query((rs,row)->new SampleAuditEvent(rs.getObject("id",UUID.class),rs.getObject("sample_id",UUID.class),
                rs.getString("entity_type"),rs.getString("action"),rs.getLong("record_version"),rs.getObject("actor_user_id",UUID.class),
                rs.getString("actor_name"),rs.getString("actor_email"),rs.getString("actor_role"),rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("before_data")==null?null:json.readTree(rs.getString("before_data")),json.readTree(rs.getString("after_data")))).list();
        return new AuditPage(events,f.page(),f.size(),total,(long)(f.page()+1)*f.size()<total);
    }
    private void add(List<String> conditions,Map<String,Object> params,String sql,String key,Object value) {
        if(value!=null){conditions.add(sql);params.put(key,value);}
    }
}
