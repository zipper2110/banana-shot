# Localization Glossary

This file holds the term tables of the app translations (B-11, E4-T2). Each
language has one table. A translator and a reviewer use the same terms in all
texts of the catalog (`src/main/resources/i18n/`) and of the help.

Rules for all languages:

- Use one translation for each term. Do not use a synonym in a different text.
- If a term is a name in the UI (a tab, a button, a field), use the exact
  catalog text of that name in the help and in the other texts.
- The words in the "Do not translate" list stay as they are.
- Decided on 2026-10-28: the languages ship without a native review (plan,
  "Decisions", "Translation"). The terms in the tables are the terms of the
  release. The sections "Open questions for the reviewer" stay for a later
  review, if a reviewer comes forward.

## Do not translate

| Term | Reason |
|---|---|
| BananaShot | The brand |
| 4K, 1080p, 720p | Video resolution codes |
| H.264, HEVC, NVENC, AMF, QSV, x264 | Codec and encoder names |
| fps, FPS | Unit of the frame rate |
| MB, GB, KB, Mbit/s | Units |
| PNG, MP4 | File types |
| Q, W, E, R, S, C, V, F1 | Key letters of the shortcuts |
| VS, vs | The word between the names on a thumbnail. Spanish uses the same word |
| Ad, AD | The advantage score on the scoreboard. It is the same in all languages |
| YouTube, Windows, GitHub, Cloudflare, Telegram, Smart App Control | Product names |

## Spanish (`es`, `es-419`)

The app has two Spanish variants:

- "Español (España)" (`es`): the files `*_es.properties`. They have all
  texts.
- "Español (Latinoamérica)" (`es-419`): the files `*_es_419.properties`. They
  have only the texts that are different from Spain. All other texts come
  from the Spain files. When you change a Spain text that has a word of the
  table "Words of each variant", change the Latin American text too.

The tables below are for both variants, except the table "Words of each
variant".

Style:

- Buttons and menu items use the infinitive: "Exportar", "Guardar la
  miniatura".
- Sentences that tell the user what to do use the "tú" imperative: "Marca
  los puntos", "Selecciona un estilo".
- The app is "la app".
- Spanish quotes are «…».
- In a catalog text, a number and the percent sign have a no-break space:
  "{percent} %" (RAE rule).
- A percentage that the code formats (the statistics values, the export
  progress) follows the Windows regional format, not the app language
  (E1-T5): "64 %" for `es-ES`, "64%" for `es-MX`. The zoom field of the
  Transform controls keeps "150%", because its parser reads the unit.

### Sport terms

| English | Spanish | Note |
|---|---|---|
| point | punto | |
| game | juego | |
| set | set | Plural "sets" |
| tiebreak | tie-break | Also "match tie-break" and "súper tie-break" |
| deuce | iguales | The deuce rule: "regla del iguales" |
| advantage | ventaja | |
| golden point | punto de oro | |
| star point | punto estrella | |
| deciding point | punto decisivo | |
| serve, server | saque, quién saca | "Switch the server" = "cambiar quién saca" |
| return | resto | "Return games won" = "Juegos al resto ganados" |
| break point | punto de break | |
| set point, match point | punto de set, punto de partido | |
| player | jugador | Default name "Jugador 1" |
| team (padel) | pareja | Default name "Pareja 1". Padel players in Spain and in Latin America say "pareja". The feminine form needs feminine words: "la pareja que saca", "las dos parejas juntas" |
| singles, doubles | individual, dobles | Thumbnail match type |
| padel, tennis | pádel, tenis | |
| beach tennis | tenis playa | The name that the ITF and the RFET use |
| Americano | Americano | |
| pro set | pro set | |
| best of 3 sets | al mejor de 3 sets | |
| momentum | dinámica | Statistics group and chart |
| no point (outcome) | sin punto | |

### App terms

| English | Spanish | Note |
|---|---|---|
| video | vídeo, video | See "Words of each variant" |
| project | proyecto | |
| export (noun, verb) | exportación, exportar | |
| scoreboard | marcador | |
| score (the numbers) | marcador, resultado | "Final score" = "Resultado final" |
| Override score | Corregir marcador | |
| Scoring settings | Ajustes del marcador | |
| Scoreboard style | Estilo del marcador | |
| favorite | favorito | |
| comment | comentario | |
| thumbnail | miniatura | |
| statistics card | tarjeta de estadísticas | |
| set summary | resumen de set | |
| change point | punto de cambio | |
| Transform | Encuadre | The tab "Colors & Transform" = "Color y encuadre" |
| crop rectangle | rectángulo de recorte | |
| playhead | cabezal de reproducción | |
| seek bar | barra de búsqueda | |
| timeline | línea de tiempo | |
| frame | fotograma | "imagen" for the visible area of the thumbnail |
| encoder | codificador | |
| bitrate | tasa de bits | |
| frame rate | fotogramas por segundo | |
| queue | cola | |
| log files | archivos de registro | |
| feedback (sidebar) | Opinión | The dialog title is "Enviar comentarios" |
| Settings | Ajustes | |
| More | Más | |
| Help | Ayuda | |
| Shift, Enter, Space, Delete | Mayús, Intro, Espacio, Supr | Key names |

### Short forms

Some controls have a fixed width. The long Spanish text did not fit, so
these controls use a short form. The help uses the same short form.

| English | Spanish | Control |
|---|---|---|
| COMMENTS | COMENT. | Track name on the timeline of the Points tab |
| {name} scored | Gana {name} | Outcome buttons of the Points tab |
| Frame step | Por fotograma | Toggle on the playback bar |
| Import a match video | Importa un partido | Step 1 of the new-project workflow |
| Make a YouTube thumbnail | Haz la miniatura | Step 6 of the new-project workflow |
| Tune the image at any time | Ajusta la imagen | Optional step of the new-project workflow |

### Words of each variant

| English | Spain (`es`) | Latin America (`es-419`) |
|---|---|---|
| video | vídeo | video |
| computer | ordenador | computadora (feminine: "esta computadora") |
| press (a key) | pulsa, mantén pulsada | presiona, mantén presionada |
| Enter (key) | Intro | Enter |
| mouse | ratón | mouse |
| phone | móvil | celular |
| expired, expires | ha caducado, caduca | venció, vence |
| live (preview) | en directo | en vivo |

### Tab names

| English | Spanish |
|---|---|
| Projects | Proyectos |
| Points | Puntos |
| Stats (sidebar), Statistics | Estadíst., Estadísticas |
| Colors & Transform | Color y encuadre |
| Thumbnail | Miniatura |
| Export | Exportar |

### Open questions

There are no open term questions now. The release has no native review
(decided on 2026-10-28). If reviewers come forward later (E4-T7), one from
Spain and one from Latin America check all texts.

## Russian (`ru`)

One variant for all countries (files `*_ru.properties`). Windows `ru-RU`,
`ru-BY`, `ru-KZ`, `ru-UA`, and all other Russian locales give it.

Style:

- Sentences that tell the user what to do use the polite "вы" imperative:
  "Отметьте розыгрыши", "Выберите стиль".
- Buttons and menu items use the infinitive: "Экспортировать", "Сохранить
  обложку". Short status words can be nouns or adverbs.
- The app is "приложение" (neuter). Thus "{app} уже запущено", "Приложение
  сохранит".
- Russian quotes are «…». A name of a tab or a button in a sentence is in
  quotes: "на вкладке «Розыгрыши»".
- Use "ё" ("ещё", "всё", "счёт").
- A number and the percent sign have a no-break space: "{percent} %".
- Do not make a past-tense verb agree with a player name. The gender of the
  name is not known. Use a form without gender: "Очко: {name}", "Сет
  выигран: {name}", "{name} впереди".
- Plurals have the four CLDR categories `one`, `few`, `many`, and `other`.
  `other` is for a number with decimals ("1,5 секунды"). A plural with only
  `other` is correct when the text does not change with the number.
- The units: "с", "мин", "ч". "MB" and "fps" stay as in "Do not translate".

### Sport terms

| English | Russian | Note |
|---|---|---|
| point (a marked part of the video, a rally) | розыгрыш | The Points tab is "Розыгрыши". "Only points" = "Только розыгрыши" |
| point (a unit of the score) | очко | "Points won" = "Выиграно очков" |
| game | гейм | |
| set | сет | |
| tiebreak | тай-брейк | "матч-тай-брейк", "супертай-брейк" |
| deuce | ровно | The deuce rule: "правило при «ровно»" |
| advantage (rule) | больше-меньше | "No-ad" = "Без преимущества" |
| golden point | золотое очко | |
| star point | звёздное очко | |
| deciding point | решающее очко | |
| serve, server | подача, подающий | "Switch the server" = "сменить подающего" |
| return | приём | "Return games won" = "Выиграно геймов на приёме" |
| break point | брейк-пойнт | "Break points saved" = "Отыграно брейк-пойнтов" |
| set point, match point | сетбол, матчбол | |
| player | игрок | Default name "Игрок 1" |
| team (padel) | пара | Default name "Пара 1" |
| singles, doubles | одиночный, парный | Thumbnail match type |
| padel, tennis | падел, теннис | |
| beach tennis | пляжный теннис | |
| Americano | Американо | |
| pro set | про-сет | |
| best of 3 sets | до 2 выигранных сетов | "Best of {count}" in the set control = "Из {count}" |
| momentum | динамика | |

### App terms

| English | Russian | Note |
|---|---|---|
| video | видео | |
| project | проект | |
| export (noun, verb) | экспорт, экспортировать | |
| scoreboard | табло | |
| score (the numbers) | счёт | "Final score" = "Итоговый счёт" |
| Override score | Изменить счёт | |
| Scoring settings | Настройки счёта | |
| Scoreboard style | Стиль табло | |
| favorite | избранное, избранный | "Only favorites" = "Только избранные" |
| comment | комментарий | |
| thumbnail | обложка | |
| statistics card | карточка статистики | |
| set summary | итоги сета | |
| change point | точка изменения | |
| Transform | кадр | The tab "Colors & Transform" = "Цвет и кадр" |
| crop rectangle | рамка | |
| playhead | курсор | "at the playhead" = "в позиции курсора" |
| seek bar | полоса перемотки | |
| timeline | шкала времени | |
| frame | кадр | |
| encoder | кодировщик | |
| bitrate | битрейт | |
| frame rate | частота кадров | |
| queue | очередь | |
| log files | файлы журнала | |
| feedback (sidebar) | Отзыв | The dialog title is "Отправить отзыв" |
| Settings | Настройки | |
| More | Ещё | |
| Help | Справка | |
| Shift, Enter, Space, Delete | Shift, Enter, Пробел, Delete | Key names. Russian keyboards show the Latin names |

### Short forms

| English | Russian | Control |
|---|---|---|
| COMMENTS | КОММЕНТ. | Track name on the timeline of the Points tab |
| {name} scored | Очко: {name} | Outcome buttons of the Points tab |
| Import a match video | Импортируйте видео | Step 1 of the new-project workflow |
| Make a YouTube thumbnail | Сделайте обложку | Step 6 of the new-project workflow |
| Tune the image at any time | Настройте картинку | Optional step of the new-project workflow |

### Tab names

| English | Russian |
|---|---|
| Projects | Проекты |
| Points | Розыгрыши |
| Stats (sidebar), Statistics | Статистика |
| Colors & Transform | Цвет и кадр |
| Thumbnail | Обложка |
| Export | Экспорт |

### Open questions for the reviewer

- "розыгрыш" for a marked point and "очко" for a score point are two terms
  for one English word. Check that each text uses the correct one.
- "Больше-меньше" for the advantage rule, and "Из {count}" for "Best of
  {count}".
- "обложка" for the YouTube thumbnail. YouTube itself says "значок видео".

## Languages after Russian

The author selected these languages on 2026-10-20 (plan, "Decisions",
"Next languages"). Each section gets its term tables with the machine
draft of its task (E7, E8). Until then, each section has only the style.

### Portuguese (`pt`, `pt-PT`)

The app has two Portuguese variants:

- "Português (Brasil)" (`pt`): the files `*_pt.properties`. They have all
  texts.
- "Português (Portugal)" (`pt-PT`): the files `*_pt_PT.properties`. They
  have only the texts that are different from Brazil. When you change a
  Brazil text that has a word of the table "Words of each variant", change
  the Portugal text too. `MessageCatalogTest` ("Portuguese of Portugal has
  no words of Brazil") finds a missing Portugal text.

Style:

- Sentences that tell the user what to do use the imperative without a
  pronoun: "Selecione", "Marque". Both variants use this form.
- Brazil can use "você" in a sentence. Portugal does not use "você". It
  uses the verb without a pronoun ("Pode mudar"), and "si" after a
  preposition ("para si").
- Buttons and menu items use the infinitive: "Exportar", "Salvar a
  miniatura".
- The app is "o app" in Brazil and "a app" in Portugal.
- Brazil avoids the gerund in short status texts ("Verificação do vídeo…",
  "Em andamento"). Portugal uses "a" + infinitive ("a calcular…") or a
  noun.
- Portugal puts the object pronoun after the verb in a main clause:
  "O servidor envia-o", "mostra-a".
- Quotes are "…". A percentage has no space: "{percent}%".

#### Sport terms

| English | Portuguese | Note |
|---|---|---|
| point | ponto | |
| game | game (`pt`), jogo (`pt-PT`) | Brazilian TV says "game". The badge "GAME" / "JOGO" |
| set | set | Plural "sets" |
| tiebreak | tie-break | Also "match tie-break" and "super tie-break" |
| deuce | iguais | "no terceiro iguais" |
| advantage | vantagem | "No-ad" = "Sem vantagem" |
| golden point | ponto de ouro | |
| star point | ponto estrela | |
| deciding point | ponto decisivo | |
| serve, server | saque, quem saca (`pt`); serviço, quem serve (`pt-PT`) | |
| return | devolução | "Return games won" = "Games de devolução ganhos" |
| break point | break point | |
| set point, match point | set point, match point | |
| player | jogador | Default name "Jogador 1" |
| team (padel) | dupla | Default name "Dupla 1" |
| singles, doubles | simples, duplas | Thumbnail match type |
| padel, tennis | padel, tênis (`pt`), ténis (`pt-PT`) | |
| beach tennis | beach tennis (`pt`), ténis de praia (`pt-PT`) | Brazilian players use the English name |
| Americano | Americano | |
| best of 3 sets | melhor de 3 sets | |
| momentum | dinâmica | |
| no point (outcome) | sem ponto | |

#### App terms

| English | Brazil (`pt`) | Portugal (`pt-PT`) |
|---|---|---|
| video | vídeo | vídeo |
| project | projeto | projeto |
| export | exportação, exportar | exportação, exportar |
| scoreboard | placar | marcador |
| score (the numbers) | placar | resultado |
| Override score | Corrigir placar | Corrigir resultado |
| Scoring settings | Configurações do placar | Definições do marcador |
| favorite | favorito | favorito |
| comment | comentário | comentário |
| thumbnail | miniatura | miniatura |
| statistics card | cartão de estatísticas | cartão de estatísticas |
| Transform | Enquadramento | Enquadramento |
| playhead | cursor de reprodução | cursor de reprodução |
| seek bar | barra de busca | barra de procura |
| frame | quadro | fotograma |
| frame rate | taxa de quadros | taxa de fotogramas |
| preview | prévia | pré-visualização |
| encoder | codificador | codificador |
| bitrate | taxa de bits | taxa de bits |
| queue | fila | fila |
| log files | arquivos de log | ficheiros de registo |
| feedback (sidebar) | Feedback | Feedback |
| Settings | Configurações | Definições |
| tab | aba | separador (masculine) |
| Shift, Enter, Space, Delete | Shift, Enter, Espaço, Delete | the same |

#### Words of each variant

| English | Brazil (`pt`) | Portugal (`pt-PT`) |
|---|---|---|
| screen | tela | ecrã (masculine) |
| file | arquivo | ficheiro |
| save | salvar | guardar |
| download | baixar, download | transferir, transferência |
| delete | excluir | eliminar |
| user | usuário | utilizador |
| press (a key) | pressione | prima |
| type (text) | digite | escreva |
| mouse | mouse | rato |
| phone | celular | telemóvel |
| sport | esporte | desporto |
| control (slider) | controle | controlo |
| search | busca | pesquisa |
| court | quadra | campo |
| gray | cinza | cinzento |
| below | embaixo | em baixo |
| live | ao vivo | em direto |
| in a | em um, em uma | num, numa |

#### Short forms

| English | Portuguese | Control |
|---|---|---|
| Colors & Transform (sidebar) | Cor e enquadr. | Sidebar button. The tab title is "Cor e enquadramento" |
| Stats (sidebar) | Estatíst. | Sidebar button |
| COMMENTS | COMENT. | Track name on the timeline of the Points tab |
| {name} scored | Ponto: {name} | Outcome buttons of the Points tab |
| Import a match video | Importe uma partida | Step 1 of the new-project workflow |
| Make a YouTube thumbnail | Faça a miniatura | Step 6 of the new-project workflow |
| Tune the image at any time | Ajuste a imagem | Optional step of the new-project workflow |

#### Open questions for the reviewers

- "game" (Brazil) or "jogo" in all texts.
- "placar" for both the scoreboard and the score in Brazil.
- "Dê cada ponto" for "Score each point" (step 3 of the workflow).
- The CLDR rule of Brazil gives "0 ponto", not "0 pontos".
- The Portugal texts come from a word list and a manual check. A reviewer
  from Portugal must read all texts, also the texts that come from Brazil.

### French (`fr`)

One variant for all countries (files `*_fr.properties`). Windows `fr-FR`,
`fr-BE`, `fr-CH`, `fr-CA`, and all other French locales give it.

Style:

- Sentences that tell the user what to do use "vous": "Sélectionnez",
  "Marquez".
- Buttons and menu items use the infinitive: "Exporter", "Enregistrer la
  miniature".
- The app is "l’app" (feminine): "L’app enregistre", "{app} est déjà
  ouverte".
- Quotes are « … ». A name of a tab or a button in a sentence has no
  quotes, as in English, except where the English text has quotes.
- A no-break space (U+00A0) comes before ":", ";", "?", "!", and "%", and
  inside « … ». Also between a number and its unit: "12 s", "2 MB".
  `MessageCatalogTest` ("French texts use the French spacing and
  apostrophe") finds a normal space.
- The apostrophe is the typographic apostrophe (’). A straight apostrophe
  before a placeholder ("d'{app}") starts an ICU quote and hides the
  placeholder.
- The past participle with "avoir" does not agree with a player name. Thus
  "{name} a gagné le point" is correct for all names.
- Plurals have `one` and `other`. `one` is for 0, 1, and the numbers with
  decimals below 2 ("0 point", "1,5 seconde").
- The number sign is "nº": "Modifier le point nº {number}".

#### Sport terms

| English | French | Note |
|---|---|---|
| point | point | |
| game | jeu | Plural "jeux". The badge "JEU" |
| set | set | |
| tiebreak | tie-break | Also "match tie-break" and "super tie-break" |
| deuce | égalité | The deuce rule: "règle d’égalité" |
| advantage | avantage | "No-ad" = "Sans avantage" |
| golden point | point en or | |
| star point | star point | French padel media use the English name |
| deciding point | point décisif | |
| serve, server | service, serveur | "{name} au service", "changer de serveur" |
| return | retour | "Return games won" = "Jeux de retour gagnés" |
| break point, set point, match point | balle de break, balle de set, balle de match | "Break points saved" = "Balles de break sauvées" |
| player | joueur | Default name "Joueur 1" |
| team (padel) | équipe | Default name "Équipe 1" |
| singles, doubles | simple, double | Thumbnail match type |
| padel, tennis | padel, tennis | |
| beach tennis | beach tennis | The name that the FFT uses |
| Americano | Americano | |
| pro set | pro set | |
| best of 3 sets | au meilleur des 3 sets | |
| momentum | dynamique | |
| no point (outcome) | sans point | |
| scored (a point with a winner) | attribué | "Score each point" = "Attribuez les points" |

#### App terms

| English | French | Note |
|---|---|---|
| video | vidéo | Feminine |
| project | projet | |
| export (noun, verb) | export, exporter | |
| scoreboard | tableau de score | |
| score (the numbers) | score | "Final score" = "Score final" |
| Override score | Corriger le score | |
| Scoring settings | Réglages du score | |
| Scoreboard style | Style du tableau de score | |
| favorite | favori | "Only favorites" = "Favoris seulement" |
| comment | commentaire | |
| thumbnail | miniature | |
| statistics card | carte des statistiques | |
| set summary | bilan du set | "Set summaries" = "Bilans des sets" |
| change point | repère de changement | Not "point", because "point" is a tennis point |
| Transform | Cadrage | The tab "Colors & Transform" = "Couleurs et cadrage" |
| crop rectangle | cadre | |
| playhead | tête de lecture | |
| seek bar | barre de lecture | |
| timeline | frise | |
| frame | image | "Frame step" = "Image par image" |
| frame rate | fréquence d’images | |
| encoder | encodeur | |
| bitrate | débit | |
| queue | file d’attente | |
| log files | fichiers journaux | |
| feedback (sidebar) | Avis | The dialog title is "Envoyer un avis" |
| Settings | Paramètres | |
| More | Plus | |
| Help | Aide | |
| email | e-mail | |
| Shift, Enter, Space, Delete, Esc | Maj, Entrée, Espace, Suppr, Échap | The names on French keyboards. "Shift+R" = "Maj+R" |

#### Short forms

| English | French | Control |
|---|---|---|
| Colors & Transform (sidebar) | Couleurs / et cadrage | Sidebar button on two lines |
| COMMENTS | COMMENT. | Track name on the timeline of the Points tab |
| {name} scored | Point : {name} | Outcome buttons of the Points tab |
| Import a match video | Importez un match | Step 1 of the new-project workflow |
| Make a YouTube thumbnail | Créez la miniature | Step 6 of the new-project workflow |
| Tune the image at any time | Réglez l’image | Optional step of the new-project workflow |

#### Tab names

| English | French |
|---|---|
| Projects | Projets |
| Points | Points |
| Stats (sidebar), Statistics | Stats, Statistiques |
| Colors & Transform | Couleurs et cadrage |
| Thumbnail | Miniature |
| Export | Export |

#### Open questions for the reviewer

- "star point" (the English name) or "point étoile".
- "tableau de score" for the scoreboard. Other options: "tableau
  d’affichage", "marqueur".
- "Avis" for the Feedback button.
- "l’app" (feminine) in all texts, or "l’application" in long sentences.
- Quebec uses "courriel" for "e-mail". The draft uses "e-mail" for all
  countries.

### German (`de`)

One variant for all countries (files `*_de.properties`), with "ß". Windows
`de-DE`, `de-AT`, `de-CH`, and all other German locales give it.

Style:

- Sentences that tell the user what to do use the "du" imperative: "Wähle",
  "Markiere". "du" and "dein" are in lower case.
- Buttons and menu items use the infinitive: "Exportieren", "Thumbnail
  speichern".
- Quotes are „…“ (U+201E and U+201C). `MessageCatalogTest` ("German texts
  use the German quotes") finds other quotes and the straight apostrophe.
- A name of a tab or a button in a sentence has no quotes, as in English:
  "Wähle Nur Punkte", "im Tab Punkte".
- A score uses a colon: "40:40", "Tiebreak bei 6:6".
- A percentage and a unit have a space: "{percent} %", "12 s", "2 MB".
- The number sign is "Nr.": "Punkt Nr. {number} bearbeiten".
- Plurals have `one` and `other`. `one` is only for 1 without decimals
  ("0 Punkte", "1,5 Sekunden"). The dative plural has "-n": "führt mit
  3 Punkten".
- Long compound nouns are normal. The UI breaks a word that is wider than
  the line (plan section 7), but prefer a short word in a narrow control.

#### Sport terms

| English | German | Note |
|---|---|---|
| point | Punkt | Plural "Punkte" |
| game | Spiel | Plural "Spiele". The badge "SPIEL" |
| set | Satz | Plural "Sätze" |
| tiebreak | Tiebreak | Also "Match-Tiebreak" and "Super-Tiebreak" |
| deuce | Einstand | The deuce rule: "Einstandsregel" |
| advantage | Vorteil | "No-ad" = "Ohne Vorteil" |
| golden point | Golden Point | German padel uses the English name |
| star point | Star Point | |
| deciding point | Entscheidungspunkt | The video badge "ENTSCHEIDUNGSPUNKT" (18 letters) |
| deciding set | Entscheidungssatz | |
| serve, server | Aufschlag, Aufschläger | "{name} schlägt auf" |
| return | Return | "Return games won" = "Returnspiele gewonnen" |
| break point, set point, match point | Breakball, Satzball, Matchball | "Break points won" = "Breakbälle genutzt", "Break points saved" = "Breakbälle abgewehrt" |
| player | Spieler | Default name "Spieler 1" |
| team (padel) | Team | Default name "Team 1" |
| singles, doubles | Einzel, Doppel | Thumbnail match type |
| match | Match | Neuter: "das Match", "des Matchs" |
| padel, tennis | Padel, Tennis | |
| beach tennis | Beachtennis | |
| Americano | Americano | |
| pro set | Pro-Satz | |
| best of 3 sets | Best of 3 Sätze | |
| momentum | Momentum | |
| no point (outcome) | Kein Punkt | |
| scored (a point with a winner) | gewertet | "Score each point" = "Punkte werten" |

#### App terms

| English | German | Note |
|---|---|---|
| video | Video | Neuter |
| project | Projekt | |
| export (noun, verb) | Export, exportieren | |
| scoreboard | Spielstandsanzeige | |
| score (the numbers) | Spielstand | "Final score" = "Endstand" |
| Override score | Spielstand korrigieren | |
| Scoring settings | Spielstand-Einstellungen | |
| Scoreboard style | Stil der Spielstandsanzeige | |
| favorite | Favorit | "Only favorites" = "Nur Favoriten" |
| comment | Kommentar | |
| thumbnail | Thumbnail | The YouTube word in German |
| statistics card | Statistikkarte | |
| set summary | Satzübersicht | |
| change point | Änderungsmarke | Not "Punkt", because "Punkt" is a tennis point |
| Transform | Ausschnitt | The tab "Colors & Transform" = "Farben & Ausschnitt" |
| crop rectangle | Zuschnitt-Rechteck | |
| playhead | Abspielposition | |
| seek bar | Fortschrittsleiste | |
| timeline | Zeitleiste | |
| frame | Bild, Einzelbild | "Frame step" = "Einzelbild" |
| frame rate | Bildrate | |
| encoder | Encoder | |
| bitrate | Bitrate | |
| queue | Warteschlange | |
| log files | Logdateien | |
| feedback (sidebar) | Feedback | |
| Settings | Einstellungen | |
| More | Mehr | |
| Help | Hilfe | |
| email | E-Mail | |
| theme | Design | |
| Shift, Enter, Space, Delete, Esc | Umschalt, Enter, Leertaste, Entf, Esc | The names on German keyboards. "Shift+R" = "Umschalt+R" |

#### Short forms

| English | German | Control |
|---|---|---|
| Colors & Transform (sidebar) | Farben & / Ausschnitt | Sidebar button on two lines |
| Stats (sidebar) | Statistik | Sidebar button |
| {name} scored | Punkt: {name} | Outcome buttons of the Points tab |
| Add here | Hier setzen | Change points card |

#### Tab names

| English | German |
|---|---|
| Projects | Projekte |
| Points | Punkte |
| Stats (sidebar), Statistics | Statistik |
| Colors & Transform | Farben & Ausschnitt |
| Thumbnail | Thumbnail |
| Export | Export |

#### Open questions for the reviewer

- "Spielstandsanzeige" for the scoreboard. Other options: "Scoreboard",
  "Anzeigetafel".
- "Entscheidungspunkt" for the deciding point, also as the video badge.
- "Thumbnail" or "Miniaturbild".
- "Ausschnitt" for Transform, and "Änderungsmarke" for a change point.
- "Breakbälle genutzt" (break points won) and "Breakbälle abgewehrt" (break
  points saved).
- The timeline track "KOMMENTARE" is longer than "COMMENTS". Check that it
  fits.

### Japanese (`ja`)

One variant (files `*_ja.properties`). Windows `ja-JP` and `ja` give it.

Style:

- Sentences use the polite です/ます form: "選択してください",
  "保存されます".
- Buttons and menu items use short noun forms or a short verb form:
  "エクスポート", "保存", "フォルダーを開く".
- Quotes are 「…」. A name of a tab or a button in a sentence is in 「…」:
  "「スコアを修正」をクリックします". The tab names with "タブ" have no
  quotes: "ポイントタブ". `MessageCatalogTest` ("Japanese texts use the
  Japanese quotes") finds other quotes and the straight apostrophe.
- Punctuation is full-width: "、", "。", "（…）", "？", "！". A colon after a
  label is the half-width ": ".
- A number and a unit have a space when the unit is a word: "3 ポイント",
  "{seconds} 秒", "2 MB". A short count of a default name has no space:
  "プレイヤー1", "ペア1".
- Plurals have only `other` (CLDR). A Japanese noun has no plural form.
- Katakana words with the long vowel mark use the modern forms of the
  Windows UI: "フォルダー", "ブラウザー", "エンコーダー", "ユーザー".
- Key names: "Space", "Delete", "Enter", "Esc", and "Shift" as on the
  keyboard, and arrows for the arrow keys ("←", "→", "↑", "↓").

#### Sport terms

| English | Japanese | Note |
|---|---|---|
| point | ポイント | |
| game | ゲーム | |
| set | セット | "Best of 3 sets" = "3 セットマッチ" |
| tiebreak | タイブレーク | Also "マッチタイブレーク" and "スーパータイブレーク" |
| deuce | デュース | |
| advantage | アドバンテージ | "No-ad" = "ノーアド" |
| golden point | ゴールデンポイント | |
| star point | スターポイント | |
| deciding point | 決定ポイント | The video badge "決定ポイント" |
| deciding set | 最終セット | |
| serve, server | サーブ, サーバー | "{name} がサーブ" |
| return | リターン | |
| break point, set point, match point | ブレークポイント, セットポイント, マッチポイント | "Break points saved" = "ブレークポイントのセーブ" |
| player | プレイヤー | Default name "プレイヤー1" |
| team (padel) | ペア | Default name "ペア1". "Team" in Americano texts = "チーム" |
| singles, doubles | シングルス, ダブルス | |
| match | 試合 | |
| padel, tennis | パデル, テニス | |
| beach tennis | ビーチテニス | |
| Americano | アメリカーノ | |
| pro set | プロセット | |
| momentum | 流れ | |
| no point (outcome) | 得点なし | |
| scored (a point with a winner) | 記録済み | "Score each point" = "各ポイントを記録する" |

#### App terms

| English | Japanese | Note |
|---|---|---|
| video | 動画 | |
| project | プロジェクト | |
| export (noun, verb) | エクスポート, エクスポートする | Not "書き出し" |
| scoreboard | スコアボード | |
| score | スコア | "Final score" = "最終スコア" |
| Override score | スコアを修正 | |
| Scoring settings | スコアの設定 | |
| Scoreboard style | スコアボードのスタイル | |
| favorite | お気に入り | |
| comment | コメント | |
| thumbnail | サムネイル | |
| statistics card | 統計カード | |
| set summary | セットのまとめ | |
| change point | 変化点 | |
| Transform | トリミング | The tab "Colors & Transform" = "色とトリミング" |
| playhead | 再生位置 | |
| seek bar | シークバー | |
| timeline | タイムライン | |
| frame | フレーム | "Frame step" = "コマ送り" |
| frame rate | フレームレート | |
| encoder | エンコーダー | |
| bitrate | ビットレート | |
| queue | 待機列 | A queued export = "待機中" |
| log files | ログファイル | |
| feedback | フィードバック | |
| Settings | 設定 | |
| More | その他 | |
| Help | ヘルプ | |
| email | メール | |
| theme | テーマ | |

#### Tab names

| English | Japanese |
|---|---|
| Projects | プロジェクト |
| Points | ポイント |
| Stats (sidebar), Statistics | 統計 |
| Colors & Transform | 色とトリミング (sidebar: "色と / トリミング") |
| Thumbnail | サムネイル |
| Export | エクスポート |

#### Open questions for the reviewer

- "決定ポイント" for the deciding point, also as the video badge. Another
  option: "デサイディングポイント" (long).
- "トリミング" for Transform. The tab also zooms and rotates. Other
  options: "構図", "変形".
- "ペア" for a padel team. Another option: "チーム".
- "流れ" for momentum. Another option: "モメンタム".
- "記録済み" for a scored point.
- The arrows as key names ("←") in place of words ("左").

### Simplified Chinese (`zh-Hans`)

One variant (files `*_zh_Hans.properties`). Windows `zh-CN`, `zh-SG`,
`zh-Hans`, and `zh` give it. The detection gives it to all Chinese locales
without the script `Hant` and outside Taiwan, Hong Kong, and Macau.

Style:

- Sentences use "你" and the polite "请" for an instruction: "请选择比赛视频".
- Buttons and menu items use a short verb or noun: "导出", "保存封面",
  "打开文件夹".
- Quotes are “…”. A name of a tab or a button in a sentence is in “…”:
  "点击“修改比分”". The tab names with "标签页" have no quotes:
  "回合标签页". Two quoted names next to each other have no "、" between
  them: "“收藏”“编辑”和“删除”". `MessageCatalogTest` ("Chinese texts use the quotes of
  their script") finds other quotes and the straight apostrophe.
- Punctuation is full-width: "，", "。", "：", "；", "（…）", "？", "！".
- A half-width space separates Chinese from Latin letters, digits, and
  placeholders: "导出 1080p 视频", "{count} 个回合", "球员 1". No space comes
  next to a full-width mark.
- A percentage has no space: "{percent}%".
- Plurals have only `other` (CLDR). A count uses a measure word: "# 个回合",
  "# 局", "# 盘", "# 分", "# 页", "# 天".
- Do not say that YouTube is the only place to share a video. YouTube is
  blocked in mainland China. "YouTube thumbnail" is "视频封面", and "YouTube
  search" is "视频平台的搜索结果". A fact about YouTube keeps the name
  ("YouTube 只接受 2 MB 或更小的封面").
- Key names: "空格" for Space. "Shift", "Enter", "Esc", and "Delete" stay as
  on the keyboard. The arrow keys are "←", "→", "↑", "↓".

#### Sport terms

| English | Simplified Chinese | Note |
|---|---|---|
| point (a marked part of the video, a rally) | 回合 | The Points tab is "回合". "Only points" = "仅回合" |
| point (a unit of the score) | 分 | "Points won" = "得分" |
| game | 局 | |
| set | 盘 | "Best of 3 sets" = "三盘两胜", "Best of {count}" = "{count} 盘制" |
| tiebreak | 抢七 | "Match tiebreak" = "决胜抢十", "super tiebreak" = "超级抢十" |
| deuce | 平分 | |
| advantage | 占先 | "No-ad" = "无占先" |
| golden point | 金球 | Also the video badge |
| star point | 星分 | |
| deciding point | 决胜分 | Also the video badge |
| deciding set | 决胜盘 | |
| serve, server | 发球, 发球方 | "Switch the server" = "更换发球方" |
| return | 接发球 | "Return games won" = "接发球局获胜" |
| break point, set point, match point | 破发点, 盘点, 赛点 | "Break points saved" = "挽救破发点" |
| player | 球员 | Default name "球员 1" |
| team (padel) | 组合 | Default name "组合 1". "Team" in Americano texts = "队" |
| singles, doubles | 单打, 双打 | |
| match | 比赛 | |
| padel, tennis | 板式网球, 网球 | |
| beach tennis | 沙滩网球 (`zh-Hans`), 沙灘網球 (`zh-Hant`) | |
| Americano | Americano | |
| pro set | 长盘 | |
| momentum | 势头 | |
| pressure (statistics group) | 关键分 | |
| no point (outcome) | 不计分 | |
| scored | 已计分 | "Score each point" = "为每个回合计分" |

#### App terms

| English | Simplified Chinese | Note |
|---|---|---|
| app | 应用 | |
| video | 视频 | |
| project | 项目 | |
| export (noun, verb) | 导出 | |
| import | 导入 | |
| scoreboard | 记分牌 | |
| score | 比分 | "Final score" = "最终比分" |
| Override score | 修改比分 | |
| Scoring settings | 计分设置 | |
| Scoreboard style | 记分牌样式 | |
| favorite | 收藏 | "Only favorites" = "仅收藏", a star = "标星" |
| comment | 评论 | |
| thumbnail | 封面 | |
| statistics card | 统计卡片 | |
| set summary | 盘总结 | |
| change point | 变化点 | |
| Transform | 构图 | The tab "Colors & Transform" = "颜色与构图" |
| crop rectangle | 裁剪框 | |
| playhead | 播放头 | |
| seek bar | 进度条 | |
| timeline | 时间轴 | |
| frame | 帧 | "Frame step" = "逐帧" |
| frame rate | 帧率 | |
| resolution | 分辨率 | |
| bitrate | 码率 | |
| encoder | 编码器 | |
| queue | 队列 | A queued export = "排队中" |
| log files | 日志文件 | |
| feedback | 反馈 | |
| Settings | 设置 | |
| More | 更多 | |
| Help | 帮助 | |
| email | 电子邮件 | The short label is "邮箱" |
| theme | 主题 | |
| tab | 标签页 | |

#### Tab names

| English | Simplified Chinese |
|---|---|
| Projects | 项目 |
| Points | 回合 |
| Stats (sidebar), Statistics | 统计 |
| Colors & Transform | 颜色与构图 (sidebar: "颜色与 / 构图") |
| Thumbnail | 封面 |
| Export | 导出 |

#### Open questions for the reviewer

- "回合" for a marked point and "分" for a score point are two terms for
  one English word, as in Russian. Check that each text uses the correct
  one. Another option: "分" in all texts.
- "星分" for the star point. The term is new in padel, and no Chinese
  term is common yet.
- "构图" for Transform. The tab also zooms and rotates. Other options:
  "画面", "裁剪".
- "评论" for a comment in the video. Other options: "注释", "文字".
- "组合" for a padel team.
- "封面" for the thumbnail, and "视频平台" in place of YouTube.
- "长盘" for the pro set.

### Traditional Chinese (`zh-Hant`)

One variant with the Taiwan terms (files `*_zh_Hant.properties`). Windows
`zh-TW`, `zh-HK`, `zh-MO`, and `zh-Hant` give it. A full catalog, not a
child of `zh-Hans`. The draft started from the Simplified texts and
changed the characters and the terms.

Style:

- The style of Simplified Chinese, with these differences.
- Quotes are 「…」. A name of a tab or a button in a sentence is in 「…」.
  Two or more names in a list have "、" between them: "「收藏」、「編輯」和「刪除」".
- Use the Taiwan terms, not a character conversion of the mainland terms.
  `MessageCatalogTest` ("Traditional Chinese uses the Taiwan terms") finds
  the common mainland terms, for example "視頻", "導出", "默認", "信息",
  "軟件", "網絡", "質量", "幀".
- "列" is a row and "欄" is a column (the reverse of the mainland use).
- YouTube is common in Taiwan. The texts keep "YouTube" as in English:
  "製作 YouTube 縮圖".
- Key names: "空白鍵" for Space. The other keys as in Simplified Chinese.
- Microsoft JhengHei UI has no "−" (U+2212). The texts use "－" (U+FF0D).
  `ScriptFontsTest` ("the UI font of each script shows all characters of
  its catalog") finds a character that the UI font does not have.

#### Terms that are different from Simplified Chinese

| English | Simplified Chinese | Traditional Chinese (Taiwan) |
|---|---|---|
| app | 应用 | 應用程式 |
| video | 视频 | 影片 |
| project | 项目 | 專案 |
| export, import | 导出, 导入 | 匯出, 匯入 |
| file, folder | 文件, 文件夹 | 檔案, 資料夾 |
| settings | 设置 | 設定 |
| default | 默认 | 預設 |
| help | 帮助 | 說明 |
| save | 保存 | 儲存 |
| open | 打开 | 開啟 |
| click | 点击 | 點選 |
| select | 选择 | 選取 |
| tab | 标签页 | 分頁 |
| scoreboard | 记分牌 | 記分板 |
| comment | 评论 | 註解 |
| thumbnail | 封面 | 縮圖 |
| playhead | 播放头 | 播放指標 |
| seek bar | 进度条 | 進度列 |
| slider | 滑块 | 滑桿 |
| control | 控件 | 控制項 |
| handle | 手柄 | 控點 |
| crop | 裁剪 | 裁切 |
| highlights | 高光 | 亮部 |
| frame | 帧 | 影格, 格 ("Frame step" = "逐格") |
| frame rate | 帧率 | 影格速率 |
| resolution | 分辨率 | 解析度 |
| bitrate | 码率 | 位元速率 |
| quality | 质量 | 品質 |
| queue | 队列 | 佇列 |
| log files | 日志文件 | 記錄檔 |
| feedback | 反馈 | 意見回饋 |
| report (a problem) | 报告 | 回報 |
| send | 发送 | 傳送 |
| privacy | 隐私 | 隱私權 |
| server | 服务器 | 伺服器 |
| network | 网络 | 網路 |
| software, hardware | 软件, 硬件 | 軟體, 硬體 |
| graphics card | 显卡 | 顯示卡 |
| burn in | 烧录 | 燒入 |
| momentum | 势头 | 氣勢 |
| advantage | 占先 | 佔先 |
| set point, match point | 盘点, 赛点 | 盤末點, 賽末點 |
| Break points saved | 挽救破发点 | 化解破發點 |
| duration | 时长 | 長度, 持續時間 |
| Smart App Control | 智能应用控制 | 智慧型應用程式控制 |

#### Tab names

| English | Traditional Chinese |
|---|---|
| Projects | 專案 |
| Points | 回合 |
| Stats (sidebar), Statistics | 統計 |
| Colors & Transform | 顏色與構圖 (sidebar: "顏色與 / 構圖") |
| Thumbnail | 縮圖 |
| Export | 匯出 |

#### Open questions for the reviewer

- The open questions of Simplified Chinese, with the Taiwan terms.
- "註解" for a comment in the video. Other options: "留言", "評論".
- "盤末點" and "賽末點" (Taiwan) or "盤點" and "賽點".
- "應用程式" in all texts, or "App" in short texts.
- A reviewer from Hong Kong checks if the Taiwan terms are clear there
  (for example "影片" and "視頻", "滑鼠" and "鼠標").
