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
