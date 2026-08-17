-- Onboarding artık hesap AÇILMADAN ÖNCE dolduruluyor: cevaplar cihazda birikiyor,
-- kullanıcı hesap oluşturunca tek seferde buraya yazılıyor.
--
-- Bu yüzden açık rıza zamanı sunucuya yazma anı (now()) değil, kullanıcının
-- rıza kutusunu işaretlediği an olmalı — istemci bunu 'health_consent_at' ile gönderiyor.
-- KVKK açısından fark önemli: rızanın verildiği an belgelenmiş olur.

create or replace function public.complete_onboarding(p jsonb)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  consent_at timestamptz := (p ->> 'health_consent_at')::timestamptz;
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;

  -- Gelecekten ya da makul olmayan geçmişten gelen rıza damgasına güvenilmez.
  if consent_at is null or consent_at > now() + interval '1 day' or consent_at < now() - interval '365 days' then
    consent_at := now();
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
    health_data_consent_at  = coalesce(health_data_consent_at, consent_at),
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
