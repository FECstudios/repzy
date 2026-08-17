-- ---------------------------------------------------------------------------
-- ai_usage yazma yolu.
--
-- 0001'de ai_usage'a bilerek sadece SELECT politikası verildi: kullanıcı kendi
-- kullanımını görebilsin ama satır ekleyip/silip limiti atlatamasın.
-- Ama Edge Function da kullanıcının JWT'siyle çalışıyor (service key taşımamak için),
-- dolayısıyla onun insert'i de RLS'e takılıyordu ve kullanım HİÇ kaydedilmiyordu:
-- sayaç her zaman 0 kalıyor, günlük tarama limiti fiilen uygulanmıyordu.
--
-- Çözüm: security definer fonksiyon. Kullanıcı bunu doğrudan çağırırsa sadece
-- kendi kotasını harcar — kazancı olmaz. Satır silme hâlâ mümkün değil
-- (ai_usage'da delete politikası yok), yani limit atlatılamıyor.
-- ---------------------------------------------------------------------------
create or replace function public.record_ai_usage(
  p_kind       text,
  p_model      text default null,
  p_tokens_in  integer default null,
  p_tokens_out integer default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'oturum yok';
  end if;

  -- Tür beyaz listesi: tablo kısıtı zaten var, burada da açıkça duruyor.
  if p_kind not in ('food_photo', 'chat', 'weekly_report', 'daily_brief') then
    raise exception 'geçersiz kullanım türü: %', p_kind;
  end if;

  insert into public.ai_usage (user_id, kind, model, tokens_in, tokens_out)
  values (uid, p_kind, p_model, p_tokens_in, p_tokens_out);
end;
$$;

revoke all on function public.record_ai_usage(text, text, integer, integer) from public;
grant execute on function public.record_ai_usage(text, text, integer, integer) to authenticated;
