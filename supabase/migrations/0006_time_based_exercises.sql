-- Bazı hareketlerde tekrar sayısı değil süre girilir (plank, wall sit, kardiyo).
-- Antrenman kaydı ekranı hangi alanı göstereceğine buna göre karar veriyor.

alter table public.exercises
  add column if not exists is_time_based boolean not null default false;

update public.exercises set is_time_based = true where id in ('plank');

-- Aktif antrenmanı (bitmemiş olanı) hızlı bulmak için.
create index if not exists workouts_active_idx
  on public.workouts (user_id)
  where finished_at is null;
