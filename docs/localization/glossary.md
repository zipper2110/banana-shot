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
| Ad, AD | The advantage score on the scoreboard. The reviewer decides if it stays (see "Open questions") |
| YouTube, Windows, GitHub, Cloudflare, Telegram, Smart App Control | Product names |

## Spanish (`es`)

The Spanish catalog is for Spain and for Latin America. When the two use
different words, the table selects a word that both understand, and the
"Note" column gives the reason.

Style:

- Buttons and menu items use the infinitive: "Exportar", "Guardar la
  miniatura".
- Sentences that tell the user what to do use the "tú" imperative: "Marca
  los puntos", "Selecciona un estilo".
- The app is "la app".
- Spanish quotes are «…».
- A number and the percent sign have a space: "75 %". The compact values in
  the statistics table keep "64%" (see "Open questions").

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
| team (padel) | equipo | Default name "Equipo 1". Padel players also say "pareja". The app uses "equipo", the same word as in the Americano rules |
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
| video | vídeo | Spain writes "vídeo", Latin America writes "video". The catalog uses "vídeo" (see "Open questions") |
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

The reviewer of E4-T7 decides these points:

- "vídeo" (Spain) or "video" (Latin America).
- "equipo" or "pareja" for a padel team.
- The advantage score "Ad" on the scoreboard and in the score controls.
- "64%" or "64 %" in the compact statistics values.
- The tagline "Tennis & Padel Video Editor" under the logo stays English. The
  owner decides if it is a brand text or a translated text.
