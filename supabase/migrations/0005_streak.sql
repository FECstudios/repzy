-- Streak, istemcide hesaplanmaz: tüm günleri telefona indirmek gerekirdi.
-- "Aktif gün" = o gün su, yemek ya da antrenman kaydı var.
--
-- Streak bugün aktifse bugünden, değilse dünden geriye sayılır — yani gün içinde
-- henüz bir şey yapmamış kullanıcının serisi sıfırlanmış görünmez.

create or replace function public.current_streak()
returns integer
language plpgsql
stable
security invoker
set search_path = public
as $$
declare
  uid    uuid := auth.uid();
  days   date[];
  anchor date;
  probe  date;
  streak integer := 0;
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;

  select array_agg(distinct d order by d desc)
    into days
    from (
      select log_date as d from public.water_logs where user_id = uid
      union
      select log_date from public.food_logs where user_id = uid
      union
      select (started_at at time zone 'utc')::date from public.workouts where user_id = uid
    ) activity
   where d <= current_date;

  if days is null then
    return 0;
  end if;

  if current_date = any(days) then
    anchor := current_date;
  elsif current_date - 1 = any(days) then
    anchor := current_date - 1;
  else
    return 0;
  end if;

  probe := anchor;
  while probe = any(days) loop
    streak := streak + 1;
    probe := probe - 1;
  end loop;

  return streak;
end;
$$;

revoke all on function public.current_streak() from public;
grant execute on function public.current_streak() to authenticated;
