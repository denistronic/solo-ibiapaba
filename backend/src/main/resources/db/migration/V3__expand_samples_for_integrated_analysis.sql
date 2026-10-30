-- Expansão compatível com o backend do backup: nenhuma coluna antiga é removida.
-- Valores históricos permanecem na escala original; a conversão pertence ao backend.
ALTER TABLE samples
    ADD COLUMN service_order_number VARCHAR(8),
    ADD COLUMN observations TEXT,
    ADD COLUMN organic_carbon NUMERIC(24,10),
    ADD COLUMN units JSONB NOT NULL DEFAULT '{"organicMatter": "g/dm³", "phosphorus": "mg/dm³", "potassium": "mg/dm³", "sodium": "mg/dm³", "calcium": "cmolc/dm³", "magnesium": "cmolc/dm³", "aluminum": "cmolc/dm³", "hAl": "cmolc/dm³", "sulfur": "mg/dm³", "boron": "mg/dm³", "copper": "mg/dm³", "iron": "mg/dm³", "manganese": "mg/dm³", "zinc": "mg/dm³", "cecReported": "cmolc/dm³", "baseSatReported": "%", "clay": "%", "sand": "%", "silt": "%", "ec": "dS/m"}'::jsonb,
    ADD COLUMN density NUMERIC(24,10),
    ADD COLUMN phosphorus_method VARCHAR(20) NOT NULL DEFAULT 'Não informado',
    ADD COLUMN analysis_schema_version SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_samples_service_order CHECK (
        service_order_number IS NULL OR service_order_number ~ '^[0-9]{8}$'
    ),
    ADD CONSTRAINT ck_samples_organic_carbon CHECK (
        organic_carbon IS NULL OR (organic_carbon >= 0 AND organic_carbon < 'Infinity'::numeric)
    ),
    ADD CONSTRAINT ck_samples_units_object CHECK (jsonb_typeof(units) = 'object'),
    ADD CONSTRAINT ck_samples_density CHECK (
        density IS NULL OR (density > 0 AND density < 'Infinity'::numeric)
    ),
    ADD CONSTRAINT ck_samples_phosphorus_method CHECK (
        phosphorus_method IN ('Resina', 'Mehlich 1', 'Não informado')
    ),
    ADD CONSTRAINT ck_samples_analysis_schema_version CHECK (analysis_schema_version IN (1, 2));

-- O default legado permite que o backend antigo continue inserindo amostras nesta etapa.
-- O backend integrado deve enviar units e analysis_schema_version=2 explicitamente.
-- OS permanece nula nos registros históricos, sem inventar um número retroativo.
COMMENT ON COLUMN samples.units IS 'Unidades dos valores originais por chave de campo; não são valores normalizados.';
COMMENT ON COLUMN samples.analysis_schema_version IS '1: formato do backup; 2: formato integrado. Nunca reinterpretar valores legados com defaults novos.';
COMMENT ON COLUMN samples.density IS 'Densidade de conversão em kg/dm³; necessária para converter resultados por massa quando aplicável.';
COMMENT ON COLUMN samples.phosphorus_method IS 'Método informado no laudo; método histórico desconhecido não é presumido como Resina.';
COMMENT ON COLUMN samples.ec IS 'Campo legado preservado; não faz parte dos novos indicadores.';

-- Mais casas decimais evitam perda de resultados pequenos nas novas unidades.
-- O aumento de precisão/escala preserva todos os valores representáveis na definição antiga.
ALTER TABLE samples
    ALTER COLUMN organic_matter TYPE NUMERIC(24,10),
    ALTER COLUMN phosphorus TYPE NUMERIC(24,10),
    ALTER COLUMN potassium TYPE NUMERIC(24,10),
    ALTER COLUMN sodium TYPE NUMERIC(24,10),
    ALTER COLUMN calcium TYPE NUMERIC(24,10),
    ALTER COLUMN magnesium TYPE NUMERIC(24,10),
    ALTER COLUMN aluminum TYPE NUMERIC(24,10),
    ALTER COLUMN h_al TYPE NUMERIC(24,10),
    ALTER COLUMN sulfur TYPE NUMERIC(24,10),
    ALTER COLUMN boron TYPE NUMERIC(24,10),
    ALTER COLUMN copper TYPE NUMERIC(24,10),
    ALTER COLUMN iron TYPE NUMERIC(24,10),
    ALTER COLUMN manganese TYPE NUMERIC(24,10),
    ALTER COLUMN zinc TYPE NUMERIC(24,10),
    ALTER COLUMN cec_reported TYPE NUMERIC(24,10),
    ALTER COLUMN base_sat_reported TYPE NUMERIC(24,10),
    ALTER COLUMN clay TYPE NUMERIC(24,10),
    ALTER COLUMN sand TYPE NUMERIC(24,10),
    ALTER COLUMN silt TYPE NUMERIC(24,10);

CREATE INDEX idx_samples_service_order ON samples (service_order_number)
    WHERE service_order_number IS NOT NULL;
