-- Storage bucket'ları ve erişim politikaları.
-- Dosya yolu şeması: <user_id>/<dosya_adı>  → politika ilk klasörü auth.uid() ile eşler.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('body-photos', 'body-photos', false, 8388608, array['image/jpeg', 'image/png', 'image/webp']),
  ('food-photos', 'food-photos', false, 8388608, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

-- Vücut fotoğrafı: KVKK özel nitelikli veri. Bucket private, imzalı URL ile okunur.
create policy body_photos_select_own on storage.objects
  for select to authenticated
  using (bucket_id = 'body-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy body_photos_insert_own on storage.objects
  for insert to authenticated
  with check (bucket_id = 'body-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy body_photos_update_own on storage.objects
  for update to authenticated
  using (bucket_id = 'body-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy body_photos_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id = 'body-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy food_photos_select_own on storage.objects
  for select to authenticated
  using (bucket_id = 'food-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy food_photos_insert_own on storage.objects
  for insert to authenticated
  with check (bucket_id = 'food-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy food_photos_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id = 'food-photos' and (storage.foldername(name))[1] = auth.uid()::text);
