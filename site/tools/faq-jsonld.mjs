// Writes the FAQPage JSON-LD of public/faq/index.html from the questions on the page. The JSON-LD must
// have the same text as the page, thus do not edit the block by hand. Run this script after each change of the FAQ:
//
//   node site/tools/faq-jsonld.mjs           writes the block
//   node site/tools/faq-jsonld.mjs --check   only checks the block, exit code 1 if it is old
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const FILE = fileURLToPath(new URL("../public/faq/index.html", import.meta.url));
const START = '<script type="application/ld+json" id="faq-jsonld">';
const END = "</script>";

const ENTITIES = { nbsp: " ", amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", "#39": "'", copy: "©", rarr: "→" };

function toText(html) {
  return html
    .replace(/<\/(p|li)>/g, " ")
    .replace(/<[^>]+>/g, "")
    .replace(/&([a-z]+|#\d+);/g, (m, name) => {
      if (!(name in ENTITIES)) throw new Error(`Unknown entity ${m}. Add it to ENTITIES.`);
      return ENTITIES[name];
    })
    .replace(/\s+/g, " ")
    .trim();
}

const html = readFileSync(FILE, "utf8");
const questions = [...html.matchAll(/<details id="[^"]+"[^>]*>\s*<summary>([\s\S]*?)<\/summary>\s*<div>([\s\S]*?)<\/div>\s*<\/details>/g)]
  .map(([, question, answer]) => ({
    "@type": "Question",
    name: toText(question),
    acceptedAnswer: { "@type": "Answer", text: toText(answer) },
  }));
if (questions.length === 0) throw new Error("No questions found. Did the markup of the FAQ change?");

const data = { "@context": "https://schema.org", "@type": "FAQPage", mainEntity: questions };
// "</" in a string must not close the script element.
// Keep the line ends of the file (CRLF or LF).
const eol = html.includes("\r\n") ? "\r\n" : "\n";
const json = JSON.stringify(data, null, 2).replace(/<\//g, "<\\/").replace(/\n/g, `${eol}  `);
const block = `${START}${eol}  ${json}${eol}  ${END}`;

const start = html.indexOf(START);
const updated = start >= 0
  ? html.slice(0, start) + block + html.slice(html.indexOf(END, start) + END.length)
  : html.replace("</head>", `  <!-- Made by site/tools/faq-jsonld.mjs. Do not edit by hand. -->${eol}  ${block}${eol}</head>`);

if (process.argv.includes("--check")) {
  if (updated !== html) {
    console.error("The FAQ JSON-LD is old. Run: node site/tools/faq-jsonld.mjs");
    process.exit(1);
  }
  console.log(`The FAQ JSON-LD is current (${questions.length} questions).`);
} else {
  writeFileSync(FILE, updated);
  console.log(`Wrote the FAQ JSON-LD (${questions.length} questions).`);
}
