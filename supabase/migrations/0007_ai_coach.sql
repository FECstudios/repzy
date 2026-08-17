-- ---------------------------------------------------------------------------
-- Günlük AI koçu: kullanıcının verisine bakıp bugün ne yapması gerektiğini söyler.
--
-- İki parça:
--   1. coach_context()  — kullanıcının son 14 gününü TEK sorguda özetler.
--      Edge Function'ın 10 ayrı istek atmasını engeller; hem hızlı hem ucuz.
--   2. ai_briefs        — üretilen brief günde bir kez saklanır. Aynı gün tekrar
--      açılışta AI'ya gidilmez (maliyet), ve dünkü brief bağlama girer;
--      böylece koç kendini tekrar etmez ve zamanla "birikmiş" veriye göre konuşur.
-- ---------------------------------------------------------------------------

create table if not exists public.ai_briefs (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users (id) on delete cascade,
  brief_date   date not null default current_date,
  headline     text not null,
  focus        text not null,
  actions      jsonb not null default '[]'::jsonb,
  progress_note text,
  model        text,
  created_at   timestamptz not null default now(),
  unique (user_id, brief_date)
);

create index if not exists ai_briefs_user_date_idx
  on public.ai_briefs (user_id, brief_date desc);

alter table public.ai_briefs enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'ai_briefs' and policyname = 'ai_briefs_select_own'
  ) then
    create policy ai_briefs_select_own on public.ai_briefs
      for select using (auth.uid() = user_id);
    create policy ai_briefs_insert_own on public.ai_briefs
      for insert with check (auth.uid() = user_id);
    create policy ai_briefs_update_own on public.ai_briefs
      for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
    create policy ai_briefs_delete_own on public.ai_briefs
      for delete using (auth.uid() = user_id);
  end if;
end $$;

-- ai_usage.kind kısıtına yeni tür ekleniyor: brief üretimi de maliyet sayılır.
alter table public.ai_usage drop constraint if exists ai_usage_kind_check;
alter table public.ai_usage add constraint ai_usage_kind_check
  check (kind in ('food_photo', 'chat', 'weekly_report', 'daily_brief'));

-- ---------------------------------------------------------------------------
-- coach_context() — koçun gördüğü her şey. Ham satır DÖNMEZ, sadece özet:
-- fotoğraf, isim gibi hassas alan AI'ya hiç gitmiyor.
-- ---------------------------------------------------------------------------
create or replace function public.coach_context()
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  today date := current_date;
  result jsonb;
begin
  if uid is null then
    raise exception 'oturum yok';
  end if;

  select jsonb_build_object(
    'profile', (
      select jsonb_build_object(
        'goal', p.goal,
        'experience', p.experience_level,
        'equipment', p.equipment_access,
        'activity', p.activity_level,
        'sex', p.sex,
        'age', extract(year from current_date)::int - p.birth_year,
        'height_cm', p.height_cm
      )
      from profiles p where p.id = uid
    ),
    'targets', (
      select jsonb_build_object(
        'calories', t.calories,
        'protein_g', t.protein_g,
        'carbs_g', t.carbs_g,
        'fat_g', t.fat_g,
        'water_ml', t.water_ml
      )
      from nutrition_targets t
      where t.user_id = uid
      order by t.effective_from desc
      limit 1
    ),
    'today', jsonb_build_object(
      'calories', coalesce((
        select round(sum(f.calories)) from food_logs f
        where f.user_id = uid and f.log_date = today
      ), 0),
      'protein_g', coalesce((
        select round(sum(f.protein_g)) from food_logs f
        where f.user_id = uid and f.log_date = today
      ), 0),
      'water_ml', coalesce((
        select sum(w.amount_ml) from water_logs w
        where w.user_id = uid and w.log_date = today
      ), 0),
      'workout_done', exists (
        select 1 from workouts w
        where w.user_id = uid and w.finished_at is not null
          and w.started_at::date = today
      )
    ),
    -- Son 7 gün: koçun "sen geliştikçe adapte ol" kısmı buradan besleniyor.
    'last7', jsonb_build_object(
      'workout_days', (
        select count(distinct w.started_at::date) from workouts w
        where w.user_id = uid and w.finished_at is not null
          and w.started_at::date > today - 7
      ),
      'avg_calories', coalesce((
        select round(avg(daily)) from (
          select sum(f.calories) as daily from food_logs f
          where f.user_id = uid and f.log_date > today - 7
          group by f.log_date
        ) d
      ), 0),
      'avg_protein_g', coalesce((
        select round(avg(daily)) from (
          select sum(f.protein_g) as daily from food_logs f
          where f.user_id = uid and f.log_date > today - 7
          group by f.log_date
        ) d
      ), 0),
      'logged_food_days', (
        select count(distinct f.log_date) from food_logs f
        where f.user_id = uid and f.log_date > today - 7
      ),
      'water_target_days', coalesce((
        select count(*) from (
          select w.log_date, sum(w.amount_ml) as total
          from water_logs w
          where w.user_id = uid and w.log_date > today - 7
          group by w.log_date
        ) d
        where d.total >= coalesce((
          select t.water_ml from nutrition_targets t
          where t.user_id = uid order by t.effective_from desc limit 1
        ), 999999)
      ), 0),
      -- Hangi kas grupları çalışıldı: dengesizliği koç görsün.
      'muscles', coalesce((
        select jsonb_agg(distinct e.primary_muscle)
        from workout_sets s
        join workouts w on w.id = s.workout_id
        join exercises e on e.id = s.exercise_id
        where w.user_id = uid and w.started_at::date > today - 7
      ), '[]'::jsonb),
      'avg_effort', (
        select round(avg(w.perceived_effort), 1) from workouts w
        where w.user_id = uid and w.perceived_effort is not null
          and w.started_at::date > today - 7
      )
    ),
    'weight', (
      select jsonb_build_object(
        'latest_kg', (array_agg(m.weight_kg order by m.measured_on desc))[1],
        'latest_on', (array_agg(m.measured_on order by m.measured_on desc))[1],
        'oldest_kg', (array_agg(m.weight_kg order by m.measured_on))[1],
        'oldest_on', (array_agg(m.measured_on order by m.measured_on))[1],
        'entries', count(*)
      )
      from body_metrics m
      where m.user_id = uid and m.measured_on > today - 60
    ),
    'streak', public.current_streak(),
    -- Dünkü brief bağlama giriyor: koç aynı şeyi iki gün üst üste söylemesin.
    'previous_brief', (
      select jsonb_build_object(
        'date', b.brief_date,
        'focus', b.focus,
        'actions', b.actions
      )
      from ai_briefs b
      where b.user_id = uid and b.brief_date < today
      order by b.brief_date desc
      limit 1
    ),
    'days_since_signup', (
      select greatest(0, (today - p.created_at::date)) from profiles p where p.id = uid
    )
  ) into result;

  return result;
end;
$$;

revoke all on function public.coach_context() from public;
grant execute on function public.coach_context() to authenticated;
