CREATE TABLE IF NOT EXISTS waste_plastic (
  id UUID PRIMARY KEY,
  weight_kg NUMERIC(12, 3) NOT NULL CHECK (weight_kg > 0),
  created_at TIMESTAMPTZ NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS waste_glass (
  id UUID PRIMARY KEY,
  weight_kg NUMERIC(12, 3) NOT NULL CHECK (weight_kg > 0),
  created_at TIMESTAMPTZ NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS waste_paper (
  id UUID PRIMARY KEY,
  weight_kg NUMERIC(12, 3) NOT NULL CHECK (weight_kg > 0),
  created_at TIMESTAMPTZ NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS waste_other (
  id UUID PRIMARY KEY,
  weight_kg NUMERIC(12, 3) NOT NULL CHECK (weight_kg > 0),
  created_at TIMESTAMPTZ NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS waste_weight_snapshots (
  id UUID PRIMARY KEY,
  captured_at TIMESTAMPTZ NOT NULL,
  plastic_weight NUMERIC(14, 3) NOT NULL,
  glass_weight NUMERIC(14, 3) NOT NULL,
  paper_weight NUMERIC(14, 3) NOT NULL,
  other_weight NUMERIC(14, 3) NOT NULL,
  total_weight NUMERIC(14, 3) NOT NULL,
  totals JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_waste_plastic_created_at ON waste_plastic (created_at);

CREATE INDEX IF NOT EXISTS idx_waste_glass_created_at ON waste_glass (created_at);

CREATE INDEX IF NOT EXISTS idx_waste_paper_created_at ON waste_paper (created_at);

CREATE INDEX IF NOT EXISTS idx_waste_other_created_at ON waste_other (created_at);

CREATE INDEX IF NOT EXISTS idx_waste_weight_snapshots_captured_at ON waste_weight_snapshots (captured_at DESC);
