-- Создание базы данных (выполнить отдельно)
-- CREATE DATABASE lmia_db;

-- Создание таблицы (выполняется автоматически через Hibernate ddl-auto=update)
-- Но можно использовать для ручного создания:

CREATE TABLE IF NOT EXISTS lmia_datasets (
    id BIGSERIAL PRIMARY KEY,
    province VARCHAR(255) NOT NULL,
    stream VARCHAR(255) NOT NULL,
    employer VARCHAR(500) NOT NULL,
    city VARCHAR(200),
    postal_code VARCHAR(20),
    noc_code VARCHAR(10) NOT NULL,
    noc_title VARCHAR(500),
    positions_approved INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    decision_date DATE NOT NULL,
    source_file VARCHAR(50)
);

-- Создание индексов для оптимизации поиска
CREATE INDEX IF NOT EXISTS idx_employer ON lmia_datasets(employer);
CREATE INDEX IF NOT EXISTS idx_noc ON lmia_datasets(noc_code);
CREATE INDEX IF NOT EXISTS idx_province ON lmia_datasets(province);
CREATE INDEX IF NOT EXISTS idx_date ON lmia_datasets(decision_date);
CREATE INDEX IF NOT EXISTS idx_status ON lmia_datasets(status);
CREATE INDEX IF NOT EXISTS idx_employer_lower ON lmia_datasets(LOWER(employer));

