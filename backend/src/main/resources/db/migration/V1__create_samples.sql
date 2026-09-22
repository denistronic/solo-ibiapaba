CREATE TABLE sample_sequences (
    year INTEGER PRIMARY KEY,
    last_value INTEGER NOT NULL CHECK (last_value > 0)
);

CREATE TABLE samples (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    block VARCHAR(80) NOT NULL,
    plot VARCHAR(80) NOT NULL,
    row_name VARCHAR(80) NOT NULL,
    crop VARCHAR(120) NOT NULL,
    sampled_at DATE NOT NULL,
    temperature NUMERIC(7, 2) NOT NULL,
    depth VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    analysis_updated_at TIMESTAMPTZ,
    ph_water NUMERIC(8, 4),
    ph_cacl2 NUMERIC(8, 4),
    organic_matter NUMERIC(16, 6),
    phosphorus NUMERIC(16, 6),
    potassium NUMERIC(16, 6),
    sodium NUMERIC(16, 6),
    calcium NUMERIC(16, 6),
    magnesium NUMERIC(16, 6),
    aluminum NUMERIC(16, 6),
    h_al NUMERIC(16, 6),
    sulfur NUMERIC(16, 6),
    boron NUMERIC(16, 6),
    copper NUMERIC(16, 6),
    iron NUMERIC(16, 6),
    manganese NUMERIC(16, 6),
    zinc NUMERIC(16, 6),
    cec_reported NUMERIC(16, 6),
    base_sat_reported NUMERIC(16, 6),
    clay NUMERIC(16, 6),
    sand NUMERIC(16, 6),
    silt NUMERIC(16, 6),
    ec NUMERIC(16, 6)
);

CREATE INDEX idx_samples_created_at ON samples (created_at DESC);
CREATE INDEX idx_samples_sampled_at ON samples (sampled_at DESC);
