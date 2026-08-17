-- ---------------------------------------------------------------------------
-- Abonelik durumu.
--
-- KRİTİK: Bu tabloya kullanıcı YAZAMAZ. Sadece select politikası var.
-- Premium'u istemci belirlerse (ya da kullanıcı kendi satırını ekleyebilirse)
-- AI limitleri anlamsız hale gelir — para maliyeti olan bir sınırı istemciye
-- bırakmıyoruz. Satırı yalnızca service_role ile çalışan doğrulama fonksiyonu yazar.
--
-- Google Play satın alması şöyle doğrulanır (henüz yapılmadı):
--   uygulama purchaseToken'ı Edge Function'a gönderir →
--   fonksiyon Google Play Developer API'sine sorar →
--   geçerliyse service_role ile bu tabloya yazar.
-- Bu doğrulama kurulmadan hiç kimse premium olamaz; kasıtlı olarak "fail closed".
-- ---------------------------------------------------------------------------
create table if not exists public.subscriptions (
  user_id        uuid primary key references auth.users (id) on delete cascade,
  status         text not null check (status in ('active', 'in_trial', 'grace', 'expired', 'canceled')),
  product_id     text,
  purchase_token text,
  started_at     timestamptz not null default now(),
  expires_at     timestamptz,
  updated_at     timestamptz not null default now()
);

create index if not exists subscriptions_expiry_idx
  on public.subscriptions (expires_at);

alter table public.subscriptions enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'subscriptions'
      and policyname = 'subscriptions_select_own'
  ) then
    -- Yalnızca okuma. insert/update/delete politikası BİLEREK yok.
    create policy subscriptions_select_own on public.subscriptions
      for select to authenticated using (auth.uid() = user_id);
  end if;
end $$;

drop trigger if exists subscriptions_touch on public.subscriptions;
create trigger subscriptions_touch
  before update on public.subscriptions
  for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- is_premium() — Edge Function'lar limiti buna göre seçiyor.
-- Süresi geçmiş abonelik premium sayılmaz; deneme süresi sayılır.
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
      and s.status in ('active', 'in_trial', 'grace')
      and (s.expires_at is null or s.expires_at > now())
  );
$$;

revoke all on function public.is_premium() from public;
grant execute on function public.is_premium() to authenticated;
