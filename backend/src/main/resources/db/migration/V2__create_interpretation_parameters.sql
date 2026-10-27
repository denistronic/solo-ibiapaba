CREATE TABLE interpretation_parameters (
    id UUID PRIMARY KEY,
    crop VARCHAR(120) NOT NULL,
    indicator VARCHAR(120) NOT NULL,
    minimum NUMERIC(24,10) NOT NULL,
    maximum NUMERIC(24,10) NOT NULL,
    CONSTRAINT uq_crop_indicator UNIQUE (crop, indicator),
    CONSTRAINT ck_interpretation_range CHECK (minimum >= 0 AND maximum >= minimum)
);
