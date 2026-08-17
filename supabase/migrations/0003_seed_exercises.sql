-- Başlangıç egzersiz kütüphanesi. Her salon hareketinin ev alternatifi var.
-- Görseller/animasyonlar sonra eklenecek (image_url / animation_url şimdilik null).

insert into public.exercises (
  id, name_tr, name_en, primary_muscle, secondary_muscles, equipment, setting, level, mechanic,
  instructions_tr, instructions_en, common_mistakes_tr, common_mistakes_en
) values

-- ============================ BACAK ============================
('barbell_back_squat', 'Barbell Squat', 'Barbell Back Squat', 'quadriceps',
 array['glutes', 'hamstrings', 'core'], 'barbell', 'gym', 'intermediate', 'compound',
 array[
   'Barı omuz üstü kas kütlesine yerleştir, ellerini simetrik kavra.',
   'Ayaklar omuz genişliğinde, parmak uçları hafif dışa dönük.',
   'Kalçayı geriye alarak çök; dizler ayak uçları hizasında dışa doğru itsin.',
   'Uyluklar yere paralel olana kadar in, topuktan güç alarak kalk.'
 ],
 array[
   'Rest the bar on your upper back muscles, grip evenly on both sides.',
   'Feet shoulder width apart, toes turned slightly out.',
   'Push your hips back and descend, tracking knees over your toes.',
   'Go until thighs are parallel to the floor, then drive up through your heels.'
 ],
 array[
   'Dizlerin içe çökmesi',
   'Topukların yerden kalkması',
   'Belin aşağıda yuvarlanması (butt wink)',
   'Baştan çok ağır yükle başlamak'
 ],
 array[
   'Knees caving inward',
   'Heels lifting off the floor',
   'Lower back rounding at the bottom',
   'Starting far too heavy'
 ]),

('bodyweight_squat', 'Vücut Ağırlığı Squat', 'Bodyweight Squat', 'quadriceps',
 array['glutes', 'core'], 'bodyweight', 'home', 'beginner', 'compound',
 array[
   'Ayaklar omuz genişliğinde, kollar önde dengede.',
   'Kalçayı geriye alarak otur, göğsünü yukarıda tut.',
   'Uyluklar paralele gelene kadar in, topuktan itip kalk.'
 ],
 array[
   'Feet shoulder width apart, arms out in front for balance.',
   'Sit back with your hips, keep your chest up.',
   'Descend until thighs are parallel, then drive up through your heels.'
 ],
 array['Öne düşen gövde', 'Dizlerin içe çökmesi', 'Yarım tekrar yapmak'],
 array['Chest collapsing forward', 'Knees caving inward', 'Cutting the range of motion short']),

('leg_press', 'Leg Press', 'Leg Press', 'quadriceps',
 array['glutes', 'hamstrings'], 'machine', 'gym', 'beginner', 'compound',
 array[
   'Sırtın ve kalçan minderdedir, ayaklar platformda omuz genişliğinde.',
   'Dizler 90 dereceye gelene kadar kontrollü in.',
   'Dizleri tam kilitlemeden it.'
 ],
 array[
   'Back and hips flat against the pad, feet shoulder width on the platform.',
   'Lower under control until your knees reach about 90 degrees.',
   'Press back up without fully locking your knees.'
 ],
 array['Kalçanın minderden kalkması', 'Dizleri sertçe kilitlemek', 'Çok derine inip beli yuvarlamak'],
 array['Hips lifting off the pad', 'Slamming the knees into lockout', 'Going so deep the lower back rounds']),

('romanian_deadlift', 'Romanian Deadlift', 'Romanian Deadlift', 'hamstrings',
 array['glutes', 'lower_back'], 'barbell', 'gym', 'intermediate', 'compound',
 array[
   'Barı kalça hizasında tut, dizler hafif kırık.',
   'Kalçayı geriye iterek barı bacak hattı boyunca indir.',
   'Hamstringde gerilme hissedince kalçayı öne iterek kalk.'
 ],
 array[
   'Hold the bar at hip height with a slight knee bend.',
   'Push your hips back and lower the bar along your legs.',
   'When you feel the hamstring stretch, drive your hips forward to stand.'
 ],
 array['Beli yuvarlamak', 'Barı vücuttan uzaklaştırmak', 'Hareketi squat gibi yapmak'],
 array['Rounding the lower back', 'Letting the bar drift away from the body', 'Turning it into a squat']),

('glute_bridge', 'Kalça Köprüsü', 'Glute Bridge', 'glutes',
 array['hamstrings', 'core'], 'bodyweight', 'home', 'beginner', 'compound',
 array[
   'Sırt üstü yat, ayaklar kalçaya yakın ve yere basılı.',
   'Karnını sık, kalçayı sıkarak yukarı kaldır.',
   'Tepede bir saniye bekle, kontrollü indir.'
 ],
 array[
   'Lie on your back, feet flat and close to your hips.',
   'Brace your core and squeeze your glutes to lift your hips.',
   'Hold for a second at the top, then lower under control.'
 ],
 array['Beli aşırı germek', 'Kalça yerine belden itmek', 'Ayakları çok uzağa koymak'],
 array['Overarching the lower back', 'Pushing from the lower back instead of the glutes', 'Placing the feet too far away']),

-- ============================ GÖĞÜS ============================
('barbell_bench_press', 'Bench Press', 'Barbell Bench Press', 'chest',
 array['triceps', 'front_delts'], 'barbell', 'gym', 'intermediate', 'compound',
 array[
   'Sedyeye uzan, gözler barın altında olsun; kürek kemiklerini geriye sık.',
   'Barı omuz genişliğinden biraz geniş kavra.',
   'Barı göğsün alt kısmına kontrollü indir, dirsekler ~45 derece.',
   'Göğüsten güç alarak yukarı it.'
 ],
 array[
   'Lie down with your eyes under the bar, retract your shoulder blades.',
   'Grip slightly wider than shoulder width.',
   'Lower the bar under control to your lower chest, elbows around 45 degrees.',
   'Press up driving through your chest.'
 ],
 array[
   'Barı göğse zıplatmak',
   'Dirsekleri tamamen yana açmak (omuz sıkışması)',
   'Kalçayı sedyeden kaldırmak',
   'Spotter olmadan maksimal denemek'
 ],
 array[
   'Bouncing the bar off the chest',
   'Flaring the elbows straight out to the sides',
   'Lifting the hips off the bench',
   'Attempting a max without a spotter'
 ]),

('push_up', 'Şınav', 'Push-Up', 'chest',
 array['triceps', 'core', 'front_delts'], 'bodyweight', 'home', 'beginner', 'compound',
 array[
   'Eller omuz genişliğinden biraz geniş, vücut baştan topuğa düz bir hat.',
   'Dirsekler ~45 derece açıyla geriye kayarak göğsünü yere yaklaştır.',
   'Karnını sıkarak yukarı it. Zorsa dizlerin üzerinde yap.'
 ],
 array[
   'Hands slightly wider than shoulders, body in a straight line head to heels.',
   'Lower your chest, elbows travelling back at about 45 degrees.',
   'Brace your core and press up. Do it from your knees if needed.'
 ],
 array['Kalçanın yukarı kalkması', 'Belin çökmesi', 'Boynu öne uzatmak', 'Yarım tekrar'],
 array['Hips riding up', 'Sagging lower back', 'Craning the neck forward', 'Partial reps']),

('machine_chest_press', 'Makine Göğüs Press', 'Machine Chest Press', 'chest',
 array['triceps', 'front_delts'], 'machine', 'gym', 'beginner', 'compound',
 array[
   'Koltuğu tutamaklar göğüs hizasına gelecek şekilde ayarla.',
   'Sırtını mindere yasla, kürekleri geride tut.',
   'Kontrollü it, dirsekleri kilitlemeden geri getir.'
 ],
 array[
   'Set the seat so the handles line up with your chest.',
   'Keep your back on the pad and shoulder blades retracted.',
   'Press under control and return without locking the elbows.'
 ],
 array['Koltuk yüksekliğini ayarlamamak', 'Omuzları öne yuvarlamak', 'Ağırlığı geri bırakırken kontrolü kaybetmek'],
 array['Not adjusting the seat height', 'Rolling the shoulders forward', 'Losing control on the way back']),

-- ============================ SIRT ============================
('lat_pulldown', 'Lat Pulldown', 'Lat Pulldown', 'lats',
 array['biceps', 'rear_delts'], 'cable', 'gym', 'beginner', 'compound',
 array[
   'Diz pedini bacaklarını sabitleyecek şekilde ayarla.',
   'Barı omuz genişliğinden geniş kavra, göğsü yukarı ver.',
   'Barı köprücük kemiğine doğru çek, dirsekleri aşağı-geriye sür.',
   'Kontrollü bırak, omuzların kulağa yaklaşmasına izin verme.'
 ],
 array[
   'Adjust the knee pad so your legs are held in place.',
   'Grip wider than shoulder width, chest up.',
   'Pull the bar toward your collarbone, driving your elbows down and back.',
   'Return under control, do not let your shoulders shrug up.'
 ],
 array['Gövdeyi aşırı geriye yatırmak', 'Barı ense arkasına çekmek', 'Kollarla değil momentumla çekmek'],
 array['Leaning way too far back', 'Pulling the bar behind your neck', 'Using momentum instead of the lats']),

('seated_cable_row', 'Oturarak Kablo Çekiş', 'Seated Cable Row', 'mid_back',
 array['lats', 'biceps'], 'cable', 'gym', 'beginner', 'compound',
 array[
   'Dizler hafif kırık, gövde dik otur.',
   'Tutamağı karın hizasına çek, kürekleri birbirine sık.',
   'Kolları kontrollü uzat, gövdeyi sallamadan tekrarla.'
 ],
 array[
   'Sit tall with a slight bend in the knees.',
   'Pull the handle to your stomach, squeezing your shoulder blades together.',
   'Extend your arms under control without rocking your torso.'
 ],
 array['Gövdeyi ileri geri sallamak', 'Omuzları kaldırmak', 'Sadece kollarla çekmek'],
 array['Rocking the torso back and forth', 'Shrugging the shoulders', 'Pulling with the arms only']),

('resistance_band_row', 'Lastikle Çekiş', 'Resistance Band Row', 'mid_back',
 array['lats', 'biceps'], 'band', 'home', 'beginner', 'compound',
 array[
   'Lastiği sabit bir noktaya göğüs hizasında bağla.',
   'Gövde dik, dirsekleri geriye sürerek kendine çek.',
   'Kürekleri sık, kontrollü geri bırak.'
 ],
 array[
   'Anchor the band at chest height to something solid.',
   'Stand tall and pull toward you, driving your elbows back.',
   'Squeeze your shoulder blades, release under control.'
 ],
 array['Gövdeyi geriye yatırmak', 'Lastiği aniden bırakmak', 'Omuzları kulağa çekmek'],
 array['Leaning back to move the band', 'Letting the band snap back', 'Shrugging the shoulders']),

-- ============================ OMUZ / KOL ============================
('dumbbell_shoulder_press', 'Dumbbell Omuz Press', 'Dumbbell Shoulder Press', 'front_delts',
 array['triceps', 'core'], 'dumbbell', 'both', 'beginner', 'compound',
 array[
   'Dumbbell''ları omuz hizasında tut, avuç içleri öne dönük.',
   'Karnını sık, dirsekleri kilitlemeden yukarı it.',
   'Kontrollü indir; dirsekler kulak hizasının biraz önünde kalsın.'
 ],
 array[
   'Hold the dumbbells at shoulder height, palms facing forward.',
   'Brace your core and press up without locking the elbows.',
   'Lower under control, keeping your elbows slightly in front of your ears.'
 ],
 array['Beli geriye kırmak', 'Dumbbell''ları birbirine vurmak', 'Çok ağır seçip yarım tekrar yapmak'],
 array['Arching the lower back', 'Clanging the dumbbells together', 'Going too heavy and cutting reps short']),

('pike_push_up', 'Pike Şınav', 'Pike Push-Up', 'front_delts',
 array['triceps', 'core'], 'bodyweight', 'home', 'intermediate', 'compound',
 array[
   'Kalçayı yukarı kaldırarak ters V pozisyonu al.',
   'Başını ellerin arasına doğru indir.',
   'Omuzlarla iterek başlangıca dön.'
 ],
 array[
   'Get into an inverted V with your hips high.',
   'Lower your head toward the space between your hands.',
   'Press back up using your shoulders.'
 ],
 array['Kalçayı düşürüp şınava dönüştürmek', 'Boynu zorlamak', 'Elleri çok ileri koymak'],
 array['Dropping the hips into a push-up', 'Straining the neck', 'Placing the hands too far forward']),

('cable_triceps_pushdown', 'Triceps Pushdown', 'Cable Triceps Pushdown', 'triceps',
 array[]::text[], 'cable', 'gym', 'beginner', 'isolation',
 array[
   'Makarayı üst konuma al, dirsekleri gövdeye sabitle.',
   'Sadece dirsekten açarak aşağı it.',
   'Kontrollü geri bırak, dirsekler öne kaymasın.'
 ],
 array[
   'Set the pulley high and pin your elbows to your sides.',
   'Extend only at the elbow to push down.',
   'Return under control, keeping the elbows from drifting forward.'
 ],
 array['Gövdeyi öne yatırıp ağırlığı bastırmak', 'Dirsekleri yana açmak', 'Omuzdan çalışmak'],
 array['Leaning forward to press the weight down', 'Flaring the elbows out', 'Turning it into a shoulder movement']),

('dumbbell_biceps_curl', 'Dumbbell Biceps Curl', 'Dumbbell Biceps Curl', 'biceps',
 array['forearms'], 'dumbbell', 'both', 'beginner', 'isolation',
 array[
   'Dumbbell''ları yanlarda tut, dirsekler gövdeye yakın.',
   'Bileği çevirmeden ya da çevirerek yukarı kaldır.',
   'Tepede bir an sık, kontrollü indir.'
 ],
 array[
   'Hold the dumbbells at your sides, elbows close to your torso.',
   'Curl up, with or without rotating the wrist.',
   'Squeeze at the top, lower under control.'
 ],
 array['Gövdeyi sallayarak momentum vermek', 'Dirsekleri öne kaldırmak', 'İnişi hızlı bırakmak'],
 array['Swinging the torso for momentum', 'Letting the elbows travel forward', 'Dropping the weight on the way down']),

-- ============================ CORE ============================
('plank', 'Plank', 'Plank', 'core',
 array['glutes', 'front_delts'], 'bodyweight', 'both', 'beginner', 'isolation',
 array[
   'Dirsekler omuz altında, vücut baştan topuğa düz hat.',
   'Karnını ve kalçanı sık, nefes almaya devam et.',
   'Form bozulduğu anda bitir — süreyi zorlamak yerine formu koru.'
 ],
 array[
   'Elbows under your shoulders, body in a straight line head to heels.',
   'Brace your core and glutes, keep breathing.',
   'Stop the moment form breaks down instead of chasing time.'
 ],
 array['Kalçanın yukarı kalkması', 'Belin çökmesi', 'Nefesi tutmak'],
 array['Hips riding up', 'Sagging lower back', 'Holding your breath'])

on conflict (id) do nothing;

-- Salon <-> ev alternatifleri (iki yönlü kayıt).
insert into public.exercise_alternatives (exercise_id, alternative_id, reason) values
  ('barbell_back_squat', 'bodyweight_squat', 'home_no_equipment'),
  ('bodyweight_squat', 'barbell_back_squat', 'gym_progression'),
  ('leg_press', 'bodyweight_squat', 'home_no_equipment'),
  ('romanian_deadlift', 'glute_bridge', 'home_no_equipment'),
  ('glute_bridge', 'romanian_deadlift', 'gym_progression'),
  ('barbell_bench_press', 'push_up', 'home_no_equipment'),
  ('push_up', 'barbell_bench_press', 'gym_progression'),
  ('machine_chest_press', 'push_up', 'home_no_equipment'),
  ('lat_pulldown', 'resistance_band_row', 'home_no_equipment'),
  ('resistance_band_row', 'lat_pulldown', 'gym_progression'),
  ('seated_cable_row', 'resistance_band_row', 'home_no_equipment'),
  ('dumbbell_shoulder_press', 'pike_push_up', 'home_no_equipment'),
  ('pike_push_up', 'dumbbell_shoulder_press', 'gym_progression'),
  ('cable_triceps_pushdown', 'push_up', 'home_no_equipment')
on conflict do nothing;
