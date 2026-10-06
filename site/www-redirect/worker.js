// Send each request to the same path and query on the domain without www.
export default {
  fetch(request) {
    const url = new URL(request.url);
    url.hostname = "banana-shot-editor.app";
    url.protocol = "https:";
    url.port = "";
    return Response.redirect(url.toString(), 301);
  },
};
