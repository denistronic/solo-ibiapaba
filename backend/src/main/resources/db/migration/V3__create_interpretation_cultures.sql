CREATE TABLE interpretation_cultures (
    normalized_name VARCHAR(120) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL
);

-- Registra as culturas existentes nos parâmetros antes de separar o catálogo.
INSERT INTO interpretation_cultures (normalized_name, display_name)
SELECT DISTINCT crop, UPPER(SUBSTRING(crop, 1, 1)) || SUBSTRING(crop, 2)
FROM interpretation_parameters;
