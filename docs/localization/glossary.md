# Localization Glossary

This file holds the term tables of the app translations (B-11, E4-T2). Each
language has one table. A translator and a reviewer use the same terms in all
texts of the catalog (`src/main/resources/i18n/`) and of the help.

Rules for all languages:

- Use one translation for each term. Do not use a synonym in a different text.
- If a term is a name in the UI (a tab, a button, a field), use the exact
  catalog text of that name in the help and in the other texts.
- The words in the "Do not translate" list stay as they are.

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

The reviewers of E4-T7 check all texts. If possible, one reviewer is from
Spain and one is from Latin America. There are no open term questions now.

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
| point (a unit of the score) | очко | "Points won" = "Выиграно очков", "No score" = "Без очка" |
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

- Sentences use the polite です/ます form.
- Buttons and menu items use short noun forms: "エクスポート", "保存".
- Quotes are 「…」.

### Simplified Chinese (`zh-Hans`)

- Sentences use "你".
- Quotes are “…”.
- Do not say that YouTube is the only place to share a video.

### Traditional Chinese (`zh-Hant`)

- Sentences use "你".
- Use the Taiwan terms: "影片", "專案", "檔案".
- Quotes are 「…」.
