#!/usr/bin/env python3
"""
Self-Hosted Backend Proxy for InstaMediaSaver Android App.
Keeps your secret Meta Instagram Graph API tokens on the server (never inside the APK).

Usage (FastAPI / Uvicorn - Recommended for Production):
  export IG_ACCESS_TOKEN="EAAB..."
  export IG_USER_ID="1784140..."
  export CLIENT_BEARER_TOKEN="optional-secret-between-app-and-proxy"
  uvicorn main:app --host 0.0.0.0 --port 8000

Usage (Zero-dependency Python standard library server for quick local testing):
  python3 main.py
"""
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any, Dict, Optional

IG_ACCESS_TOKEN = os.environ.get("IG_ACCESS_TOKEN", "")
IG_USER_ID = os.environ.get("IG_USER_ID", "")
CLIENT_BEARER_TOKEN = os.environ.get("CLIENT_BEARER_TOKEN", "")
GRAPH_API_VERSION = "v26.0"
GRAPH_BASE_URL = os.environ.get("GRAPH_BASE_URL", f"https://graph.facebook.com/{GRAPH_API_VERSION}")

SHORTCODE_RE = re.compile(
    r"^https?://(?:www\.)?(?:instagram\.com|instagr\.am)/(?:[A-Za-z0-9_.]+/)?(p|reel|reels)/([A-Za-z0-9_-]{5,32})(?:[/?#].*)?$",
    re.IGNORECASE,
)


def parse_instagram_url(raw_url: str) -> Optional[Dict[str, str]]:
    if not raw_url:
        return None
    m = SHORTCODE_RE.match(raw_url.strip())
    if not m:
        return None
    kind = "reel" if m.group(1).lower() == "reels" else m.group(1).lower()
    shortcode = m.group(2)
    return {
        "kind": kind,
        "shortcode": shortcode,
        "canonical_url": f"https://www.instagram.com/{kind}/{shortcode}/",
    }


def normalize_graph_node(node: Dict[str, Any], shortcode: str, canonical_url: str) -> Dict[str, Any]:
    media_id = node.get("id", shortcode)
    media_type = node.get("media_type", "IMAGE")
    caption = node.get("caption")
    username = node.get("username")
    timestamp = node.get("timestamp")

    items = []
    if media_type == "CAROUSEL_ALBUM":
        children = node.get("children", {}).get("data", [])
        for idx, child in enumerate(children, start=1):
            c_type = child.get("media_type", "IMAGE")
            c_url = child.get("media_url")
            c_thumb = child.get("thumbnail_url") or c_url
            items.append({
                "id": child.get("id", f"{media_id}_{idx}"),
                "index": idx,
                "kind": "VIDEO" if c_type in ("VIDEO", "REELS") else "IMAGE",
                "media_url": c_url,
                "thumbnail_url": c_thumb,
                "width": None,
                "height": None,
                "is_downloadable": bool(c_url),
                "restriction_reason": None if c_url else (
                    "media_url omitted by Meta Graph API (copyrighted audio or Reel download control)"
                ),
            })
    else:
        m_url = node.get("media_url")
        m_thumb = node.get("thumbnail_url") or m_url
        items.append({
            "id": media_id,
            "index": 1,
            "kind": "VIDEO" if media_type in ("VIDEO", "REELS") else "IMAGE",
            "media_url": m_url,
            "thumbnail_url": m_thumb,
            "width": None,
            "height": None,
            "is_downloadable": bool(m_url),
            "restriction_reason": None if m_url else (
                "media_url omitted by Meta Graph API (copyrighted audio or Reel download control)"
            ),
        })

    return {
        "shortcode": shortcode,
        "canonical_url": canonical_url,
        "author_username": username,
        "caption": caption,
        "media_type_label": media_type,
        "timestamp": timestamp,
        "items": items,
        "resolution_source": f"Backend Proxy -> Meta Graph API {GRAPH_API_VERSION}",
        "notice_message": None,
    }


def resolve_shortcode_via_graph_api(
    raw_url: str,
    access_token: str = IG_ACCESS_TOKEN,
    ig_user_id: str = IG_USER_ID,
    graph_base_url: str = GRAPH_BASE_URL,
) -> tuple[int, Dict[str, Any]]:
    parsed = parse_instagram_url(raw_url)
    if not parsed:
        return 400, {"error": "Invalid Instagram post or Reel URL."}
    if not access_token or not ig_user_id:
        return 503, {"error": "Server is missing IG_ACCESS_TOKEN or IG_USER_ID environment variables."}

    fields = (
        "id,caption,media_type,media_url,thumbnail_url,permalink,shortcode,timestamp,username,"
        "children{id,media_type,media_url,thumbnail_url}"
    )
    query = urllib.parse.urlencode({
        "fields": fields,
        "limit": "50",
        "access_token": access_token,
    })
    endpoint = f"{graph_base_url}/{ig_user_id}/media?{query}"

    try:
        req = urllib.request.Request(endpoint, method="GET")
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        return e.code, {"error": f"Graph API returned HTTP {e.code}", "details": err_body}
    except Exception as e:
        return 502, {"error": f"Upstream network error: {e}"}

    for node in payload.get("data", []):
        node_sc = node.get("shortcode")
        permalink = node.get("permalink", "")
        if node_sc == parsed["shortcode"] or f"/{parsed['shortcode']}" in permalink:
            return 200, normalize_graph_node(node, parsed["shortcode"], parsed["canonical_url"])

    return 404, {
        "error": (
            f"Shortcode {parsed['shortcode']} not found in the authenticated Professional "
            "account's permitted media."
        )
    }


# Optional FastAPI ASGI wrapper when `fastapi` is installed
try:
    from fastapi import FastAPI, Header, HTTPException
    from pydantic import BaseModel

    app = FastAPI(title="InstaMediaSaver Backend Proxy", version="1.0.0")

    class ResolveRequest(BaseModel):
        url: str
        shortcode: Optional[str] = None

    @app.post("/api/v1/resolve")
    def api_resolve(body: ResolveRequest, authorization: Optional[str] = Header(default=None)):
        if CLIENT_BEARER_TOKEN:
            expected = f"Bearer {CLIENT_BEARER_TOKEN}"
            if authorization != expected:
                raise HTTPException(status_code=401, detail="Unauthorized client bearer token.")
        status, result = resolve_shortcode_via_graph_api(body.url)
        if status != 200:
            raise HTTPException(status_code=status, detail=result)
        return result
except ImportError:
    app = None


class ProxyHTTPRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/api/v1/resolve":
            self.send_response(404)
            self.end_headers()
            return
        if CLIENT_BEARER_TOKEN:
            auth = self.headers.get("Authorization", "")
            if auth != f"Bearer {CLIENT_BEARER_TOKEN}":
                self.send_response(401)
                self.end_headers()
                self.wfile.write(b'{"error":"Unauthorized"}')
                return
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length).decode("utf-8")
        data = json.loads(raw_body) if raw_body else {}
        status, result = resolve_shortcode_via_graph_api(data.get("url", ""))
        resp_bytes = json.dumps(result).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(resp_bytes)))
        self.end_headers()
        self.wfile.write(resp_bytes)

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    server = HTTPServer(("0.0.0.0", port), ProxyHTTPRequestHandler)
    print(f"InstaMediaSaver Backend Proxy listening on http://0.0.0.0:{port}")
    server.serve_forever()
