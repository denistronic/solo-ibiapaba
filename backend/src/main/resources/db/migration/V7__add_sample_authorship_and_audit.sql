-- Expansão compatível com o backend entregue antes das regras de autoria.
-- Não inventa autores, primeira data de laudo ou eventos para registros históricos.
ALTER TABLE samples
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN updated_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN analysis_created_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN analysis_created_at TIMESTAMPTZ,
    ADD COLUMN analysis_updated_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_samples_record_version CHECK (record_version >= 0);

COMMENT ON COLUMN samples.created_by IS 'Autor da criação da amostra. NULL = autoria não registrada; nunca inferir a partir de administrador atual.';
COMMENT ON COLUMN samples.updated_by IS 'Responsável pela última edição cadastral da amostra; NULL até edição rastreada.';
COMMENT ON COLUMN samples.updated_at IS 'Última edição cadastral rastreada, separada de analysis_updated_at.';
COMMENT ON COLUMN samples.analysis_created_by IS 'Responsável pelo primeiro registro rastreado do laudo; NULL no legado desconhecido.';
COMMENT ON COLUMN samples.analysis_created_at IS 'Primeiro salvamento rastreado do laudo; não inferir da última edição histórica.';
COMMENT ON COLUMN samples.analysis_updated_by IS 'Responsável pelo último salvamento rastreado do laudo.';
COMMENT ON COLUMN samples.record_version IS 'Versão compartilhada de amostra/laudo para controle transacional na etapa backend. Legado ou escrita anterior à integração de auditoria começa em 0.';

CREATE TABLE sample_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_id UUID NOT NULL REFERENCES samples(id) ON DELETE RESTRICT,
    entity_type VARCHAR(12) NOT NULL,
    action VARCHAR(6) NOT NULL,
    record_version BIGINT NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_name VARCHAR(120) NOT NULL,
    actor_email VARCHAR(180) NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    before_data JSONB,
    after_data JSONB NOT NULL,
    CONSTRAINT ck_sample_audit_entity CHECK (entity_type IN ('SAMPLE','ANALYSIS')),
    CONSTRAINT ck_sample_audit_action CHECK (action IN ('CREATE','UPDATE')),
    CONSTRAINT ck_sample_audit_version CHECK (record_version > 0),
    CONSTRAINT ck_sample_audit_actor_name CHECK (length(btrim(actor_name)) > 0),
    CONSTRAINT ck_sample_audit_actor_email CHECK (length(btrim(actor_email)) > 0),
    CONSTRAINT ck_sample_audit_actor_role CHECK (actor_role IN ('USER','ADMIN')),
    CONSTRAINT ck_sample_audit_update_admin CHECK (action = 'CREATE' OR actor_role = 'ADMIN'),
    CONSTRAINT ck_sample_audit_after_object CHECK (jsonb_typeof(after_data) = 'object'),
    CONSTRAINT ck_sample_audit_before_action CHECK (
        (action = 'CREATE' AND before_data IS NULL) OR
        (action = 'UPDATE' AND before_data IS NOT NULL AND jsonb_typeof(before_data) = 'object')
    ),
    CONSTRAINT ck_sample_audit_change CHECK (action <> 'UPDATE' OR before_data <> after_data),
    CONSTRAINT uq_sample_audit_version UNIQUE (sample_id, record_version)
);

CREATE UNIQUE INDEX uq_sample_audit_creation
    ON sample_audit_events (sample_id, entity_type) WHERE action = 'CREATE';
CREATE INDEX idx_sample_audit_sample_time ON sample_audit_events (sample_id, occurred_at DESC, id);
CREATE INDEX idx_sample_audit_actor_time ON sample_audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_sample_audit_time ON sample_audit_events (occurred_at DESC);
CREATE INDEX idx_sample_audit_entity_action_time ON sample_audit_events (entity_type, action, occurred_at DESC);

COMMENT ON TABLE sample_audit_events IS 'Histórico de criações/edições de amostras e laudos. Inserido atomicamente pelo backend a partir da etapa 3; não é gerado automaticamente pela V7.';
COMMENT ON COLUMN sample_audit_events.actor_name IS 'Nome no momento da ação; preservado após renomear a conta.';
COMMENT ON COLUMN sample_audit_events.actor_email IS 'E-mail no momento da ação; preservado após alterar a conta.';
COMMENT ON COLUMN sample_audit_events.actor_role IS 'Perfil autenticado verificado pelo backend no momento da ação; não confiar no payload do cliente.';
COMMENT ON COLUMN sample_audit_events.before_data IS 'Snapshot de negócio anterior, sem segredos; SQL NULL na criação.';
COMMENT ON COLUMN sample_audit_events.after_data IS 'Snapshot de negócio posterior; nunca incluir senha, hash de senha, JWT ou cabeçalhos de autenticação.';
COMMENT ON COLUMN sample_audit_events.record_version IS 'Versão resultante da alteração na linha samples; vínculo e incremento são responsabilidade transacional do backend.';

CREATE FUNCTION reject_sample_audit_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'O histórico de auditoria não permite alteração, exclusão ou truncamento.'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_sample_audit_immutable_rows
    BEFORE UPDATE OR DELETE ON sample_audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_sample_audit_mutation();
CREATE TRIGGER trg_sample_audit_no_truncate
    BEFORE TRUNCATE ON sample_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_sample_audit_mutation();
