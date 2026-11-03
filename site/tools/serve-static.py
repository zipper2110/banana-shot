"""Serves site/public on http://127.0.0.1:<port>/ for a local preview of the site.

The plain "python3 -m http.server" sends no Cache-Control header, so the browser can keep an old site.css or
script. This server sends "Cache-Control: no-cache", as the live site sends "max-age=0, must-revalidate".

Run from the repository root: python3 site/tools/serve-static.py [port]
"""
import functools
import http.server
import os
import sys


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8792
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "public")
    handler = functools.partial(NoCacheHandler, directory=os.path.normpath(root))
    with http.server.ThreadingHTTPServer(("127.0.0.1", port), handler) as server:
        print(f"Serving {os.path.normpath(root)} on http://127.0.0.1:{port}/", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
