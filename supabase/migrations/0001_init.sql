-- Repzy — temel şema, RLS ve onboarding RPC'si.
-- Supabase SQL Editor'da sırayla çalıştırılır: 0001 → 0002 → 0003.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Enum'lar. Android tarafındaki com.repzy.app.data.model.Enums.kt ile birebir aynı.
-- ---------------------------------------------------------------------------
create type public.sex as enum ('male', 'female');

create type public.fitness_goal as enum ('lose_fat', 'build_muscle', 'endurance', 'general_fitness');

create type public.experience_level as enum ('beginner', 'intermediate', 'advanced');

create type public.equipment_access as enum ('gym', 'home', 'both');

create type public.activity_level as enum ('sedentary', 'light', 'moderate', 'active', 'very_active');

create type public.unit_system as enum ('metric', 'imperial');

create type public.body_fat_source as enum ('user', 'navy', 'device');

create type public.photo_pose as enum ('front', 'side', 'back');

create type public.meal_type as enum ('breakfast', 'lunch', 'dinner', 'snack');

create type public.food_log_source as enum ('ai_photo', 'manual', 'search', 'barcode');

-- ---------------------------------------------------------------------------
-- updated_at otomatiği
-- ---------------------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- profiles
-- Doğum tarihi değil doğum YILI tutulur (veri minimizasyonu).
-- BMI saklanmaz — kilo/boydan türetilir.
-- ---------------------------------------------------------------------------
create table public.profiles (
  id                      uuid primary key references auth.users (id) on delete cascade,
  display_name            text,
  sex                     public.sex,
  birth_year              smallint check (birth_year between 1920 and 2020),
  height_cm               numeric(5, 1) check (height_cm between 100 and 250),
  goal                    public.fitness_goal,
  experience_level        public.experience_level,
  equipment_access        public.equipment_access,
  activity_level          public.activity_level,
  unit_system             public.unit_system not null default 'metric',
  locale                  text not null default 'tr',
  onboarding_completed_at timestamptz,
  -- KVKK Md. 6: sağlık verisi ve fotoğraf için AYRI açık rıza. Genel kullanım koşulu yeterli değil.
  health_data_consent_at  timestamptz,
  photo_consent_at        timestamptz,
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now()
);

create trigger profiles_touch_updated_at
  before update on public.profiles
  for each row execute function public.touch_updated_at();

-- Kayıt olan her kullanıcı için boş profil satırı açılır.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, new.raw_user_meta_data ->> 'display_name')
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- body_metrics — kilo/ölçü geçmişi, ilerleme grafiğinin kaynağı
-- ---------------------------------------------------------------------------
create table public.body_metrics (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users (id) on delete cascade,
  measured_on     date not null default current_date,
  weight_kg       numeric(5, 2) check (weight_kg between 20 and 400),
  body_fat_pct    numeric(4, 1) check (body_fat_pct between 3 and 70),
  muscle_mass_pct numeric(4, 1) check (muscle_mass_pct between 10 and 70),
  neck_cm         numeric(4, 1),
  waist_cm        numeric(4, 1),
  hip_cm          numeric(4, 1),
  chest_cm        numeric(4, 1),
  arm_cm          numeric(4, 1),
  thigh_cm        numeric(4, 1),
  body_fat_source public.body_fat_source,
  note            text,
  created_at      timestamptz not null default now(),
  -- Aynı gün ikinci ölçüm girilirse üzerine yazılır.
  unique (user_id, measured_on)
);

create index body_metrics_user_date_idx
  on public.body_metrics (user_id, measured_on desc);

-- ---------------------------------------------------------------------------
-- nutrition_targets — kalori/makro hedefi, tarihsel (adaptif güncelleme için)
-- ---------------------------------------------------------------------------
create table public.nutrition_targets (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users (id) on delete cascade,
  effective_from date not null default current_date,
  calories       integer not null check (calories between 800 and 6000),
  protein_g      integer not null check (protein_g >= 0),
  carbs_g        integer not null check (carbs_g >= 0),
  fat_g          integer not null check (fat_g >= 0),
  water_ml       integer not null check (water_ml between 500 and 8000),
  source         text not null default 'rule' check (source in ('rule', 'ai', 'user')),
  created_at     timestamptz not null default now(),
  unique (user_id, effective_from)
);

create index nutrition_targets_user_idx
  on public.nutrition_targets (user_id, effective_from desc);

-- ---------------------------------------------------------------------------
-- body_photos — dosya Storage'da, burada sadece yol tutulur
-- ---------------------------------------------------------------------------
create table public.body_photos (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users (id) on delete cascade,
  storage_path   text not null unique,
  pose           public.photo_pose not null,
  taken_on       date not null default current_date,
  body_metric_id uuid references public.body_metrics (id) on delete set null,
  created_at     timestamptz not null default now()
);

create index body_photos_user_idx on public.body_photos (user_id, taken_on desc);

-- ---------------------------------------------------------------------------
-- exercises — herkese açık okuma, kullanıcıya ait değil
-- ---------------------------------------------------------------------------
create table public.exercises (
  id                  text primary key,
  name_tr             text not null,
  name_en             text not null,
  primary_muscle      text not null,
  secondary_muscles   text[] not null default '{}',
  equipment           text not null,
  setting             public.equipment_access not null,
  level               public.experience_level not null,
  mechanic            text check (mechanic in ('compound', 'isolation')),
  instructions_tr     text[] not null default '{}',
  instructions_en     text[] not null default '{}',
  common_mistakes_tr  text[] not null default '{}',
  common_mistakes_en  text[] not null default '{}',
  image_url           text,
  animation_url       text,
  created_at          timestamptz not null default now()
);

create index exercises_muscle_idx on public.exercises (primary_muscle);
create index exercises_setting_level_idx on public.exercises (setting, level);

-- Gym <-> ev alternatifi eşleşmesi (çift yönlü değil, iki satır yazılır).
create table public.exercise_alternatives (
  exercise_id     text not null references public.exercises (id) on delete cascade,
  alternative_id  text not null references public.exercises (id) on delete cascade,
  reason          text,
  primary key (exercise_id, alternative_id),
  check (exercise_id <> alternative_id)
);

-- ---------------------------------------------------------------------------
-- workouts / workout_sets
-- ---------------------------------------------------------------------------
create table public.workouts (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users (id) on delete cascade,
  started_at       timestamptz not null default now(),
  finished_at      timestamptz,
  title            text,
  note             text,
  perceived_effort smallint check (perceived_effort between 1 and 10),
  created_at       timestamptz not null default now()
);

create index workouts_user_idx on public.workouts (user_id, started_at desc);

create table public.workout_sets (
  id           uuid primary key default gen_random_uuid(),
  workout_id   uuid not null references public.workouts (id) on delete cascade,
  user_id      uuid not null references auth.users (id) on delete cascade,
  exercise_id  text not null references public.exercises (id),
  set_index    smallint not null check (set_index > 0),
  reps         smallint check (reps between 0 and 500),
  weight_kg    numeric(6, 2) check (weight_kg >= 0),
  duration_sec integer check (duration_sec >= 0),
  distance_m   integer check (distance_m >= 0),
  is_warmup    boolean not null default false,
  completed_at timestamptz not null default now(),
  unique (workout_id, exercise_id, set_index)
);

create index workout_sets_workout_idx on public.workout_sets (workout_id);
create index workout_sets_user_exercise_idx on public.workout_sets (user_id, exercise_id, completed_at desc);

-- ---------------------------------------------------------------------------
-- food_logs / water_logs
-- ---------------------------------------------------------------------------
create table public.food_logs (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users (id) on delete cascade,
  log_date      date not null default current_date,
  logged_at     timestamptz not null default now(),
  meal          public.meal_type not null,
  name          text not null,
  serving_desc  text,
  grams         numeric(7, 1) check (grams >= 0),
  calories      numeric(7, 1) not null check (calories >= 0),
  protein_g     numeric(6, 1) not null default 0 check (protein_g >= 0),
  carbs_g       numeric(6, 1) not null default 0 check (carbs_g >= 0),
  fat_g         numeric(6, 1) not null default 0 check (fat_g >= 0),
  fiber_g       numeric(6, 1) check (fiber_g >= 0),
  source        public.food_log_source not null,
  ai_confidence numeric(3, 2) check (ai_confidence between 0 and 1),
  photo_path    text,
  created_at    timestamptz not null default now()
);

create index food_logs_user_date_idx on public.food_logs (user_id, log_date desc);

create table public.water_logs (
  id        uuid primary key default gen_random_uuid(),
  user_id   uuid not null references auth.users (id) on delete cascade,
  log_date  date not null default current_date,
  logged_at timestamptz not null default now(),
  amount_ml integer not null check (amount_ml between 1 and 5000)
);

create index water_logs_user_date_idx on public.water_logs (user_id, log_date desc);

-- ---------------------------------------------------------------------------
-- ai_usage — ücretsiz katmandaki tarama limitinin ve maliyetin kaynağı.
-- İstemci yazmaz; sadece Edge Function (service role) yazar.
-- ---------------------------------------------------------------------------
create table public.ai_usage (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users (id) on delete cascade,
  usage_date date not null default current_date,
  used_at    timestamptz not null default now(),
  kind       text not null check (kind in ('food_photo', 'chat', 'weekly_report')),
  model      text,
  tokens_in  integer,
  tokens_out integer,
  cost_usd   numeric(8, 5)
);

create index ai_usage_user_date_idx on public.ai_usage (user_id, usage_date desc);

-- ---------------------------------------------------------------------------
-- RLS — kullanıcı sadece kendi satırını görür
-- ---------------------------------------------------------------------------
alter table public.profiles            enable row level security;
alter table public.body_metrics        enable row level security;
alter table public.nutrition_targets   enable row level security;
alter table public.body_photos         enable row level security;
alter table public.workouts            enable row level security;
alter table public.workout_sets        enable row level security;
alter table public.food_logs           enable row level security;
alter table public.water_logs          enable row level security;
alter table public.ai_usage            enable row level security;
alter table public.exercises           enable row level security;
alter table public.exercise_alternatives enable row level security;

create policy profiles_select_own on public.profiles
  for select to authenticated using (auth.uid() = id);
create policy profiles_update_own on public.profiles
  for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);
create policy profiles_delete_own on public.profiles
  for delete to authenticated using (auth.uid() = id);

-- user_id kolonu olan tablolar için aynı kalıp
do $$
declare
  t text;
begin
  foreach t in array array[
    'body_metrics', 'nutrition_targets', 'body_photos',
    'workouts', 'workout_sets', 'food_logs', 'water_logs'
  ]
  loop
    execute format(
      'create policy %1$s_select_own on public.%1$s for select to authenticated using (auth.uid() = user_id)', t);
    execute format(
      'create policy %1$s_insert_own on public.%1$s for insert to authenticated with check (auth.uid() = user_id)', t);
    execute format(
      'create policy %1$s_update_own on public.%1$s for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id)', t);
    execute format(
      'create policy %1$s_delete_own on public.%1$s for delete to authenticated using (auth.uid() = user_id)', t);
  end loop;
end;
$$;

-- ai_usage: kullanıcı kendi kullanımını GÖRÜR ama yazamaz (limit atlatılamasın).
create policy ai_usage_select_own on public.ai_usage
  for select to authenticated using (auth.uid() = user_id);

-- Egzersiz kütüphanesi: giriş yapan herkes okur, kimse yazmaz.
create policy exercises_read_all on public.exercises
  for select to authenticated using (true);
create policy exercise_alternatives_read_all on public.exercise_alternatives
  for select to authenticated using (true);

-- ---------------------------------------------------------------------------
-- complete_onboarding — profil + ilk ölçüm + beslenme hedefi tek işlemde.
-- Biri patlarsa hiçbiri yazılmaz, onboarding "tamamlandı" işaretlenmez.
-- ---------------------------------------------------------------------------
create or replace function public.complete_onboarding(p jsonb)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;

  update public.profiles set
    display_name            = nullif(p ->> 'display_name', ''),
    sex                     = (p ->> 'sex')::public.sex,
    birth_year              = (p ->> 'birth_year')::smallint,
    height_cm               = (p ->> 'height_cm')::numeric,
    goal                    = (p ->> 'goal')::public.fitness_goal,
    experience_level        = (p ->> 'experience_level')::public.experience_level,
    equipment_access        = (p ->> 'equipment_access')::public.equipment_access,
    activity_level          = (p ->> 'activity_level')::public.activity_level,
    locale                  = coalesce(p ->> 'locale', locale),
    health_data_consent_at  = coalesce(health_data_consent_at, now()),
    onboarding_completed_at = now()
  where id = uid;

  if not found then
    raise exception 'profile row missing for user %', uid;
  end if;

  insert into public.body_metrics (
    user_id, measured_on, weight_kg, body_fat_pct, body_fat_source, neck_cm, waist_cm, hip_cm
  )
  values (
    uid,
    coalesce((p ->> 'measured_on')::date, current_date),
    (p ->> 'weight_kg')::numeric,
    (p ->> 'body_fat_pct')::numeric,
    (p ->> 'body_fat_source')::public.body_fat_source,
    (p ->> 'neck_cm')::numeric,
    (p ->> 'waist_cm')::numeric,
    (p ->> 'hip_cm')::numeric
  )
  on conflict (user_id, measured_on) do update set
    weight_kg       = excluded.weight_kg,
    body_fat_pct    = excluded.body_fat_pct,
    body_fat_source = excluded.body_fat_source,
    neck_cm         = excluded.neck_cm,
    waist_cm        = excluded.waist_cm,
    hip_cm          = excluded.hip_cm;

  insert into public.nutrition_targets (
    user_id, effective_from, calories, protein_g, carbs_g, fat_g, water_ml, source
  )
  values (
    uid,
    current_date,
    (p ->> 'calories')::integer,
    (p ->> 'protein_g')::integer,
    (p ->> 'carbs_g')::integer,
    (p ->> 'fat_g')::integer,
    (p ->> 'water_ml')::integer,
    'rule'
  )
  on conflict (user_id, effective_from) do update set
    calories  = excluded.calories,
    protein_g = excluded.protein_g,
    carbs_g   = excluded.carbs_g,
    fat_g     = excluded.fat_g,
    water_ml  = excluded.water_ml,
    source    = excluded.source;
end;
$$;

revoke all on function public.complete_onboarding(jsonb) from public;
grant execute on function public.complete_onboarding(jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- delete_my_account — Play Store zorunluluğu: uygulama içinden hesap silme.
-- ---------------------------------------------------------------------------
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;
  -- auth.users silinince tüm tablolar on delete cascade ile temizlenir.
  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
