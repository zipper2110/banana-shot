// Serves the files of the site. For each HTML answer, it tells the analytics Worker the path, the status, and the
// user agent. The analytics Worker keeps only the requests of bots (site/README.md, "Bot counter").
export default {
  async fetch(request, env, ctx) {
    const response = await env.ASSETS.fetch(request);
    const type = response.headers.get("content-type") || "";
    if (request.method === "GET" && type.startsWith("text/html")) {
      const report = env.ANALYTICS.pageRequest(new URL(request.url).pathname, response.status, request.headers.get("user-agent") || "");
      // A failure of the counter must not change the answer.
      ctx.waitUntil(Promise.resolve(report).catch(() => {}));
    }
    return response;
  },
};
