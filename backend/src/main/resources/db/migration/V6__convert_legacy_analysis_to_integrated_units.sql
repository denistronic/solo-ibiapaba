-- Não modificar V1–V5: V6 também atualiza bancos que já receberam a etapa 2 anterior.
-- A execução deve ser transacional pelo Flyway, com o backend antigo parado.
LOCK TABLE samples IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM samples WHERE analysis_schema_version = 1 AND (
            (organic_matter IS NOT NULL AND (units ->> 'organicMatter') IS DISTINCT FROM 'g/dm³') OR
            (phosphorus IS NOT NULL AND (units ->> 'phosphorus') IS DISTINCT FROM 'mg/dm³') OR
            (potassium IS NOT NULL AND (units ->> 'potassium') IS DISTINCT FROM 'mg/dm³') OR
            (sodium IS NOT NULL AND (units ->> 'sodium') IS DISTINCT FROM 'mg/dm³') OR
            (calcium IS NOT NULL AND (units ->> 'calcium') IS DISTINCT FROM 'cmolc/dm³') OR
            (magnesium IS NOT NULL AND (units ->> 'magnesium') IS DISTINCT FROM 'cmolc/dm³') OR
            (aluminum IS NOT NULL AND (units ->> 'aluminum') IS DISTINCT FROM 'cmolc/dm³') OR
            (h_al IS NOT NULL AND (units ->> 'hAl') IS DISTINCT FROM 'cmolc/dm³') OR
            (sulfur IS NOT NULL AND (units ->> 'sulfur') IS DISTINCT FROM 'mg/dm³') OR
            (boron IS NOT NULL AND (units ->> 'boron') IS DISTINCT FROM 'mg/dm³') OR
            (copper IS NOT NULL AND (units ->> 'copper') IS DISTINCT FROM 'mg/dm³') OR
            (iron IS NOT NULL AND (units ->> 'iron') IS DISTINCT FROM 'mg/dm³') OR
            (manganese IS NOT NULL AND (units ->> 'manganese') IS DISTINCT FROM 'mg/dm³') OR
            (zinc IS NOT NULL AND (units ->> 'zinc') IS DISTINCT FROM 'mg/dm³') OR
            (cec_reported IS NOT NULL AND (units ->> 'cecReported') IS DISTINCT FROM 'cmolc/dm³') OR
            (base_sat_reported IS NOT NULL AND (units ->> 'baseSatReported') IS DISTINCT FROM '%') OR
            (clay IS NOT NULL AND (units ->> 'clay') IS DISTINCT FROM '%') OR
            (sand IS NOT NULL AND (units ->> 'sand') IS DISTINCT FROM '%') OR
            (silt IS NOT NULL AND (units ->> 'silt') IS DISTINCT FROM '%') OR
            (ec IS NOT NULL AND (units ->> 'ec') IS DISTINCT FROM 'dS/m') OR
            organic_carbon IS NOT NULL OR density IS NOT NULL
    )) THEN
        RAISE EXCEPTION 'Laudo legado com unidades ou metadados não reconhecidos. Revise antes de converter; nenhum dado foi convertido.';
    END IF;
    IF EXISTS (SELECT 1 FROM samples WHERE analysis_schema_version = 1 AND (
            (organic_matter IS NOT NULL AND (organic_matter < 0 OR organic_matter >= 'Infinity'::numeric)) OR
            (organic_carbon IS NOT NULL AND (organic_carbon < 0 OR organic_carbon >= 'Infinity'::numeric)) OR
            (phosphorus IS NOT NULL AND (phosphorus < 0 OR phosphorus >= 'Infinity'::numeric)) OR
            (potassium IS NOT NULL AND (potassium < 0 OR potassium >= 'Infinity'::numeric)) OR
            (sodium IS NOT NULL AND (sodium < 0 OR sodium >= 'Infinity'::numeric)) OR
            (calcium IS NOT NULL AND (calcium < 0 OR calcium >= 'Infinity'::numeric)) OR
            (magnesium IS NOT NULL AND (magnesium < 0 OR magnesium >= 'Infinity'::numeric)) OR
            (aluminum IS NOT NULL AND (aluminum < 0 OR aluminum >= 'Infinity'::numeric)) OR
            (h_al IS NOT NULL AND (h_al < 0 OR h_al >= 'Infinity'::numeric)) OR
            (sulfur IS NOT NULL AND (sulfur < 0 OR sulfur >= 'Infinity'::numeric)) OR
            (boron IS NOT NULL AND (boron < 0 OR boron >= 'Infinity'::numeric)) OR
            (copper IS NOT NULL AND (copper < 0 OR copper >= 'Infinity'::numeric)) OR
            (iron IS NOT NULL AND (iron < 0 OR iron >= 'Infinity'::numeric)) OR
            (manganese IS NOT NULL AND (manganese < 0 OR manganese >= 'Infinity'::numeric)) OR
            (zinc IS NOT NULL AND (zinc < 0 OR zinc >= 'Infinity'::numeric)) OR
            (cec_reported IS NOT NULL AND (cec_reported < 0 OR cec_reported >= 'Infinity'::numeric)) OR
            (base_sat_reported IS NOT NULL AND (base_sat_reported < 0 OR base_sat_reported >= 'Infinity'::numeric)) OR
            (clay IS NOT NULL AND (clay < 0 OR clay >= 'Infinity'::numeric)) OR
            (sand IS NOT NULL AND (sand < 0 OR sand >= 'Infinity'::numeric)) OR
            (silt IS NOT NULL AND (silt < 0 OR silt >= 'Infinity'::numeric)) OR
            (ec IS NOT NULL AND (ec < 0 OR ec >= 'Infinity'::numeric)) OR
            clay > 100 OR sand > 100 OR silt > 100 OR
            (ph_cacl2 IS NOT NULL AND (ph_cacl2 < 0 OR ph_cacl2 > 14))
    )) THEN
        RAISE EXCEPTION 'Laudo legado com valor inválido ou textura acima de 100 por cento. Revise antes de converter; nenhum dado foi convertido.';
    END IF;
END $$;

CREATE TABLE sample_analysis_legacy_archive (
    sample_id UUID PRIMARY KEY REFERENCES samples (id),
    original_record JSONB NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    migration_version VARCHAR(16) NOT NULL DEFAULT '6',
    CONSTRAINT ck_legacy_archive_object CHECK (jsonb_typeof(original_record) = 'object')
);
COMMENT ON TABLE sample_analysis_legacy_archive IS 'Cópia anterior à conversão V6; preserva valores e metadados originais para conferência, não para cálculos ativos.';

INSERT INTO sample_analysis_legacy_archive (sample_id, original_record)
SELECT id, to_jsonb(samples) FROM samples WHERE analysis_schema_version = 1;

-- Mesma aproximação do algoritmo legado: K / 391 e Na / 230 em cmolc/dm³,
-- multiplicados por 10 para mmolc/dm³. A conversão é arredondada a 10 casas pela coluna.
-- Multiplicações/divisões preservam NULL. Carbono não é inferido de matéria orgânica.
UPDATE samples SET
    phosphorus = phosphorus / 1000,
    potassium = potassium / 39.1,
    sodium = sodium / 23.0,
    calcium = calcium * 10,
    magnesium = magnesium * 10,
    aluminum = aluminum * 10,
    h_al = h_al * 10,
    sulfur = sulfur / 1000,
    boron = boron / 1000,
    copper = copper / 1000,
    iron = iron / 1000,
    manganese = manganese / 1000,
    zinc = zinc / 1000,
    cec_reported = cec_reported * 10,
    clay = clay * 10,
    sand = sand * 10,
    silt = silt * 10,
    ph_water = CASE WHEN ph_cacl2 IS NOT NULL THEN ph_cacl2 + 0.6 ELSE ph_water END,
    units = '{"organicMatter": "g/dm³", "organicCarbon": "g/dm³", "phosphorus": "g/dm³", "sulfur": "g/dm³", "boron": "g/dm³", "copper": "g/dm³", "iron": "g/dm³", "manganese": "g/dm³", "zinc": "g/dm³", "potassium": "mmolc/dm³", "sodium": "mmolc/dm³", "calcium": "mmolc/dm³", "magnesium": "mmolc/dm³", "aluminum": "mmolc/dm³", "hAl": "mmolc/dm³", "cecReported": "mmolc/dm³", "clay": "g/kg", "sand": "g/kg", "silt": "g/kg"}'::jsonb,
    analysis_schema_version = 2
WHERE analysis_schema_version = 1;

ALTER TABLE samples
    ALTER COLUMN units SET DEFAULT '{"organicMatter": "g/dm³", "organicCarbon": "g/dm³", "phosphorus": "g/dm³", "sulfur": "g/dm³", "boron": "g/dm³", "copper": "g/dm³", "iron": "g/dm³", "manganese": "g/dm³", "zinc": "g/dm³", "potassium": "mmolc/dm³", "sodium": "mmolc/dm³", "calcium": "mmolc/dm³", "magnesium": "mmolc/dm³", "aluminum": "mmolc/dm³", "hAl": "mmolc/dm³", "cecReported": "mmolc/dm³", "clay": "g/kg", "sand": "g/kg", "silt": "g/kg"}'::jsonb,
    ALTER COLUMN analysis_schema_version SET DEFAULT 2;

COMMENT ON COLUMN samples.units IS 'Unidades dos valores armazenados. V6 converte registros legados às unidades padrão integradas; entradas futuras podem informar outras unidades suportadas pela API.';
COMMENT ON COLUMN samples.analysis_schema_version IS '2: contrato integrado; registros de versão 1 foram convertidos na V6 e arquivados antes da alteração.';
