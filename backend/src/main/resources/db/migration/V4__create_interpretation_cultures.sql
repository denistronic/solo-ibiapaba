-- Catálogo separado das amostras; nenhuma faixa agronômica inicial é presumida.
CREATE TABLE interpretation_cultures (
    normalized_name VARCHAR(120) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    CONSTRAINT ck_cultures_normalized_name CHECK (length(btrim(normalized_name)) > 0),
    CONSTRAINT ck_cultures_display_name CHECK (length(btrim(display_name)) > 0)
);

COMMENT ON COLUMN interpretation_cultures.normalized_name IS 'Chave normalizada pela aplicação: strip, remoção de acentos e minúsculas.';
-- Não relacionar samples.crop a esta chave: o campo histórico contém o nome exibido.
