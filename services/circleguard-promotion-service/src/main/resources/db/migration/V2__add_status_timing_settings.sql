-- Crear tabla si no existe (faltó en V1)
CREATE TABLE IF NOT EXISTS system_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE system_settings
ADD COLUMN IF NOT EXISTS mandatory_fence_days INTEGER NOT NULL DEFAULT 14,
ADD COLUMN IF NOT EXISTS encounter_window_days INTEGER NOT NULL DEFAULT 14;

-- Seed initial row if empty
INSERT INTO system_settings (mandatory_fence_days, encounter_window_days)
SELECT 14, 14
WHERE NOT EXISTS (SELECT 1 FROM system_settings);
