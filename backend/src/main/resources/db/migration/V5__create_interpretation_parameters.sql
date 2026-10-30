CREATE TABLE interpretation_parameters (
    id UUID PRIMARY KEY,
    crop VARCHAR(120) NOT NULL,
    indicator VARCHAR(120) NOT NULL,
    minimum NUMERIC(24,10) NOT NULL,
    maximum NUMERIC(24,10) NOT NULL,
    CONSTRAINT uq_crop_indicator UNIQUE (crop, indicator),
    CONSTRAINT fk_parameters_culture FOREIGN KEY (crop)
        REFERENCES interpretation_cultures (normalized_name),
    CONSTRAINT ck_parameters_indicator CHECK (length(btrim(indicator)) > 0),
    CONSTRAINT ck_interpretation_range CHECK (
        minimum >= 0 AND maximum >= minimum AND maximum < 'Infinity'::numeric
    )
);

COMMENT ON COLUMN interpretation_parameters.crop IS 'Chave normalized_name do catálogo de culturas; não é o nome exibido.';
COMMENT ON TABLE interpretation_parameters IS 'Faixas por cultura nas unidades padrão dos indicadores; sem valores iniciais automáticos.';
