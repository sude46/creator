#!/usr/bin/env python3
"""Create or reset synthetic demo works through the public HTTP API.

The script never writes to the database directly and never contains credentials.
It requires an already bootstrapped admin account and a normal author account.

Example:
    export DEMO_ADMIN_PASSWORD='...'
    export DEMO_AUTHOR_PASSWORD='...'
    python3 deploy/seed-demo.py --reset-only
    python3 deploy/seed-demo.py --count 12

Only works whose titles start with "[演示]" are removed by --reset, so real
uploads and manually created demo works are left untouched.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
import uuid
from http.cookiejar import CookieJar
from pathlib import Path
from urllib import error, parse, request

MARKER = "[演示]"
CATEGORY_NAMES = ["生活", "知识", "游戏", "旅行"]


class ApiError(RuntimeError):
    def __init__(self, method: str, path: str, status: int, body: str):
        super().__init__(f"{method} {path} -> HTTP {status}: {body[:300]}")
        self.status = status
        self.body = body


class Client:
    def __init__(self, base_url: str, timeout: int = 180):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.opener = request.build_opener(request.HTTPCookieProcessor(CookieJar()))
        self.csrf_token: str | None = None

    def csrf(self) -> str:
        if self.csrf_token:
            return self.csrf_token
        data = self.call("GET", "/api/auth/csrf", csrf=False)
        self.csrf_token = data["token"]
        return self.csrf_token

    def call(self, method: str, path: str, payload=None, raw: bytes | None = None,
             content_type: str | None = None, csrf: bool = True, extra_headers: dict | None = None):
        headers = {"Accept": "application/json"}
        data = None
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif raw is not None:
            data = raw
            if content_type:
                headers["Content-Type"] = content_type
        if csrf and method not in ("GET", "HEAD"):
            headers["X-CSRF-TOKEN"] = self.csrf()
        if extra_headers:
            headers.update(extra_headers)
        req = request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=self.timeout) as response:
                body = response.read()
                return json.loads(body) if body else None
        except error.HTTPError as failure:
            body = failure.read().decode("utf-8", "replace")
            if failure.code == 403 and csrf and "CSRF" in body:
                self.csrf_token = None
                return self.call(method, path, payload, raw, content_type, True, extra_headers)
            raise ApiError(method, path, failure.code, body) from failure

    def login(self, username: str, password: str):
        self.csrf()
        body = parse.urlencode({"username": username, "password": password}).encode("utf-8")
        self.call("POST", "/api/auth/login", raw=body,
                  content_type="application/x-www-form-urlencoded")
        self.csrf_token = None
        self.csrf()

    def logout(self):
        try:
            self.call("POST", "/api/auth/logout")
        except ApiError:
            pass


def ensure_categories(admin: Client) -> list[dict]:
    existing = admin.call("GET", "/api/admin/categories?page=1&size=50")["items"]
    by_name = {item["name"]: item for item in existing}
    for index, name in enumerate(CATEGORY_NAMES):
        current = by_name.get(name)
        if current is None:
            by_name[name] = admin.call("POST", "/api/admin/categories", {"name": name, "sortOrder": index})
        elif not current["enabled"]:
            by_name[name] = admin.call("PUT", f"/api/admin/categories/{current['id']}", {
                "name": name, "sortOrder": index, "enabled": True, "version": current["version"],
            })
    return [by_name[name] for name in CATEGORY_NAMES]


def make_clip(ffmpeg: str, path: Path, hue: int):
    subprocess.run([
        ffmpeg, "-y", "-loglevel", "error",
        "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30:duration=3",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
        "-vf", f"hue=h={hue}",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "veryfast",
        "-c:a", "aac", "-shortest", str(path),
    ], check=True)


def wait_ready(author: Client, video_id: str, timeout_seconds: int = 180) -> dict:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        video = author.call("GET", f"/api/creator/videos/{video_id}")
        if video["processingStatus"] == "READY":
            return video
        if video["processingStatus"] == "FAILED":
            raise RuntimeError(f"video {video_id} failed processing: {video.get('failureCode')}")
        time.sleep(2)
    raise TimeoutError(f"video {video_id} did not finish processing in {timeout_seconds}s")


def seed_one(author: Client, category_id: str, title: str, clip: Path) -> str:
    draft = author.call("POST", "/api/creator/drafts", {
        "requestKey": str(uuid.uuid4()),
        "title": title,
        "description": "用于演示浏览、播放和审核流程的合成视频。",
        "categoryId": category_id,
    })
    author.call("PUT", f"/api/creator/videos/{draft['id']}/source",
                raw=clip.read_bytes(), content_type="video/mp4",
                extra_headers={"X-Video-Version": str(draft["version"])})
    current = wait_ready(author, draft["id"])
    author.call("POST", f"/api/creator/videos/{draft['id']}/review-submissions",
                {"version": current["version"]})
    return draft["id"]


def approve(admin: Client, video_id: str, reason: str):
    pending = admin.call("GET", f"/api/admin/reviews/{video_id}")
    admin.call("POST", f"/api/admin/reviews/{video_id}/decision", {
        "version": pending["version"],
        "reviewRound": pending["reviewRound"],
        "outcome": "APPROVED",
        "reason": reason,
    })


def reset_seed_works(author: Client):
    works = []
    page = 1
    while page <= 20:
        result = author.call("GET", f"/api/creator/videos?page={page}&size=50")
        works.extend(video for video in result["items"] if video["title"].startswith(MARKER))
        if page * 50 >= result["total"]:
            break
        page += 1
    removed = 0
    for video in works:
        try:
            author.call("DELETE", f"/api/creator/videos/{video['id']}?version={video['version']}")
            removed += 1
        except ApiError as failure:
            print(f"skip reset {video['id']}: {failure}", file=sys.stderr)
    print(f"reset: {removed} seeded work(s) removed")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.environ.get("DEMO_BASE_URL", "http://127.0.0.1:8081"))
    parser.add_argument("--admin-user", default=os.environ.get("DEMO_ADMIN_USERNAME", "studio_admin"))
    parser.add_argument("--author-user", default=os.environ.get("DEMO_AUTHOR_USERNAME", "demo_author"))
    parser.add_argument("--count", type=int, default=12)
    parser.add_argument("--leave-pending", type=int, default=1)
    parser.add_argument("--reset", action="store_true", help="remove existing [演示] works first")
    parser.add_argument("--reset-only", action="store_true")
    parser.add_argument("--ffmpeg", default=os.environ.get("FFMPEG_BIN", "ffmpeg"))
    args = parser.parse_args()

    admin_password = os.environ.get("DEMO_ADMIN_PASSWORD")
    author_password = os.environ.get("DEMO_AUTHOR_PASSWORD")
    if not admin_password or not author_password:
        parser.error("set DEMO_ADMIN_PASSWORD and DEMO_AUTHOR_PASSWORD in the environment")
    if args.count < 1 or args.leave_pending < 0 or args.leave_pending > args.count:
        parser.error("count must be positive and leave-pending must be between 0 and count")

    author = Client(args.base_url)
    admin = Client(args.base_url)
    author.login(args.author_user, author_password)
    if args.reset or args.reset_only:
        reset_seed_works(author)
    if args.reset_only:
        return 0

    admin.login(args.admin_user, admin_password)
    categories = ensure_categories(admin)
    created: list[str] = []
    with tempfile.TemporaryDirectory(prefix="creator-seed-") as directory:
        for index in range(args.count):
            category = categories[index % len(categories)]
            title = f"{MARKER} {category['name']} · 示例作品 {index + 1:02d}"
            clip = Path(directory) / f"clip-{index + 1:02d}.mp4"
            make_clip(args.ffmpeg, clip, (index * 31) % 360)
            print(f"upload {index + 1}/{args.count}: {title}")
            created.append(seed_one(author, category["id"], title, clip))

    approved = 0
    for video_id in created[:max(0, len(created) - args.leave_pending)]:
        try:
            approve(admin, video_id, "演示数据审核通过")
            approved += 1
        except ApiError as failure:
            print(f"approve {video_id} skipped: {failure}", file=sys.stderr)
    print(f"seed: {len(created)} works created, {approved} approved, "
          f"{len(created) - approved} left for review")
    print(f"open: {args.base_url}/")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ApiError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as failure:
        print(f"seed failed: {failure}", file=sys.stderr)
        raise SystemExit(1)
