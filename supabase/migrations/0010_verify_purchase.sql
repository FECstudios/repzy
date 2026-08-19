-- ---------------------------------------------------------------------------
-- verify-purchase Edge Function'ının ihtiyaç duyduğu düzeltmeler.
--
-- 1) is_premium() 'canceled' durumunu da kabul ediyor.
--
--    Google Play'de SUBSCRIPTION_STATE_CANCELED "otomatik yenileme kapatıldı"
--    demek — kullanıcının erişimi ÖDEDİĞİ dönemin sonuna kadar sürüyor.
--    0009'daki hâli iptali anında premium dışı sayıyordu: parasını ödemiş
--    kullanıcı iptale bastığı saniye limitleri düşerdi. Süre kontrolünü zaten
--    expires_at yapıyor, dolayısıyla 'canceled' eklemek güvenli.
--
-- 2) Satın alma token'ı ile kullanıcı eşleşmesini sorgulayan yardımcı.
--    Edge Function bir token'ın BAŞKA bir hesaba bağlı olup olmadığını
--    service_role ile kontrol ediyor; aynı fişi iki hesapta kullanmayı engeller.
-- ---------------------------------------------------------------------------

create or replace function public.is_premium()
returns boolean
language sql
security invoker
set search_path = public
stable
as $$
  select exists (
    select 1 from subscriptions s
    where s.user_id = auth.uid()
      and s.status in ('active', 'in_trial', 'grace', 'canceled')
      and (s.expires_at is null or s.expires_at > now())
  );
$$;

revoke all on function public.is_premium() from public;
grant execute on function public.is_premium() to authenticated;

-- Aynı purchase_token iki farklı hesaba yazılamasın: token benzersiz olmalı.
-- (Kullanıcı başına tek satır zaten primary key ile garanti; bu ikinci yön.)
create unique index if not exists subscriptions_token_uniq
  on public.subscriptions (purchase_token)
  where purchase_token is not null;
